package spolks.tcpserver.command

import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.RESOURCES_FOLDER
import spolks.tcpserver.UDP_MAX_WINDOW
import spolks.tcpserver.UDP_NUMBER_SIZE
import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.command.impl.exception.IllegalCommandArgsException
import spolks.tcpserver.server.ServerAction
import spolks.tcpserver.server.receiveAck
import spolks.tcpserver.server.receiveFileAck
import spolks.tcpserver.server.receivePacket
import spolks.tcpserver.server.sendAck
import spolks.tcpserver.server.sendPacket
import spolks.tcpserver.server.sendUdpReliably
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.DatagramSocket
import java.net.InetAddress
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
    socket: DatagramSocket
): ServerAction {
    return when (commandPayload.commandName.uppercase()) {
        CommandName.SHUTDOWN.name -> {
            sendAck(buffer, address, port, socket); ServerAction.SHUTDOWN
        }
        CommandName.EXIT.name -> {
            sendAck(buffer, address, port, socket); ServerAction.EXIT
        }
        CommandName.TIME.name -> timeCommand(buffer, address, port, socket)
        CommandName.ECHO.name -> echoCommand(commandPayload, address, port, socket)
        CommandName.DOWNLOAD.name -> downloadCommand(commandPayload, address, port, socket)
        else -> ServerAction.CONTINUE
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
    socket: DatagramSocket
): ServerAction {
    println("#Download started")
    if (commandPayload.commandName == commandPayload.commandWithArgs.trim()) {
        sendUdpReliably("$ERROR No filename provided", address, port, socket)
        throw IllegalCommandArgsException("No filename provided")
    }

    fun send(packet: Any) {
        sendUdpReliably(packet.toString(), address, port, socket)
    }

    fun send(packet: ByteArray) {
        sendUdpReliably(packet, address, port, socket)
    }

    fun getAck(): Int {
        return receiveFileAck(address, port, socket)
    }

    val filename = commandPayload.commandWithArgs.substring(commandPayload.commandName.length + 1)

    val file = getFile(filename)
    if (!file.exists()) {
        sendUdpReliably("$ERROR file not found", address, port, socket)
        throw FileNotFoundException("File with name $filename wasn't found")
    }
    sendUdpReliably(OK, address, port, socket)

    sendUdpReliably(filename, address, port, socket)
    val fileSize = file.length()

    val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
    val segmentSize = receivePacket(receiveBuffer, address, port, socket).toInt()
    val bufferSize = segmentSize - UDP_NUMBER_SIZE
    val segmentsAmount = ceil(fileSize.toDouble() / segmentSize).toInt()
    sendUdpReliably(segmentsAmount.toString(), address, port, socket)
    sendUdpReliably(UDP_NUMBER_SIZE.toString(), address, port, socket)

    val startFrom = 0L // TODO
    val fileIn: InputStream = BufferedInputStream(FileInputStream(file!!))
    fileIn.skip(startFrom)
    send(startFrom)

    val packets: MutableMap<Int, ByteArray> = LinkedHashMap()
//
//    fileIn.use {
//        val startAt = Date().time
//        val buffer = ByteArray(bufferSize)
//        var windowSize = 1
//        socket.soTimeout = 500
//        for (i: Int in 0 until segmentsAmount) {
//            if (packets.size < windowSize) {
//                val bytesRead = fileIn.read(buffer, 0, segmentSize)
//                val packet = ByteArray(segmentSize)
//                for (j in (0..UDP_NUMBER_SIZE)) {
//                    packet[j] = j.toString().padStart(UDP_NUMBER_SIZE, '0')[j].code.toByte()
////                packet[j+ UDP_NUMBER_SIZE] = bytesRead.toString().padStart(UDP_NUMBER_SIZE, '0')[j].code.toByte()
//                }
//                buffer.copyInto(packet, UDP_NUMBER_SIZE)
//                packets[i] = packet
//                send(packet)
//            }
//            try {
//                repeat(packets.size) {
//                    packets.remove(getAck())
//                    windowSize = min(windowSize + 1, UDP_MAX_WINDOW)
//                }
//            } catch (e: Exception) {
//                windowSize = max(1, windowSize - 1)
//            }
//        }
//        receiveAck(address, port, socket)
//        val timeSpent = ((Date().time - startAt + 1) / 1000) + 1
//        println("#Download bitrate is: $timeSpent")
//    }
    return ServerAction.CONTINUE
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
