package spolks.tcpserver.server

import spolks.tcpserver.session.SessionProcessing
import java.io.IOException
import java.net.ServerSocket

class MonoThreadServer(
    val port: Int
) : Runnable {

    override fun run() {
        try {
            ServerSocket(port).use {
                println("#Waiting for client")
                try {
                    it.accept().use {
                        SessionProcessing(it).run()
                    }
                } catch (e: IOException) {
                    println("Client error occurred: $e")
                }
            }
        } catch (e: IOException) {
            println("Exception occurred: $e")
        }
    }
}
