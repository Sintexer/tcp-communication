package spolks.tcpclient.command

import spolks.tcpclient.session.receiveAck
import spolks.tcpclient.session.receivePacket
import spolks.tcpclient.session.sendAck
import spolks.tcpclient.terminal.ClientInputReader
import java.net.DatagramSocket
import java.net.InetAddress
import spolks.tcpclient.session.receiveFileAck
import spolks.tcpclient.session.sendFileAck
import spolks.tcpclient.session.sendUdpReliably

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

val downloadCommand =
    { receiveBuffer: ByteArray, sendBuffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket ->
        println("#Download started")
        fun send(packet: Any) {
            sendUdpReliably(packet.toString(), address, port, socket)
        }
        fun send(packet: ByteArray) {
            sendUdpReliably(packet, address, port, socket)
        }
        fun sendAck() {
            sendAck(address, port, socket)
        }
        fun sendAck(id: Int): Int {
            return sendFileAck(id, address, port, socket)
        }
        fun receiveString(): String {
            val str = receivePacket(receiveBuffer, address, port, socket)
            return str.also { sendAck() }
        }

        socket.soTimeout = 5_000
        val serverIsFine = receiveAck(address, port, socket)
        sendAck(address, port, socket)
        if (!serverIsFine) {
            throw Exception("Server error")
        }

        val fileName = receiveString()
        val segmentsAmount = receiveString().toInt()
        val numberSize = receiveString().toInt()
        val startFrom = receiveString().toLong()

        println("$fileName, $segmentsAmount, $numberSize, $startFrom")
    }

val commands = mapOf(
    "echo" to echoCommand,
    "time" to timeCommand,
    "exit" to emptyCommand,
    "shutdown" to emptyCommand,
    "download" to downloadCommand
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
