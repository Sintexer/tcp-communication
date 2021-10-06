package spolks.tcpserver

import spolks.tcpserver.server.MonoThreadServer

fun main() {
    MonoThreadServer(34543).run()
}
