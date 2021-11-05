package spolks.tcpserver.command.resuming

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.command.CommandPayload
import spolks.tcpserver.command.impl.FileCommand
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.files.FileInfo
import spolks.tcpserver.files.FileInfoStorage
import kotlin.math.ceil

class ResumeDownloadCommand : FileCommand() {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
        val clientId = commandPayload.clientId
        val fileInfo = FileInfoStorage.getDownloadInfo(clientId)
        val file = checkResumeFileDownloadPreconditions(fileInfo, output)
        val filename = fileInfo!!.fileName
        output.writeUTF(OK)

        output.writeUTF(filename)
        if (!input.readUTF().startsWith(OK)) {
            throw CommandFlowException("Client error")
        }
        val bytesTransferred = input.readLong()
        val fileSize = file.length()

        val segmentSize = input.readInt()
        val segmentsAmount = ceil((fileSize.toDouble() - bytesTransferred) / segmentSize).toInt()
        output.writeInt(segmentsAmount)
        downloadFile(file, clientId, filename, segmentSize, segmentsAmount, bytesTransferred, output, input)
    }

    private fun checkResumeFileDownloadPreconditions(fileInfo: FileInfo?, output: DataOutputStream): File {
        if (fileInfo == null) {
            output.writeUTF("$ERROR can't find resuming details")
            throw CommandFlowException("$ERROR can't find resuming details")
        }
        val filename = fileInfo.fileName
        val file = getFile(filename)
        if (file == null || !file.exists()) {
            output.writeUTF("$ERROR file not found")
            throw FileNotFoundException("File with name $filename wasn't found")
        }
        return file
    }
}
