package spolks.tcpclient.client

import spolks.tcpclient.session.UdpSessionProcessing
import spolks.tcpclient.terminal.TerminalInputReader

class UdpClient(
    private val ip: String,
    private val port: Int,
    private val desiredClientId: Int
) {
    fun run() {
        UdpSessionProcessing(ip, port, TerminalInputReader()).run(desiredClientId)
    }
}