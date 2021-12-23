package spolks.tcpserver.session

import spolks.tcpserver.CONTINUE
import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.RESOURCES_FOLDER
import spolks.tcpserver.UDP_CLIENT_ID_SIZE
import spolks.tcpserver.UDP_DEFAULT_SO_TIMEOUT
import spolks.tcpserver.UDP_DOWNLOAD_SO_TIMEOUT
import spolks.tcpserver.UDP_MAX_WAIT_FAILS
import spolks.tcpserver.UDP_MAX_WINDOW
import spolks.tcpserver.UDP_MIN_WINDOW
import spolks.tcpserver.UDP_NUMBER_SIZE
import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.UDP_QUEUE_CAPACITY
import spolks.tcpserver.command.CommandName
import spolks.tcpserver.command.CommandPayload
import spolks.tcpserver.command.CommandStatus
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.command.impl.exception.IllegalCommandArgsException
import spolks.tcpserver.command.parseCommandName
import spolks.tcpserver.command.parseCommandPayload
import spolks.tcpserver.files.FileInfo
import spolks.tcpserver.files.FileInfoStorage
import spolks.tcpserver.server.ServerAction
import spolks.tcpserver.server.receiveFilePacket
import spolks.tcpserver.server.receivePacket
import spolks.tcpserver.server.sendPacket
import spolks.tcpserver.server.sendUdpReliably
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import spolks.tcpserver.server.UdpConnectionException
import spolks.tcpserver.server.receiveAck
import kotlin.math.ceil
import kotlin.math.roundToInt

class UdpThread(
    private val clientId: Int,
    private val address: InetAddress,
    private val port: Int,
    private val socket: DatagramSocket,
    private val unprocessedMessages: ConcurrentHashMap<Int, BlockingQueue<Pair<ByteArray, Int>>>
) : Thread() {

    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)

    override fun run() {
        println("Start thread")
        SessionsStorage.setIsAckTransfer(clientId)
        SessionsStorage.setAsInProgress(clientId)
        try {
            val packetAndLength =
                unprocessedMessages.computeIfAbsent(clientId) { ArrayBlockingQueue(UDP_QUEUE_CAPACITY) }.take()

            val message = String(packetAndLength.first, 0, packetAndLength.second)
            println("#Client  sent: $message")

            if(processPendingCommand(address, port, clientId)){
                processCommand(message, address, port, clientId)
            }


        } catch (e: CommandFlowException) {
            println(e.message)
        } catch (e: IOException) {
            println("#Client error occurred: $e")
        } finally {
            SessionsStorage.setAsDone(clientId)
            SessionsStorage.setIsAckTransfer(clientId)
            println("Thread is dead")
        }
    }

    private fun processCommand(command: String, address: InetAddress, port: Int, clientId: Int): ServerAction {
        val commandPayload = parseCommandPayload(command, 0, UDP_CLIENT_ID_SIZE)
        val commandName = parseCommandName(commandPayload.commandName)
        SessionsStorage.getInfo(clientId).apply { this.command = commandName; this.status = CommandStatus.IN_PROGRESS }
        val state = processUdpCommand(commandPayload, receivingBuffer, address, port, socket, clientId)
        SessionsStorage.getInfo(clientId).apply { this.status = CommandStatus.COMPLETED }
        return state
    }

    private fun processPendingCommand(address: InetAddress, port: Int, clientId: Int): Boolean {
        return if (SessionsStorage.getInfo(clientId).status != CommandStatus.COMPLETED) {
            val commandName = SessionsStorage.getInfo(clientId).command
            sendPacket(commandName.name, address, port, socket)
            SessionsStorage.getInfo(clientId)
                .apply { this.command = commandName; this.status = CommandStatus.COMPLETED }
            processPendingUdpCommand(commandName.name, address, port, socket, clientId)
            false
        } else {
            sendPacket(CONTINUE, address, port, socket)
            true
        }
    }

    private fun processUdpCommand(
        commandPayload: CommandPayload,
        buffer: ByteArray,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        clientId: Int
    ): ServerAction {
        return when (commandPayload.commandName.uppercase()) {
            CommandName.SHUTDOWN.name -> {
                ServerAction.SHUTDOWN
            }
            CommandName.EXIT.name -> {
                ServerAction.EXIT
            }
            CommandName.TIME.name -> timeCommand(buffer, address, port, socket)
            CommandName.ECHO.name -> echoCommand(commandPayload, address, port, socket)
            CommandName.DOWNLOAD.name -> downloadCommand(
                commandPayload,
                address,
                port,
                socket,
                clientId
            )
            CommandName.UPLOAD.name -> uploadCommand(
                commandPayload,
                address,
                port,
                socket,
                clientId
            )
            else -> ServerAction.CONTINUE
        }
    }

    private fun processPendingUdpCommand(
        commandName: String,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        clientId: Int
    ) {
        when (commandName.uppercase()) {
            CommandName.DOWNLOAD.name -> continueDownloadCommand(
                address,
                port,
                socket,
                clientId
            )
            CommandName.UPLOAD.name -> continueUploadCommand(address, port, socket, clientId)
        }
    }

    private fun timeCommand(buffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket): ServerAction {
        sendPacket(LocalDateTime.now().toString(), address, port, socket)
        return ServerAction.CONTINUE
    }

    private fun echoCommand(
        commandPayload: CommandPayload,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket
    ): ServerAction {
        val payload = commandPayload.commandWithArgs.substring(commandPayload.commandName.length)
        sendPacket(payload, address, port, socket)
        return ServerAction.CONTINUE
    }

    private fun Pair<ByteArray, Int>.getMessage() = String(this.first, 0, this.second)

    private fun downloadCommand(
        commandPayload: CommandPayload,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        clientId: Int
    ): ServerAction {
        println("#$clientId:Download started")
        if (commandPayload.commandName == commandPayload.commandWithArgs.trim()) {
            sendPacket("$ERROR No filename provided", address, port, socket)
            throw IllegalCommandArgsException("No filename provided")
        }
        val filename = commandPayload.commandWithArgs.substring(commandPayload.commandName.length + 1)

        val file = getFile(filename)
        if (!file.exists()) {
            sendPacket("$ERROR file not found", address, port, socket)
            throw FileNotFoundException("File with name $filename wasn't found")
        }
        sendPacket(OK, address, port, socket)

        try {
            processUdpFileDownload(filename, address, port, socket, file, 0, clientId)
            println("#$clientId:Download successfully finished")
        } catch (e: SocketTimeoutException) {
            println("#$clientId:Client have disconnected")
            throw CommandFlowException(e.message ?: "Client have disconnected")
        }
        return ServerAction.CONTINUE
    }

    private fun continueDownloadCommand(
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        clientId: Int
    ) {
        println("#Continue Download")
        val fileInfo = FileInfoStorage.getDownloadInfo(clientId)
        if (fileInfo == null) {
            sendPacket("$ERROR Can't find file download information", address, port, socket)
            return
        }

        val filename = fileInfo.fileName

        val file = getFile(filename)
        if (!file.exists()) {
            sendPacket("$ERROR file not found", address, port, socket)
            println("File with name $filename wasn't found")
            return
        }

        sendPacket(OK, address, port, socket)

        try {
            processUdpFileDownload(filename, address, port, socket, file, fileInfo.bytesTransferred.toLong(), clientId)
            println("#Download successfully finished")
        } catch (e: SocketTimeoutException) {
            println("#Client have disconnected")
        }
    }

    private fun uploadCommand(
        commandPayload: CommandPayload,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        clientId: Int
    ): ServerAction {
        println("#Upload started")
        try {
            processUdpUpload(clientId, address, port, socket)
        } catch (e: SocketTimeoutException) {
            println("#Client have disconnected")
            throw CommandFlowException(e.message ?: "Client have disconnected")
        }
        println("#Upload completed")
        return ServerAction.CONTINUE
    }

    private fun continueUploadCommand(
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        clientId: Int
    ): ServerAction {
        println("#Continue upload")
        val fileInfo = FileInfoStorage.getUploadInfo(clientId)
        if (fileInfo == null) {
            println("Server can't find file info")
            sendUdpReliably("$ERROR Server can't find file info", address, port, socket)
        }
        sendUdpReliably(OK, address, port, socket)
        sendUdpReliably(fileInfo!!.fileName, address, port, socket)
        try {
            processUdpUpload(clientId, address, port, socket)
            println("#Upload successfully completed")
        } catch (e: SocketTimeoutException) {
            println("#Client have disconnected")
        }
        return ServerAction.CONTINUE
    }

    private fun processUdpUpload(
        clientId: Int,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket
    ) {
        val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
        fun send(packet: Any) = sendUdpReliably(packet.toString(), address, port, socket)
        fun sendAck() = spolks.tcpserver.server.sendAck(address, port, socket)
        fun sendFileAck(id: Int) = spolks.tcpserver.server.sendFileAck(id, address, port, socket)
        fun receiveString() = receivePacket(receiveBuffer, address, port, socket).also { sendAck() }

        val clientStatus = receiveString()
        if (clientStatus != OK) {
            println(clientStatus)
            return
        }
        val filename = receiveString()
        var startFrom = FileInfoStorage.getUploadInfo(clientId)?.bytesTransferred?.toLong()
        val file = if (startFrom != null && startFrom != 0L) {
            val existingFile = getFile(filename)
            if (!existingFile.exists()) {
                sendUdpReliably("$ERROR file not found", address, port, socket)
                throw CommandFlowException("$ERROR file not found, can't continue download")
            } else existingFile
        } else {
            createFile(filename)
        }
        startFrom = if (startFrom == null) 0L else file.length()

        send(startFrom)
        val fileSize = receiveString().toLong()
        val segmentSize = receiveString().toInt()
        val bufferSize = segmentSize - UDP_NUMBER_SIZE
        val segmentsAmount = ceil((fileSize.toDouble() - startFrom) / bufferSize).toInt()

        send(segmentsAmount)
        send(UDP_NUMBER_SIZE)

        println("filename: $filename, number of segments: $segmentsAmount, number size:  $UDP_NUMBER_SIZE, start from byte: $startFrom, segmentSize: $segmentSize")

        val buffer = ByteArray(segmentSize)

        fun receiveFileChunk() =
            receiveFilePacket(buffer, address, port, socket).also { sendFileAck(getSegmentId(buffer, UDP_NUMBER_SIZE)) }

        val shelvedChunks = TreeMap<Int, ByteArray>()
        var currentChunk = 1
        val fileOut: OutputStream = BufferedOutputStream(FileOutputStream(file, startFrom != 0L))
        val startAt = Date().time
        fileOut.use {
            FileInfoStorage.putUploadInfo(clientId, FileInfo(filename, startFrom.toInt()))
            while (currentChunk <= segmentsAmount || shelvedChunks.isNotEmpty()) {
                val packetSize = receiveFileChunk()
                val chunkId = getSegmentId(buffer, UDP_NUMBER_SIZE)
                sendFileAck(chunkId)
                if (chunkId >= currentChunk && !shelvedChunks.containsKey(chunkId)) {
                    shelvedChunks[chunkId] = buffer.copyOf()
                }
                while (shelvedChunks.containsKey(currentChunk)) {
                    val bytesWritten = packetSize - UDP_NUMBER_SIZE
                    fileOut.write(shelvedChunks[currentChunk]!!, UDP_NUMBER_SIZE, bytesWritten)
                    sendFileAck(currentChunk)
                    FileInfoStorage.putUploadInfo(
                        clientId,
                        FileInfo(filename, FileInfoStorage.getUploadInfo(clientId)!!.bytesTransferred + bytesWritten)
                    )
                    shelvedChunks.remove(currentChunk)
                    ++currentChunk
                }
            }
        }
        val bitRate = (FileInfoStorage.getUploadInfo(clientId)!!.bytesTransferred - startFrom) /
            (((Date().time - startAt + 1) / 1000) + 1).toDouble()
        FileInfoStorage.removeUploadDetails(clientId)
        println("#Upload bitrate is: ${bitRate.roundToInt()}")
        sendFileAck(0)
        socket.soTimeout = 10
        socket.disconnect()
        socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
    }

    private fun processUdpFileDownload(
        filename: String,
        address: InetAddress,
        port: Int,
        socket: DatagramSocket,
        file: File,
        startFrom: Long,
        clientId: Int
    ) {
        val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
        fun pair() = unprocessedMessages[clientId]!!.take()
        fun readMessage() = pair().getMessage()
        fun readPayload() = pair().getMessage().substring(UDP_CLIENT_ID_SIZE)
        fun send(packet: Any) = sendPacket(packet.toString(), address, port, socket)
        fun send(packet: ByteArray, packetSize: Int = packet.size) =
            sendPacket(packet, address, port, socket, packetSize)

        fun sendAck() = spolks.tcpserver.server.sendAck(address, port, socket)

        fun getAck() =
            unprocessedMessages[clientId]!!.poll(3000, TimeUnit.NANOSECONDS)?.getMessage()?.substring(UDP_CLIENT_ID_SIZE)
                ?.toInt()

        fun getInt() = readPayload().toInt()

        send(filename)
        val fileSize = file.length()
        val fileIn: InputStream = BufferedInputStream(FileInputStream(file))
        send(startFrom)
        val clientIsOk = readPayload()
        if (clientIsOk != OK) {
            println(clientIsOk)
            throw CommandFlowException("Client can't continue download")
        }
        val actuallyStartFrom = getInt().toLong()

        val segmentSize = getInt()
        val bufferSize = segmentSize - UDP_NUMBER_SIZE
        val segmentsAmount = ceil((fileSize.toDouble() - actuallyStartFrom) / bufferSize).toInt()
        send(segmentsAmount.toString())
        send(UDP_NUMBER_SIZE.toString())
        fileIn.skip(actuallyStartFrom)
        val packets: TreeMap<Int, Triple<ByteArray, Int, Int>> = TreeMap()
        println("#$clientId:filename: $filename, startFrom: $startFrom, segmentSize: $segmentSize, segmentsAmount: $segmentsAmount")

        //
        fileIn.use {
            val startAt = Date().time
            val buffer = ByteArray(bufferSize)
            var windowSize = UDP_MIN_WINDOW
            var prevWindowSize = windowSize
            socket.soTimeout = UDP_DOWNLOAD_SO_TIMEOUT
            var i = 1
            var failsAmount = 0
            var finishedByClient = false
            val acks = HashSet<Int>()
            FileInfoStorage.putDownloadInfo(clientId, FileInfo(filename, actuallyStartFrom.toInt()))
            TimeUnit.NANOSECONDS.sleep(100)
            SessionsStorage.setIsUnAckTransfer(clientId)
            while (!finishedByClient || (acks.size < segmentsAmount || i <= segmentsAmount)) {
                if(failsAmount > UDP_MAX_WAIT_FAILS) break;
                while (acks.size < segmentsAmount && packets.size < windowSize * 4) {
                    val bytesRead = fileIn.read(buffer, 0, bufferSize)
                    val packet = ByteArray(segmentSize)
                    for (j in (0 until UDP_NUMBER_SIZE)) {
                        packet[j] = i.toString().padStart(UDP_NUMBER_SIZE, '0')[j].code.toByte()
                    }
                    buffer.copyInto(packet, UDP_NUMBER_SIZE)
                    packets[i] = Triple(packet.copyOf(), bytesRead + UDP_NUMBER_SIZE, LocalDateTime.now().nano)
                    ++i
                }
                packets.filterKeys { key -> acks.contains(key) }.forEach { packets.remove(it.key) }
                if (packets.size > 0) {
                    packets.filter {
                        it.value.third - LocalDateTime.now().nano > 20
                    }.entries.sortedBy { it.key }.take(windowSize)
                        .forEach { entry -> send(entry.value.first, entry.value.second) }
                }
                TimeUnit.NANOSECONDS.sleep(10)
                try {
                    val acksToProcess = ArrayList<Pair<ByteArray, Int>>()
                    unprocessedMessages[clientId]!!.drainTo(acksToProcess)
                    val before = acks.size
                    acksToProcess.forEach {
                        failsAmount = 0
                        val segmentAck = it.getMessage().substring(UDP_CLIENT_ID_SIZE).toInt()
                        finishedByClient = (segmentAck == 0)
                        if (!acks.contains(segmentAck)) {
                            windowSize = Integer.min(windowSize + 1, UDP_MAX_WINDOW)
                            if (prevWindowSize != windowSize) {
                                println("#$clientId:Window size increased: $windowSize")
                                prevWindowSize = windowSize
                            }
//                            failsAmount = Integer.min(0, failsAmount - 1)
                        }
                        acks.add(segmentAck)
                        if (packets.containsKey(segmentAck)) {
                            val totalBytesTransferred =
                                (segmentAck - 1) * bufferSize + packets[segmentAck]!!.second
                            if (totalBytesTransferred > FileInfoStorage.getDownloadInfo(clientId)!!.bytesTransferred) {
                                FileInfoStorage.putDownloadInfo(
                                    clientId,
                                    FileInfo(
                                        filename,
                                        totalBytesTransferred
                                    )
                                )
                            }
                        }
                    }
                    if (before == acks.size) {
                        ++failsAmount
                        windowSize = Integer.max(UDP_MIN_WINDOW, windowSize - 1)
                        if (prevWindowSize != windowSize) {
                            println("#$clientId:Window size decreased: $windowSize")
                            prevWindowSize = windowSize
                        }
                    }
                } catch (e: Exception) {
//                    ++failsAmount
//                    windowSize = Integer.max(UDP_MIN_WINDOW, windowSize - 1)
//                    if (prevWindowSize != windowSize) {
////                        println("#Window size decreased: $windowSize")
//                        prevWindowSize = windowSize
//                    }
                    throw CommandFlowException("#$clientId:Client disconnected during download")
                }
            }
            if (failsAmount >= UDP_MAX_WAIT_FAILS) {
                throw CommandFlowException("#$clientId:Client disconnected during download")
            }
            val bitRate = (FileInfoStorage.getDownloadInfo(clientId)!!.bytesTransferred - startFrom) /
                (((Date().time - startAt + 1) / 1000) + 1).toDouble()
            println("#$clientId:Download bitrate is: ${bitRate.roundToInt()}")
        }
        TimeUnit.NANOSECONDS.sleep(100)
        unprocessedMessages[clientId]!!.clear()
        SessionsStorage.setIsAckTransfer(clientId)
//        socket.disconnect()
        socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
    }

    private fun getSegmentId(packet: ByteArray, numberSize: Int): Int {
        return String(packet, 0, packet.size).substring(0, numberSize).toInt()
    }

    fun getFile(filename: String): File {
        if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
            Files.createDirectory(Path.of(RESOURCES_FOLDER))
        }
        return File("$RESOURCES_FOLDER/$filename")
    }

    fun createFile(filename: String): File {
        getResourcesFolder()
        val file = File("$RESOURCES_FOLDER/$filename")
        if (!file.exists() || file.delete()) {
            file.createNewFile()
        }
        return file
    }

    fun getResourcesFolder(): File {
        if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
            Files.createDirectory(Path.of(RESOURCES_FOLDER))
        }
        return File(RESOURCES_FOLDER)
    }
}
