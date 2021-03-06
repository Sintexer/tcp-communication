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
                do {
                    println("#Waiting for UDP client connection")
                    shutdown = UdpSessionProcessing(port).run()
                } while (!shutdown)
        } catch (e: IOException) {
            println("#Server exception occurred: $e")
            e.printStackTrace()
        }
    }
}