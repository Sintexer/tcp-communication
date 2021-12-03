package spolks.tcpclient.session

import spolks.tcpclient.UDP_PACKET_SIZE
import spolks.tcpclient.command.getUdpCommand
import spolks.tcpclient.terminal.ClientInputReader
import java.net.DatagramSocket
import java.net.InetAddress

class UdpSessionProcessing(
    private val ip: String,
    private val port: Int,
    private val socket: DatagramSocket,
    private val clientIn: ClientInputReader
) {

    val clientSocket = DatagramSocket()

    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)
    private val sendingBuffer = ByteArray(UDP_PACKET_SIZE)

    fun run() {
        clientSocket.soTimeout = 5_000
        processWorkLoop()
    }

    private fun processWorkLoop() {
        val ipAddress = InetAddress.getByName(ip)
        var running = true
        try {
            while (running) {
                val command = getUdpCommand(clientIn)
                sendUdpReliably(command.first, ipAddress, port, clientSocket)
                command.second(receivingBuffer, sendingBuffer, ipAddress, port, clientSocket)
                running = !command.first.equals("SHUTDOWN", ignoreCase = true) &&
                        !command.first.equals("EXIT", ignoreCase = true)
            }
        } catch (e: UdpConnectionException) {
            println("Shutting down server")
        }
    }

}