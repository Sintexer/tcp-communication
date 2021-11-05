package spolks.tcpserver.command.resuming

import java.io.DataInputStream
import java.io.DataOutputStream
import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.command.CommandPayload
import spolks.tcpserver.command.impl.FileCommand
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.files.FileInfoStorage
import kotlin.math.ceil

class ResumeUploadCommand : FileCommand() {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
        val clientId = commandPayload.clientId
        val uploadInfo = FileInfoStorage.getUploadInfo(clientId)
        if (uploadInfo == null) {
            output.writeUTF("$ERROR can't find info about uploading process")
            throw CommandFlowException("$ERROR can't find info about uploading process")
        }
        val filename = uploadInfo.fileName
        val file = getFile(filename)
        if (file == null || (uploadInfo.bytesTransferred > 0 && !file.exists())) {
            output.writeUTF("$ERROR info that already was uploaded wasn't found")
            throw CommandFlowException("$ERROR can't find info about uploading process")
        }
        output.writeUTF(OK)

        output.writeUTF(filename)
        val startFrom = uploadInfo.bytesTransferred.toLong()
        output.writeLong(startFrom)
        val clientResponse = input.readUTF()
        if (!clientResponse.startsWith(OK)) {
            throw CommandFlowException("#Client error: $clientResponse")
        }

        val fileLength = input.readLong()
        val segmentSize = input.readInt()
        val segmentsAmount = ceil((fileLength.toDouble() - startFrom) / segmentSize).toInt()
        output.writeInt(segmentsAmount)

        processUploading(file, clientId, filename, segmentSize, segmentsAmount, startFrom, output, input)
    }
}
