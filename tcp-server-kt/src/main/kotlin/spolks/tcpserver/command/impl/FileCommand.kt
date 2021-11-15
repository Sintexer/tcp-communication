package spolks.tcpserver.command.impl

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import spolks.tcpserver.OK
import spolks.tcpserver.RESOURCES_FOLDER
import spolks.tcpserver.command.Command
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.files.FileInfo
import spolks.tcpserver.files.FileInfoStorage

abstract class FileCommand : Command {
    fun getFile(filename: String): File? {
        if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
            Files.createDirectory(Path.of(RESOURCES_FOLDER))
        }
        return File("$RESOURCES_FOLDER/$filename")
    }

    fun createFile(filename: String): File {
        getResourcesFolder()
        val file = File("$RESOURCES_FOLDER/$filename")
        if (!file.exists() || file.delete()) {
            file.createNewFile()
        }
        return file
    }

    fun getResourcesFolder(): File {
        if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
            Files.createDirectory(Path.of(RESOURCES_FOLDER))
        }
        return File(RESOURCES_FOLDER)
    }

    fun downloadFile(
        file: File?,
        clientId: Int,
        filename: String,
        segmentSize: Int,
        segmentsAmount: Int,
        startFrom: Long,
        output: DataOutputStream,
        input: DataInputStream
    ) {
        val fileIn: InputStream = BufferedInputStream(FileInputStream(file!!))
        fileIn.skip(startFrom)
        output.writeLong(startFrom)
        fileIn.use {
            val startAt = Date().time
            FileInfoStorage.putDownloadInfo(clientId, FileInfo(filename, 0))
            val buffer = ByteArray(segmentSize)
            for (i: Int in 0 until segmentsAmount) {
                val bytesRead = fileIn.read(buffer, 0, segmentSize)
                output.write(buffer, 0, bytesRead)
                val bytesTransferred = FileInfoStorage.getDownloadInfo(clientId)!!.bytesTransferred + bytesRead
                FileInfoStorage.putDownloadInfo(clientId, FileInfo(filename, bytesTransferred))
                if (!input.readUTF().startsWith(OK)) {
                    println("Client failed to download file")
                    throw CommandFlowException("#Timeout")
                }
            }
            if (!input.readUTF().startsWith(OK)) {
                println("Client failed to download file")
            } else {
                val timeSpent = ((Date().time - startAt + 1) / 1000) + 1
                val bitrate = FileInfoStorage.getDownloadInfo(clientId)!!.bytesTransferred / timeSpent
                output.writeLong(bitrate)
                println("#Download bitrate is: $bitrate")
                FileInfoStorage.removeDetails(clientId)
            }
        }
    }

    fun processUploading(
        file: File,
        clientId: Int,
        filename: String,
        segmentSize: Int,
        segmentsAmount: Int,
        startFrom: Long,
        output: DataOutputStream,
        input: DataInputStream
    ) {
        val startAt = Date().time
        output.writeLong(startFrom)
        val fileOut = BufferedOutputStream(FileOutputStream(file, startFrom != 0L))
        FileInfoStorage.putUploadInfo(clientId, FileInfo(filename, 0))
        fileOut.use {
            for (i: Int in 0 until segmentsAmount) {
                val buffer = ByteArray(segmentSize)
                val bytesRead = input.read(buffer)
                fileOut.write(buffer, 0, bytesRead)
                val bytesTransferred = FileInfoStorage.getUploadInfo(clientId)!!.bytesTransferred + bytesRead
                FileInfoStorage.putUploadInfo(clientId, FileInfo(filename, bytesTransferred))
                output.writeUTF(OK)
            }
        }
        output.writeUTF(OK)
        val timeSpent = ((Date().time - startAt + 1) / 1000) + 1
        val bitrate = FileInfoStorage.getUploadInfo(clientId)!!.bytesTransferred / timeSpent
        output.writeLong(bitrate)
        println("#Upload finished")
        println("#Upload bitrate is: $bitrate")
        FileInfoStorage.removeDetails(clientId)
    }
}
