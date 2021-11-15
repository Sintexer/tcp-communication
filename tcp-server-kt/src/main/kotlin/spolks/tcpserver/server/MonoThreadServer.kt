package spolks.tcpserver.server

import java.io.IOException
import java.net.ServerSocket
import spolks.tcpserver.session.SessionProcessing

class MonoThreadServer(
    private val port: Int
) : Runnable {
    private var shutdown = false

    override fun run() {
        try {
            ServerSocket(port).use { server ->
                server.soTimeout = 10_000_000
                do {
                    println("#Waiting for client connection")
                    try {
                        server.accept().use { client ->
                            println("#Connected with client")
                            SessionProcessing(client).use { sp ->
                                sp.run()
                                shutdown = sp.shutdown
                            }
                        }
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
