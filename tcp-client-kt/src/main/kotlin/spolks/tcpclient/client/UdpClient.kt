package spolks.tcpclient.client

import spolks.tcpclient.session.UdpSessionProcessing
import spolks.tcpclient.terminal.TerminalInputReader
import java.net.DatagramSocket

class UdpClient(
    val ip: String,
    val port: Int
) {
    fun run() {
        DatagramSocket().use {
            UdpSessionProcessing(ip, port, it, TerminalInputReader()).run()
        }
    }
}