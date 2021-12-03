package spolks.tcpclient.command

import spolks.tcpclient.UDP_PACKET_SIZE
import spolks.tcpclient.session.receivePacket
import spolks.tcpclient.session.sendAck
import spolks.tcpclient.terminal.ClientInputReader
import java.net.DatagramSocket
import java.net.InetAddress
import java.sql.Date
import java.time.Instant
import java.time.LocalDateTime

val echoCommand =
    { receiveBuffer: ByteArray, sendBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        val message = receivePacket(receiveBuffer, address, port, socket)
        sendAck(address, port, socket)
        println("Server sent: $message")
    }

val timeCommand =
    { receiveBuffer: ByteArray, sendBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        val message = receivePacket(receiveBuffer, address, port, socket)
        sendAck(address, port, socket)
        println("Server time: $message")
    }

val emptyCommand =
    { _: ByteArray, _: ByteArray, _: InetAddress, _: Int, _: DatagramSocket -> }

val commands = mapOf(
    "echo" to echoCommand,
    "time" to timeCommand,
    "exit" to emptyCommand,
    "shutdown" to emptyCommand
)

fun getUdpCommand(clientIn: ClientInputReader): Pair<String, (ByteArray, ByteArray, InetAddress, Int, DatagramSocket) -> Unit> {
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

