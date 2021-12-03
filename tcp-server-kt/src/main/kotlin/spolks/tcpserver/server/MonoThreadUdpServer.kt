package spolks.tcpserver.server

import spolks.tcpserver.session.SessionProcessing
import spolks.tcpserver.session.UdpSessionProcessing
import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.SocketTimeoutException

class MonoThreadUdpServer(
    private val port: Int
) : Runnable {
    private var shutdown = false

    override fun run() {
        try {
            DatagramSocket(port).use { server ->
                do {
                    println("#Waiting for UDP client connection")
                    server.soTimeout = 10_000_000
                    try {
                        shutdown = UdpSessionProcessing(server).run()
                    } catch (e: SocketTimeoutException){
                        println("Client didn't reconnect")
                    } catch (e: IOException) {
                        println("#Client error occurred: $e")
                    }
                } while (!shutdown)
            }
        } catch (e: IOException) {
            println("#Server exception occurred: $e")
            e.printStackTrace()
        }
    }
}