package spolks.tcpserver

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import spolks.tcpserver.server.MonoThreadServer
import spolks.tcpserver.server.MonoThreadUdpServer

fun main(args: Array<String>) {
    val parser = ArgParser("tcp-server")
    val port = parser.option(ArgType.Int, "port", "p", "Server port").default(34543)
    val isUdp = parser.option(ArgType.Boolean, "udp", "u", "Run udp server").default(false)
    parser.parse(args)
    if(!isUdp.value) {
        MonoThreadServer(port.value).run()
    } else {
        MonoThreadUdpServer(port.value).run()
    }
}
