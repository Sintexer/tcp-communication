package spolks.tcpserver.session

import spolks.tcpserver.CONTINUE
import spolks.tcpserver.UDP_DEFAULT_SO_TIMEOUT
import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.command.*
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.server.ServerAction
import spolks.tcpserver.server.receivePacket
import spolks.tcpserver.server.sendAck
import spolks.tcpserver.server.sendUdpReliably
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UdpSessionProcessing(
    private val socket: DatagramSocket
) : Closeable {


    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)
    private val sendingBuffer = ByteArray(UDP_PACKET_SIZE)
    private var clientIdCounter = 1
    private val localSessionsStorage = LocalSessionsStorage()

    override fun close() {

    }

    fun run(): Boolean {
        return processWorkLoop()
    }

    private fun processWorkLoop(): Boolean {
        var running = true
        var previousAction = ServerAction.CONTINUE
        var currentClientId = 0
        while (running) {
            val packet = DatagramPacket(receivingBuffer, receivingBuffer.size)
            try {
                socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
                socket.receive(packet).also { sendAck(packet.address, packet.port, socket) }
                val address = packet.address
                val port = packet.port
                val desiredClientId = String(packet.data, 0, packet.length).toInt()
                currentClientId =
                    if (currentClientId != 0) currentClientId
                    else if (desiredClientId != 0) desiredClientId
                    else clientIdCounter++
                println("Client id: $currentClientId")
                sendUdpReliably(currentClientId.toString(), address, port, socket)

                processPendingCommand(address, port, currentClientId)

                val received =
                    receivePacket(receivingBuffer, address, port, socket).also { sendAck(address, port, socket) }
                println("#Client  sent: $received")

                previousAction = processCommand(received, address, port, currentClientId)
            } catch (e: CommandFlowException) {
                println(e.message)
            } catch (e: SocketTimeoutException) {
                println("Client didn't reconnect")
            } catch (e: IOException) {
                println("#Client error occurred: $e")
            }
            running = previousAction == ServerAction.CONTINUE
        }
        return previousAction == ServerAction.SHUTDOWN
    }

    private fun processCommand(command: String, address: InetAddress, port: Int, clientId: Int): ServerAction {
        val commandPayload = parseCommandPayload(command, 0)
        val commandName = parseCommandName(commandPayload.commandName)
        localSessionsStorage.getInfo(clientId).apply { this.command = commandName; this.status = CommandStatus.IN_PROGRESS }
        val state = processUdpCommand(commandPayload, receivingBuffer, address, port, socket, clientId)
        localSessionsStorage.getInfo(clientId).apply { this.status = CommandStatus.COMPLETED }
        return state
    }

    private fun processPendingCommand(address: InetAddress, port: Int, clientId: Int) {
        if (localSessionsStorage.getInfo(clientId).status != CommandStatus.COMPLETED) {
            val commandName = localSessionsStorage.getInfo(clientId).command
            sendUdpReliably(commandName.name, address, port, socket)
            localSessionsStorage.getInfo(clientId)
                .apply { this.command = commandName; this.status = CommandStatus.COMPLETED }
            processPendingUdpCommand(commandName.name, address, port, socket, clientId)
        } else {
            sendUdpReliably(CONTINUE, address, port, socket)
        }
    }
}