package spolks.tcpserver.session

import java.util.concurrent.ConcurrentHashMap

object SessionsStorage {
    private val sessionInfoMap = ConcurrentHashMap<Int, SessionInfo>()

    fun getInfo(sessionId: Int) = sessionInfoMap.computeIfAbsent(sessionId) { SessionInfo() }
}
