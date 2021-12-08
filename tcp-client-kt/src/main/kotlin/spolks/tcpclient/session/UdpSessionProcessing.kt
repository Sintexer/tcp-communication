package spolks.tcpclient.session

import java.net.DatagramSocket
import java.net.InetAddress
import spolks.tcpclient.CONTINUE
import spolks.tcpclient.UDP_DEFAULT_SO_TIMEOUT
import spolks.tcpclient.UDP_PACKET_SIZE
import spolks.tcpclient.command.getUdpCommand
import spolks.tcpclient.command.processPendingCommand
import spolks.tcpclient.terminal.ClientInputReader

class UdpSessionProcessing(
    private val ip: String,
    private val port: Int,
    private val clientIn: ClientInputReader
) {

    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)

    fun run(desiredClientId: Int) {

        processWorkLoop(desiredClientId)
    }

    private fun processWorkLoop(desiredClientId: Int) {
        val ipAddress = InetAddress.getByName(ip)
        var running = true
        var clientId = 0
        try {
            while (running) {
                val clientSocket = DatagramSocket()
                clientSocket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
                val command = getUdpCommand(clientIn)
                clientId = if (desiredClientId == 0) clientId else desiredClientId
                sendUdpReliably(clientId.toString(), ipAddress, port, clientSocket)
                clientId = receivePacket(receivingBuffer, ipAddress, port, clientSocket).also {
                    sendAck(
                        ipAddress,
                        port,
                        clientSocket
                    )
                }.toInt()
                println("Client id: $clientId")
                if (processPendingCommand(ipAddress, port, clientSocket)) {
                    sendUdpReliably(command.first, ipAddress, port, clientSocket)
                    command.second(receivingBuffer, ipAddress, port, clientSocket)
                    running = !command.first.equals("SHUTDOWN", ignoreCase = true) &&
                        !command.first.equals("EXIT", ignoreCase = true)
                }
            }
        } catch (e: UdpConnectionException) {
            println("Shutting down client")
        }
    }

    private fun processPendingCommand(address: InetAddress, port: Int, socket: DatagramSocket): Boolean {
        val action = receivePacket(receivingBuffer, address, port, socket).also { sendAck(address, port, socket) }
        return if (action != CONTINUE) {
            processPendingCommand(action, address, port, socket)
            false
        } else {
            true
        }
    }
}
