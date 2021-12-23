package spolks.tcpserver.session

import java.util.concurrent.ConcurrentHashMap

object SessionsStorage {
    private val sessionInfoMap = ConcurrentHashMap<Int, SessionInfo>()
    private val sessionInProgressMap = ConcurrentHashMap<Int, Boolean>()
    private val ackTransferMap = ConcurrentHashMap<Int, Boolean>()
    private val clientInfo = ConcurrentHashMap<Int, ClientData>()

    fun getInfo(sessionId: Int) = sessionInfoMap.computeIfAbsent(sessionId) { SessionInfo() }
    fun setAsInProgress(sessionId: Int) = sessionInProgressMap.set(sessionId, true)
    fun setAsDone(sessionId: Int) = sessionInProgressMap.set(sessionId, false)
    fun isInProgress(sessionId: Int) = sessionInProgressMap.computeIfAbsent(sessionId) { false }
    fun setIsUnAckTransfer(sessionId: Int) = ackTransferMap.put(sessionId, false)
    fun setIsAckTransfer(sessionId: Int) = ackTransferMap.put(sessionId, true)
    fun isAckTransfer(sessionId: Int) = ackTransferMap.computeIfAbsent(sessionId) { true }

    fun has(sessionId: Int) = clientInfo[sessionId] != null
    fun putClientInfo(sessionId: Int, clientData: ClientData) = clientInfo.put(sessionId, clientData)
    fun getClientInfo(sessionId: Int) = clientInfo[sessionId]
}

class LocalSessionsStorage() {
    private val sessionInfoMap = ConcurrentHashMap<Int, SessionInfo>()

    fun getInfo(sessionId: Int) = sessionInfoMap.computeIfAbsent(sessionId) { SessionInfo() }
}
