package spolks.tcpserver.server

import spolks.tcpserver.UDP_DEFAULT_SO_TIMEOUT
import spolks.tcpserver.UDP_PACKET_SIZE
import spolks.tcpserver.UDP_QUEUE_CAPACITY
import spolks.tcpserver.session.ClientData
import spolks.tcpserver.session.SessionsStorage
import spolks.tcpserver.session.UdpThread
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MultiThreadUdpServer(
    private val port: Int
) : Runnable {
    private var shutdown = false
    private var clientIdCounter = 1

    private val receivingBuffer = ByteArray(UDP_PACKET_SIZE)
    private val socket = DatagramSocket(port)
    private val unprocessedMessages = ConcurrentHashMap<Int, BlockingQueue<Pair<ByteArray, Int>>>()

    init {
        socket.reuseAddress = true
        socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT
    }

    override fun run() {
        try {
            do {
                try {
                    unprocessedMessages.forEach { (clientId, queue) ->
                        if (!queue.isEmpty() && !SessionsStorage.isInProgress(clientId)) {
                            UdpThread(
                                clientId,
                                SessionsStorage.getClientInfo(clientId)!!.address,
                                SessionsStorage.getClientInfo(clientId)!!.port,
                                socket,
                                unprocessedMessages
                            ).start()
                        }
                    }
                    println("#Waiting for UDP client connection")
                    val packet = DatagramPacket(receivingBuffer, receivingBuffer.size)
                    val clientId = resolveClientId(packet)
                    socket.soTimeout = UDP_DEFAULT_SO_TIMEOUT

//                    if (!SessionsStorage.isInProgress(clientId)) {
//                        println("Client id: $clientId")
//                        UdpThread(clientId, packet.address, packet.port, socket, unprocessedMessages).start()
//                    }



                    TimeUnit.NANOSECONDS.sleep(10)
                } catch (e: SocketTimeoutException) {
                    socket.soTimeout = (socket.soTimeout * 1.5).roundToInt()
                }
            } while (!shutdown)
        } catch (e: IOException) {
            println("#Server exception occurred: $e")
            e.printStackTrace()
        }
    }

    private fun resolveClientId(packet: DatagramPacket): Int {
        socket.receive(packet).also { sendAck(packet.address, packet.port, socket) }
        val desiredClientId = getClientId(packet.data)
        val clientId = if (desiredClientId != 0) desiredClientId
        else clientIdCounter++
        if (!SessionsStorage.has(clientId)) {
            SessionsStorage.putClientInfo(clientId, ClientData(packet.address, packet.port))
            sendUdpReliably(clientId.toString(), packet.address, packet.port, socket)
        } else {
            unprocessedMessages.computeIfAbsent(clientId) { ArrayBlockingQueue(UDP_QUEUE_CAPACITY) }
                .offer(Pair(receivingBuffer.copyOf(), packet.length))
        }
        return clientId
    }
}
