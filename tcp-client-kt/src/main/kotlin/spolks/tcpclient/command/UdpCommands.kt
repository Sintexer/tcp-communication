package spolks.tcpclient.command

import spolks.tcpclient.DEFAULT_SEGMENT_SIZE
import spolks.tcpclient.ERROR
import spolks.tcpclient.OK
import spolks.tcpclient.RESOURCES_FOLDER
import spolks.tcpclient.UDP_DEFAULT_SO_TIMEOUT
import spolks.tcpclient.UDP_MAX_WAIT_FAILS
import spolks.tcpclient.UDP_MAX_WINDOW
import spolks.tcpclient.UDP_MIN_WINDOW
import spolks.tcpclient.UDP_PACKET_SIZE
import spolks.tcpclient.UDP_UPLOAD_SO_TIMEOUT
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.session.receiveAck
import spolks.tcpclient.session.receiveFileAck
import spolks.tcpclient.session.receiveFilePacket
import spolks.tcpclient.session.receivePacket
import spolks.tcpclient.session.sendAck
import spolks.tcpclient.session.sendFileAck
import spolks.tcpclient.session.sendPacket
import spolks.tcpclient.session.sendUdpReliably
import spolks.tcpclient.terminal.ClientInputReader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

val echoCommand =
    { _: String, receiveBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        val message = receivePacket(receiveBuffer, address, port, socket)
        sendAck(address, port, socket)
        println("Server sent: $message")
    }

val timeCommand =
    { _: String, receiveBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        val message = receivePacket(receiveBuffer, address, port, socket)
        sendAck(address, port, socket)
        println("Server time: $message")
    }

val emptyCommand =
    { _: String, _: ByteArray, _: InetAddress, _: Int, _: DatagramSocket -> }

val downloadCommand =
    { _: String, _: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        println("#Download started")
        processUdpDownload(address, port, socket)
        println("#Download completed")
    }

val uploadCommand =
    { commandName: String, _: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        println("#Upload started")
        if (!commandName.contains(" ")) {
            sendUdpReliably("$ERROR No filename provided", address, port, socket)
            throw IllegalArgumentException("No filename provided")
        }

        val filename = commandName.substring(commandName.split(" ")[0].length + 1)
        processUploadCommand(filename, address, port, socket)

        println("#Upload completed")
    }

private fun getSegmentId(packet: ByteArray, numberSize: Int): Int {
    return String(packet, 0, packet.size).substring(0, numberSize).toInt()
}

private fun createFile(filename: String): File {
    resourcesFolderExists()
    val file = File("$RESOURCES_FOLDER/$filename")
    if (file.delete()) {
        file.createNewFile()
    }
    return file
}

private fun getFile(filename: String): File {
    resourcesFolderExists()
    return File("$RESOURCES_FOLDER/$filename")
}

private fun resourcesFolderExists() {
    if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
        Files.createDirectory(Path.of(RESOURCES_FOLDER))
    }
}

val commands = mapOf(
    "echo" to echoCommand,
    "time" to timeCommand,
    "exit" to emptyCommand,
    "shutdown" to emptyCommand,
    "download" to downloadCommand,
    "connect" to emptyCommand,
    "upload" to uploadCommand
)

fun getUdpCommand(clientIn: ClientInputReader): Pair<String, (String, ByteArray, InetAddress, Int, DatagramSocket) -> Unit> {
    var validCommand = false
    var input = ""
    var command = ""
    while (!validCommand) {
        input = clientIn.readString()
        command = input.split(" ")[0]
        validCommand = commands.containsKey(command.lowercase())
        if (!validCommand) {
            println(
                "Invalid command. Valid commands are: \n" +
                    commands.keys.joinToString("\n")
            )
        }
    }
    return Pair(input, commands[command]!!)
}

fun processPendingCommand(commandName: String, address: InetAddress, port: Int, socket: DatagramSocket) {
    when (commandName.lowercase()) {
        "download" -> continueDownloadCommand(address, port, socket)
        "upload" -> continueUploadCommand(address, port, socket)
    }
}

fun continueDownloadCommand(address: InetAddress, port: Int, socket: DatagramSocket) {
    println("#Continue Download")
    processUdpDownload(address, port, socket)
    println("#Download successfully completed")
}

fun continueUploadCommand(address: InetAddress, port: Int, socket: DatagramSocket) {
    println("#Continue Upload")
    val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
    fun receiveString() = receivePacket(receiveBuffer, address, port, socket).also { sendAck(address, port, socket) }
    val serverStatus = receiveString()
    if (serverStatus != OK) {
        println(serverStatus)
        return
    }
    val filename = receiveString()
    processUploadCommand(filename, address, port, socket)
    println("#Upload successfully completed")
}

private fun processUdpDownload(
    address: InetAddress,
    port: Int,
    socket: DatagramSocket
) {
    val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
    fun send(packet: Any) = sendUdpReliably(packet.toString(), address, port, socket)
    fun sendAck() = sendAck(address, port, socket)
    fun sendFileAck(id: Int) = sendFileAck(id, address, port, socket)
    fun receiveString() = receivePacket(receiveBuffer, address, port, socket).also { sendAck() }

//  socket.soTimeout = UDP_DOWNLOAD_SO_TIMEOUT
    val serverIsFine = receiveAck(address, port, socket).also { sendAck(address, port, socket) }

    if (!serverIsFine) {
        throw CommandFlowException("Server error")
    }

    val filename = receiveString()
    var startFrom = receiveString().toLong()

    val shelvedChunks = TreeMap<Int, ByteArray>()
    var currentChunk = 1
    val file = if (startFrom != 0L) {
        val existingFile = getFile(filename)
        if (!existingFile.exists()) {
            sendUdpReliably("$ERROR file not found", address, port, socket)
            throw CommandFlowException("$ERROR file not found, can't continue download")
        } else existingFile
    } else {
        createFile(filename)
    }
    startFrom = file.length()
    sendUdpReliably(OK, address, port, socket)
    sendUdpReliably(startFrom.toString(), address, port, socket)
    val segmentSize = DEFAULT_SEGMENT_SIZE
    send(segmentSize)
    val segmentsAmount = receiveString().toInt()
    val numberSize = receiveString().toInt()

    println("filename: $filename, number of segments: $segmentsAmount, number size:  $numberSize, start from byte: $startFrom")

    val buffer = ByteArray(segmentSize)

    fun receiveFileChunk() =
        receiveFilePacket(buffer, address, port, socket).also { sendFileAck(getSegmentId(buffer, numberSize)) }

    val fileOut: OutputStream = BufferedOutputStream(FileOutputStream(file, startFrom != 0L))
    fileOut.use {
        while (currentChunk <= segmentsAmount || shelvedChunks.isNotEmpty()) {
            val packetSize = receiveFileChunk()
            val chunkId = getSegmentId(buffer, numberSize)
            sendFileAck(chunkId)
            if (chunkId >= currentChunk && !shelvedChunks.containsKey(chunkId)) {
                shelvedChunks[chunkId] = buffer.copyOf()
            }
            while (shelvedChunks.containsKey(currentChunk)) {
                fileOut.write(shelvedChunks[currentChunk]!!, numberSize, packetSize - numberSize)
                sendFileAck(currentChunk)
                shelvedChunks.remove(currentChunk)
                ++currentChunk
            }
        }
    }
    sendFileAck(0)
    socket.disconnect()
    socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
}

private fun processUploadCommand(
    filename: String,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket
) {
    val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
    fun send(packet: Any) = sendUdpReliably(packet.toString(), address, port, socket)
    fun send(packet: ByteArray, packetSize: Int = packet.size) = sendPacket(packet, address, port, socket, packetSize)
    fun sendAck() = sendAck(address, port, socket)
    fun sendFileAck(id: Int) = sendFileAck(id, address, port, socket)
    fun receiveString() = receivePacket(receiveBuffer, address, port, socket).also { sendAck() }
    fun getAck() = receiveFileAck(address, port, socket)

    val file = getFile(filename)
    if (!file.exists()) {
        sendUdpReliably("$ERROR file not found", address, port, socket)
        throw FileNotFoundException("File with name $filename wasn't found")
    }
    send(OK)
    val fileSize = file.length()
    val segmentSize = DEFAULT_SEGMENT_SIZE
    send(filename)
    val startFrom = receiveString().toLong()
    send(fileSize)
    send(segmentSize)
    val segmentsAmount = receiveString().toInt()
    val numberSize = receiveString().toInt()
    val bufferSize = segmentSize - numberSize
    println("filename: $filename, number of segments: $segmentsAmount, number size:  $numberSize, start from byte: $startFrom")

    val fileIn: InputStream = BufferedInputStream(FileInputStream(file))
    fileIn.skip(startFrom)
    val packets: TreeMap<Int, Pair<ByteArray, Int>> = TreeMap()
    fileIn.use {
        val buffer = ByteArray(bufferSize)
        var windowSize = UDP_MIN_WINDOW
        var prevWindowSize = windowSize
        socket.soTimeout = UDP_UPLOAD_SO_TIMEOUT
        var i = 1
        var failsAmount = 0
        var finishedByServer = false
        val acks = HashSet<Int>()
        while (!finishedByServer && failsAmount < UDP_MAX_WAIT_FAILS && (acks.size < segmentsAmount || i <= segmentsAmount)) {
            if (acks.size < segmentsAmount && packets.size < windowSize) {
                val bytesRead = fileIn.read(buffer, 0, bufferSize)
                val packet = ByteArray(segmentSize)
                for (j in (0 until numberSize)) {
                    packet[j] = i.toString().padStart(numberSize, '0')[j].code.toByte()
                }
                buffer.copyInto(packet, numberSize)
                packets[i] = Pair(packet.copyOf(), bytesRead + numberSize)
                ++i
            }
            packets.filterKeys { key -> acks.contains(key) }.forEach { packets.remove(it.key) }
            if (packets.size > 0) {
                packets.entries.take(windowSize).forEach { entry -> send(entry.value.first, entry.value.second) }
            }

            try {
                repeat(windowSize) {
                    val segmentAck = getAck()
                    if (!acks.contains(segmentAck)) {
                        windowSize = Integer.min(windowSize + 1, UDP_MAX_WINDOW)
                        if (prevWindowSize != windowSize) {
                            println("#Window size increased: $windowSize")
                            prevWindowSize = windowSize
                        }
                        failsAmount = Integer.min(0, failsAmount - 1)
                    }
                    acks.add(segmentAck).also { finishedByServer = (segmentAck == 0) }
                }
            } catch (e: SocketTimeoutException) {
                ++failsAmount
                windowSize = Integer.max(UDP_MIN_WINDOW, windowSize - 1)
                if (prevWindowSize != windowSize) {
                    println("#Window size decreased: $windowSize")
                    prevWindowSize = windowSize
                }
            }
        }
        if (failsAmount >= UDP_MAX_WAIT_FAILS) {
            throw CommandFlowException("Server disconnected during download")
        }
    }
    socket.disconnect()
    socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
}
