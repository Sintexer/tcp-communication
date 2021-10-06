package spolks.tcpserver.session

import java.util.concurrent.atomic.AtomicInteger

object SessionIdentifierGenerator {
    private val counter = AtomicInteger(1)
    val next: Int = counter.getAndIncrement()
}
