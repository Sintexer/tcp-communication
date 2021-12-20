package spolks.tcpserver.server

import spolks.tcpserver.session.UdpSessionProcessing
import java.io.IOException

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
