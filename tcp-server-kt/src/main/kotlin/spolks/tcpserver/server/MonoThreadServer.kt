package spolks.tcpserver.server

import spolks.tcpserver.session.SessionProcessing
import java.io.IOException
import java.net.ServerSocket

class MonoThreadServer(
    private val port: Int
) : Runnable {

    override fun run() {
        try {
            ServerSocket(port).use { server ->
                do {
                    println("#Waiting for client connection")
                    try {
                        server.accept().use { client ->
                            SessionProcessing(client).use { run() }
                        }
                    } catch (e: IOException) {
                        println("#Client error occurred: $e")
                    }
                } while (true)
            }
        } catch (e: IOException) {
            println("#Server exception occurred: $e")
        }
    }
}
