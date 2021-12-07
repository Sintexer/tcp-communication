package spolks.tcpclient.client

import spolks.tcpclient.session.SessionProcessing
import spolks.tcpclient.terminal.TerminalInputReader
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TcpClient(
    private val ip: String,
    private val port: Int,
    private val clientId: Int
) : Runnable {
    override fun run() {
        try {
            val address = InetSocketAddress(ip, port)
            val sock = Socket()
            sock.soTimeout = 10_000
            sock.connect(address, 30_000)
            sock.use { SessionProcessing(it, TerminalInputReader()).run(clientId) }
        } catch (e: IOException) {
            println("#IOException")
            e.printStackTrace()
        }
    }
}
