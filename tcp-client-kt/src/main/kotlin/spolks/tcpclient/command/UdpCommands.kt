package spolks.tcpclient.command

import spolks.tcpclient.*
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.session.*
import spolks.tcpclient.terminal.ClientInputReader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

val echoCommand =
    { receiveBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        val message = receivePacket(receiveBuffer, address, port, socket)
        sendAck(address, port, socket)
        println("Server sent: $message")
    }

val timeCommand =
    { receiveBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        val message = receivePacket(receiveBuffer, address, port, socket)
        sendAck(address, port, socket)
        println("Server time: $message")
    }

val emptyCommand =
    { _: ByteArray, _: InetAddress, _: Int, _: DatagramSocket -> }

val downloadCommand =
    { _: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        println("#Download started")
        processUdpDownload(address, port, socket)
        println("#Download completed")
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
    "connect" to emptyCommand
)

fun getUdpCommand(clientIn: ClientInputReader): Pair<String, (ByteArray, InetAddress, Int, DatagramSocket) -> Unit> {
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
    }
}

fun continueDownloadCommand(address: InetAddress, port: Int, socket: DatagramSocket) {
    println("#Continue Download")
    processUdpDownload(address, port, socket)
    println("#Download successfully completed")
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
        throw Exception("Server error")
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