package spolks.tcpserver.session

import java.util.concurrent.atomic.AtomicInteger

object SessionIdentifierGenerator {
    private var counter = 1
    val next: Int = counter++
}
