package spolks.tcpserver.session

import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.command.parseCommandPayload
import spolks.tcpserver.command.processUdpCommand
import spolks.tcpserver.server.ServerAction
import spolks.tcpserver.server.receivePacket
import spolks.tcpserver.server.sendAck
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpSessionProcessing(
    private val socket: DatagramSocket
) : Closeable {


    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)
    private val sendingBuffer = ByteArray(UDP_PACKET_SIZE)

    override fun close() {

    }

    fun run(): Boolean {
        val packet = DatagramPacket(receivingBuffer, receivingBuffer.size)
        socket.receive(packet)
        println("#Client connected")
        return processWorkLoop(packet.address, packet.port)
    }

    private fun processWorkLoop(address: InetAddress, port: Int): Boolean {
        var running = true
        var previousAction = ServerAction.SHUTDOWN
        while (running) {
            val received = receivePacket(receivingBuffer, address, port, socket)
            println("#Client  sent: $received")
            sendAck(sendingBuffer, address, port, socket)
            previousAction = processCommand(received, address, port)
            running = previousAction == ServerAction.CONTINUE
        }
        return previousAction == ServerAction.SHUTDOWN
    }

    private fun processCommand(command: String, address: InetAddress, port: Int): ServerAction {
        val commandPayload = parseCommandPayload(command, 0)
        return processUdpCommand(commandPayload, receivingBuffer, address, port, socket)
    }
}