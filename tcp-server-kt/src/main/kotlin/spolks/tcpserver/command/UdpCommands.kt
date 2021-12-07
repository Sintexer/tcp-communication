package spolks.tcpserver.command

import spolks.tcpserver.*
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.command.impl.exception.IllegalCommandArgsException
import spolks.tcpserver.files.FileInfo
import spolks.tcpserver.files.FileInfoStorage
import spolks.tcpserver.server.*
import java.io.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import kotlin.math.ceil

fun processUdpCommand(
    commandPayload: CommandPayload,
    buffer: ByteArray,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket,
    clientId: Int
): ServerAction {
    return when (commandPayload.commandName.uppercase()) {
        CommandName.SHUTDOWN.name -> {
            sendAck(address, port, socket); ServerAction.SHUTDOWN
        }
        CommandName.EXIT.name -> {
            sendAck(address, port, socket); ServerAction.EXIT
        }
        CommandName.TIME.name -> timeCommand(buffer, address, port, socket)
        CommandName.ECHO.name -> echoCommand(commandPayload, address, port, socket)
        CommandName.DOWNLOAD.name -> downloadCommand(commandPayload, address, port, socket, clientId)
        else -> ServerAction.CONTINUE
    }
}

fun processPendingUdpCommand(
    commandName: String,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket,
    clientId: Int
) {
    when (commandName.uppercase()) {
        CommandName.DOWNLOAD.name -> continueDownloadCommand(address, port, socket, clientId)
    }
}

private fun timeCommand(buffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket): ServerAction {
    sendPacket(LocalDateTime.now().toString(), address, port, socket)
    if (!receiveAck(address, port, socket)) throw UdpAckException("Ack wasn't received")
    return ServerAction.CONTINUE
}

private fun echoCommand(
    commandPayload: CommandPayload,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket
): ServerAction {
    val payload = commandPayload.commandWithArgs.substring(commandPayload.commandName.length)
    sendUdpReliably(payload, address, port, socket)
    return ServerAction.CONTINUE
}

private fun downloadCommand(
    commandPayload: CommandPayload,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket,
    clientId: Int
): ServerAction {
    println("#Download started")
    if (commandPayload.commandName == commandPayload.commandWithArgs.trim()) {
        sendUdpReliably("$ERROR No filename provided", address, port, socket)
        throw IllegalCommandArgsException("No filename provided")
    }
    val filename = commandPayload.commandWithArgs.substring(commandPayload.commandName.length + 1)

    val file = getFile(filename)
    if (!file.exists()) {
        sendUdpReliably("$ERROR file not found", address, port, socket)
        throw FileNotFoundException("File with name $filename wasn't found")
    }
    sendUdpReliably(OK, address, port, socket)

    processUdpFileDownload(filename, address, port, socket, file, 0, clientId)
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
        sendUdpReliably("$ERROR Can't find file download information", address, port, socket)
        return
    }

    val filename = fileInfo.fileName

    val file = getFile(filename)
    if (!file.exists()) {
        sendUdpReliably("$ERROR file not found", address, port, socket)
        println("File with name $filename wasn't found")
        return
    }

    sendUdpReliably(OK, address, port, socket)

    processUdpFileDownload(filename, address, port, socket, file, fileInfo.bytesTransferred.toLong(), clientId)
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
    fun send(packet: Any) = sendUdpReliably(packet.toString(), address, port, socket)
    fun send(packet: ByteArray, packetSize: Int = packet.size) = sendPacket(packet, address, port, socket, packetSize)
    fun sendAck() = sendAck(address, port, socket)
    fun getAck() = receiveFileAck(address, port, socket)
    fun getInt() = receivePacket(receiveBuffer, address, port, socket).toInt().also { sendAck() }

    sendUdpReliably(filename, address, port, socket)
    val fileSize = file.length()
    val fileIn: InputStream = BufferedInputStream(FileInputStream(file))
    send(startFrom)
    val clientIsOk = receivePacket(receiveBuffer, address, port, socket).also { sendAck() }
    if (clientIsOk != OK) {
        println(clientIsOk)
        throw CommandFlowException("Client can't continue download")
    }
    val actuallyStartFrom = receivePacket(receiveBuffer, address, port, socket).also { sendAck() }.toLong()

    val segmentSize = getInt()
    val bufferSize = segmentSize - UDP_NUMBER_SIZE
    val segmentsAmount = ceil((fileSize.toDouble() - actuallyStartFrom) / bufferSize).toInt()
    sendUdpReliably(segmentsAmount.toString(), address, port, socket)
    sendUdpReliably(UDP_NUMBER_SIZE.toString(), address, port, socket)
    fileIn.skip(actuallyStartFrom)
    val packets: TreeMap<Int, Pair<ByteArray, Int>> = TreeMap()

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
        while (!finishedByClient && failsAmount < UDP_MAX_WAIT_FAILS && (acks.size < segmentsAmount || i <= segmentsAmount)) {
            if (acks.size < segmentsAmount && packets.size < windowSize) {
                val bytesRead = fileIn.read(buffer, 0, bufferSize)
                val packet = ByteArray(segmentSize)
                for (j in (0 until UDP_NUMBER_SIZE)) {
                    packet[j] = i.toString().padStart(UDP_NUMBER_SIZE, '0')[j].code.toByte()
                }
                buffer.copyInto(packet, UDP_NUMBER_SIZE)
                packets[i] = Pair(packet.copyOf(), bytesRead + UDP_NUMBER_SIZE)
                ++i
            }
            packets.filterKeys { key -> acks.contains(key) }.forEach { packets.remove(it.key) }
            if (packets.size > 0) {
                packets.entries.take(windowSize).forEach { entry -> send(entry.value.first, entry.value.second) }
            }

            try {
                repeat(windowSize) {
                    val segmentAck = getAck()
                    if(!acks.contains(segmentAck)){
                        windowSize = min(windowSize + 1, UDP_MAX_WINDOW)
//                        if(prevWindowSize != windowSize ) {
//                            println("#Window size decreased: $windowSize")
//                            prevWindowSize = windowSize
//                        }
                        failsAmount = min(0, failsAmount - 1)
                    }
                    acks.add(segmentAck).also { finishedByClient = (segmentAck == 0) }
                    if (packets.containsKey(segmentAck)) {
                        val totalBytesTransferred = (segmentAck - 1) * bufferSize + packets[segmentAck]!!.second
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
            } catch (e: SocketTimeoutException) {
                ++failsAmount
                windowSize = max(UDP_MIN_WINDOW, windowSize - 1)
//                if(prevWindowSize != windowSize ) {
//                    println("#Window size decreased: $windowSize")
//                    prevWindowSize = windowSize
//                }
            }
        }
        if (failsAmount >= UDP_MAX_WAIT_FAILS) {
            throw CommandFlowException("Client disconnected during download")
        }
        val timeSpent = ((Date().time - startAt + 1) / 1000) + 1
        println("#Download bitrate is: $timeSpent")
    }
    socket.disconnect()
    socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
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


