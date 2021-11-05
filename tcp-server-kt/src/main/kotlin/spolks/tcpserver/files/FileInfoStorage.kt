package spolks.tcpserver.files

import java.util.concurrent.ConcurrentHashMap

object FileInfoStorage {
    private val downloadStorage: MutableMap<Int, FileInfo> = ConcurrentHashMap()
    private val uploadStorage: MutableMap<Int, FileInfo> = ConcurrentHashMap()

    fun getDownloadInfo(clientId: Int) = downloadStorage[clientId]
    fun putDownloadInfo(clientId: Int, fileInfo: FileInfo) = downloadStorage.put(clientId, fileInfo)
    fun getUploadInfo(clientId: Int) = uploadStorage[clientId]
    fun putUploadInfo(clientId: Int, fileInfo: FileInfo) = uploadStorage.put(clientId, fileInfo)
    fun removeDetails(clientId: Int) = downloadStorage.remove(clientId)
}
