package spolks.tcpclient

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import spolks.tcpclient.client.TcpClient
import spolks.tcpclient.client.UdpClient

fun main(args: Array<String>) {
    val parser = ArgParser("tcp-client")
    val ip by parser.option(ArgType.String, "ip", "i", "Tcp server ip address").required()
    val port by parser.option(ArgType.Int, "port", "p", "Tcp server port").required()
    val clientId by parser.option(ArgType.Int, "clientId", "id", "Client session id").default(0)
    val isUdp by parser.option(ArgType.Boolean, "udp", "u", "Run udp client").default(false)

    parser.parse(args)
    if(!isUdp){
        TcpClient(ip, port, clientId).run()
    } else {
        UdpClient(ip, port).run()
    }
}
