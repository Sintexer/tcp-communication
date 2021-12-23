package spolks.tcpclient.session

import spolks.tcpclient.CONTINUE
import spolks.tcpclient.UDP_DEFAULT_SO_TIMEOUT
import spolks.tcpclient.UDP_PACKET_SIZE
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.command.getUdpCommand
import spolks.tcpclient.command.processPendingCommand
import spolks.tcpclient.terminal.ClientInputReader
import java.net.DatagramSocket
import java.net.InetAddress

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
        var clientId = desiredClientId
        var clientSocket = DatagramSocket()
        clientSocket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
        try {
            while (running) {

                val command = getUdpCommand(clientIn)
                if (clientId == 0) {
                    sendUdpReliably(clientId.toString(), ipAddress, port, clientSocket)
                    clientId = receivePacket(receivingBuffer, ipAddress, port, clientSocket).also {
                        sendAck(
                            ipAddress,
                            port,
                            clientSocket
                        )
                    }.toInt()
                    println("Client id: $clientId")
                }
                try {
                    sendUdpReliably(command.first, ipAddress, port, clientSocket, clientId = clientId)
                    if (processPendingCommand(ipAddress, port, clientSocket, clientId)) {
                        command.second(command.first, receivingBuffer, ipAddress, port, clientSocket, clientId)
                        running = !command.first.equals("SHUTDOWN", ignoreCase = true) &&
                            !command.first.equals("EXIT", ignoreCase = true)
                    }
                } catch (e: CommandFlowException) {
                    println("#Command flow exception: $e")
                } finally {
                    while (dropPacket(ipAddress, port, clientSocket)){}
                }
            }
        } catch (e: UdpConnectionException) {
            println("Shutting down client")
        }
    }

    private fun processPendingCommand(address: InetAddress, port: Int, socket: DatagramSocket, clientId: Int): Boolean {
        val action = receivePacket(receivingBuffer, address, port, socket)
        return if (action != CONTINUE) {
            processPendingCommand(action, address, port, socket, clientId)
            false
        } else {
            true
        }
    }
}
