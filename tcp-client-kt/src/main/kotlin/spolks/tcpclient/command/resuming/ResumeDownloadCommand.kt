package spolks.tcpclient.command.resuming

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import spolks.tcpclient.DEFAULT_SEGMENT_SIZE
import spolks.tcpclient.ERROR
import spolks.tcpclient.OK
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.terminal.ClientInputReader
import spolks.tcpclient.utils.getFile

class ResumeDownloadCommand : ResumingCommand {
    override fun execute(clientIn: ClientInputReader, input: DataInputStream, output: DataOutputStream) {
        println("#Resuming file download")
        val file = checkResumeDownloadPreConditions(input, output)
        output.writeUTF(OK)

        output.writeLong(file.length())
        val segmentSize = DEFAULT_SEGMENT_SIZE
        output.writeInt(segmentSize)
        val segmentsToDownload = input.readInt()
        val startFrom = input.readLong()
        val fileOut: OutputStream = BufferedOutputStream(FileOutputStream(file, startFrom != 0L))
        fileOut.use {
            for (i: Int in 0 until segmentsToDownload) {
                val buffer = ByteArray(segmentSize)
                val bytesRead = input.read(buffer)
                fileOut.write(buffer, 0, bytesRead)
            }
        }
        output.writeUTF(OK)
        val bitrate = input.readLong()
        println("#File downloaded")
        println("#Bitrate is: $bitrate")
    }

    private fun checkResumeDownloadPreConditions(input: DataInputStream, output: DataOutputStream): File {
        val serverDownloadReady = input.readUTF()
        println("#Server is ready for download: $serverDownloadReady")
        if (!serverDownloadReady.startsWith(OK)) {
            throw CommandFlowException(serverDownloadReady)
        }
        val filename = input.readUTF()

        val file = getFile(filename)
        if (!file.exists()) {
            output.writeUTF("$ERROR file not found")
            throw CommandFlowException("$ERROR file not found")
        }
        return file
    }
}
