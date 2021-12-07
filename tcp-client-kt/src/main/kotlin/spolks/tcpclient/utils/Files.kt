package spolks.tcpclient.utils

import spolks.tcpclient.OK
import spolks.tcpclient.RESOURCES_FOLDER
import spolks.tcpclient.command.exception.CommandFlowException
import java.io.*
import java.nio.file.Files
import java.nio.file.Path

fun getFile(filename: String): File {
    resourcesFolderExists()
    return File("$RESOURCES_FOLDER/$filename")
}

private fun resourcesFolderExists() {
    if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
        Files.createDirectory(Path.of(RESOURCES_FOLDER))
    }
}

fun uploadFile(
    file: File,
    startFrom: Long,
    segmentsAmount: Int,
    segmentSize: Int,
    output: DataOutputStream,
    input: DataInputStream
): Long {
    val fileIn = BufferedInputStream(FileInputStream(file))
    fileIn.skip(startFrom)
    fileIn.use {
        for (i: Int in 0 until segmentsAmount) {
            val buffer = ByteArray(segmentSize)
            val bytesRead = fileIn.read(buffer, 0, segmentSize)
            output.write(buffer, 0, bytesRead)
            if (!input.readUTF().startsWith(OK)) {
                println("Client failed to download file")
                throw CommandFlowException("#Timeout")
            }
        }
    }
    if (!input.readUTF().startsWith(OK)) {
        println("Client failed to download file")
        throw CommandFlowException("#Error while uploading file")
    } else {
        return input.readLong() // bitrate
    }
}
