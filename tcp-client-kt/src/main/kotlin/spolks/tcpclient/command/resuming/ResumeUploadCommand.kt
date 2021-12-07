package spolks.tcpclient.command.resuming

import spolks.tcpclient.DEFAULT_SEGMENT_SIZE
import spolks.tcpclient.ERROR
import spolks.tcpclient.OK
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.terminal.ClientInputReader
import spolks.tcpclient.utils.getFile
import spolks.tcpclient.utils.uploadFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class ResumeUploadCommand : ResumingCommand {
    override fun execute(clientIn: ClientInputReader, input: DataInputStream, output: DataOutputStream) {
        println("#Trying to continue file upload")
        checkServerIsReadyToStart(input)
        val filename = input.readUTF()
        println("#File name is $filename")
        val alreadyTransferred = input.readLong()
        val file = checkResumeUploadPreconditions(filename, alreadyTransferred, output)
        val fileLength = file.length()
        output.writeLong(fileLength)
        val segmentSize = DEFAULT_SEGMENT_SIZE
        output.writeInt(segmentSize)
        val segmentsAmount = input.readInt()
        val startFrom = input.readLong()
        val bitrate = uploadFile(file, startFrom, segmentsAmount, segmentSize, output, input)
        println("#Upload finished")
    }

    private fun checkServerIsReadyToStart(input: DataInputStream) {
        val serverMessage = input.readUTF()
        if (!serverMessage.startsWith(OK)) {
            throw CommandFlowException("#Server can't continue file upload: $serverMessage")
        }
    }

    private fun checkResumeUploadPreconditions(filename: String, startFrom: Long, output: DataOutputStream): File {
        val file = getFile(filename)
        if (!file.exists()) {
            output.writeUTF("$ERROR Can't continue file upload. File with name $filename doesn't exist")
            throw CommandFlowException("#Can't continue file upload. File with name $filename doesn't exist")
        }
        if (file.length() < startFrom) {
            output.writeUTF("$ERROR Can't continue file upload. Server requests file chunk beyond file size")
            throw CommandFlowException("#Can't continue file upload. Server requests file chunk beyond file size")
        }
        output.writeUTF(OK)
        return file
    }
}
