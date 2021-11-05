package spolks.tcpclient.client

import java.io.IOException
import java.net.Socket
import spolks.tcpclient.session.SessionProcessing
import spolks.tcpclient.terminal.TerminalInputReader

class TcpClient(
    private val ip: String,
    private val port: Int,
    private val clientId: Int
) : Runnable {
    override fun run() {
        try {
            Socket(ip, port).use { SessionProcessing(it, TerminalInputReader()).run(clientId) }
        } catch (e: IOException) {
            println("#IOException")
            e.printStackTrace()
        }
    }
}
