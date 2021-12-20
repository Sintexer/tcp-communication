package spolks.tcpserver.session

import java.net.InetAddress

data class ClientData(
    val address: InetAddress,
    val port: Int
)
