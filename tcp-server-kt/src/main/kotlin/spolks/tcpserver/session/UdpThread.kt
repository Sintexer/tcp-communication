package spolks.tcpserver.session

import spolks.tcpserver.CONTINUE
import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.UDP_QUEUE_CAPACITY
import spolks.tcpserver.command.CommandStatus
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.command.parseCommandName
import spolks.tcpserver.command.parseCommandPayload
import spolks.tcpserver.command.processPendingUdpCommand
import spolks.tcpserver.command.processUdpCommand
import spolks.tcpserver.server.ServerAction
import spolks.tcpserver.server.sendUdpReliably
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

class UdpThread(
    private val clientId: Int,
    private val address: InetAddress,
    private val port: Int,
    private val socket: DatagramSocket,
    private val unprocessedMessages: ConcurrentHashMap<Int, BlockingQueue<Pair<ByteArray, Int>>>
) : Thread() {

    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)

    override fun run() {
        println("Start thread")
        SessionsStorage.setAsInProgress(clientId)
        try {

//            processPendingCommand(address, port, clientId)

            val packetAndLength =
                unprocessedMessages.computeIfAbsent(clientId) { ArrayBlockingQueue(UDP_QUEUE_CAPACITY) }.take()

            println("#Client  sent: ${String(packetAndLength.first, 0, packetAndLength.second)}")

//            processCommand(packet, address, port, clientId)
        } catch (e: CommandFlowException) {
            println(e.message)
        } catch (e: IOException) {
            println("#Client error occurred: $e")
        } finally {
            SessionsStorage.setAsDone(clientId)
        }
    }

    private fun processCommand(command: String, address: InetAddress, port: Int, clientId: Int): ServerAction {
        val commandPayload = parseCommandPayload(command, 0)
        val commandName = parseCommandName(commandPayload.commandName)
        SessionsStorage.getInfo(clientId).apply { this.command = commandName; this.status = CommandStatus.IN_PROGRESS }
        val state = processUdpCommand(commandPayload, receivingBuffer, address, port, socket, clientId)
        SessionsStorage.getInfo(clientId).apply { this.status = CommandStatus.COMPLETED }
        return state
    }

    private fun processPendingCommand(address: InetAddress, port: Int, clientId: Int) {
        if (SessionsStorage.getInfo(clientId).status != CommandStatus.COMPLETED) {
            val commandName = SessionsStorage.getInfo(clientId).command
            sendUdpReliably(commandName.name, address, port, socket)
            SessionsStorage.getInfo(clientId)
                .apply { this.command = commandName; this.status = CommandStatus.COMPLETED }
            processPendingUdpCommand(commandName.name, address, port, socket, clientId)
        } else {
            sendUdpReliably(CONTINUE, address, port, socket)
        }
    }
}
