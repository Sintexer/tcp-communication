package spolks.tcpserver.command

import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.RESOURCES_FOLDER
import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.command.impl.exception.IllegalCommandArgsException
import spolks.tcpserver.server.*
import java.io.File
import java.io.FileNotFoundException
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
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
        CommandName.SHUTDOWN.name -> {sendAck(buffer, address, port, socket); ServerAction.SHUTDOWN}
        CommandName.EXIT.name -> {sendAck(buffer, address, port, socket); ServerAction.EXIT}
        CommandName.TIME.name -> timeCommand(buffer, address, port, socket)
        CommandName.ECHO.name -> echoCommand(commandPayload, address, port, socket)
        else -> ServerAction.CONTINUE
    }
}

private fun timeCommand(buffer : ByteArray, address: InetAddress, port: Int, socket: DatagramSocket): ServerAction {
    sendPacket(LocalDateTime.now().toString(), address, port, socket)
    if (!receiveAck(address, port, socket)) throw UdpAckException("Ack wasn't received")
    return ServerAction.CONTINUE
}

private fun echoCommand(commandPayload: CommandPayload, address: InetAddress, port: Int, socket: DatagramSocket): ServerAction {
    val payload = commandPayload.commandWithArgs.substring(commandPayload.commandName.length)
    sendUdpReliably(payload, address, port, socket)
    return ServerAction.CONTINUE
}

private fun downloadCommand(commandPayload: CommandPayload, address: InetAddress, port: Int, socket: DatagramSocket): ServerAction {
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

    sendUdpReliably(filename, address, port, socket)
    val fileSize = file.length()

    val receiveBuffer = ByteArray(UDP_PACKET_SIZE)
    val segmentSize = receivePacket(receiveBuffer, address, port, socket).toInt()
    val segmentsAmount = ceil(fileSize.toDouble() / segmentSize).toInt()
    sendUdpReliably(segmentsAmount.toString(), address, port, socket)



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
