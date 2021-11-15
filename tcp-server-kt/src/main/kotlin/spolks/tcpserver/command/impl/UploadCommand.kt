package spolks.tcpserver.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.command.CommandPayload
import spolks.tcpserver.command.impl.exception.CommandFlowException
import spolks.tcpserver.command.impl.exception.IllegalCommandArgsException
import kotlin.math.ceil

class UploadCommand : FileCommand() {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
        println("#Upload started")
        val file = checkUploadPreconditions(commandPayload, output)
        val filename = commandPayload.commandWithArgs.substring(commandPayload.commandName.length + 1)
        val fileLength = input.readLong()
        val segmentSize = input.readInt()
        val segmentsAmount = ceil(fileLength.toDouble() / segmentSize).toInt()
        output.writeInt(segmentsAmount)
        val clientId = commandPayload.clientId
        val startFrom = 0L
        processUploading(file, clientId, filename, segmentSize, segmentsAmount, startFrom, output, input)
    }

    private fun checkUploadPreconditions(
        commandPayload: CommandPayload,
        output: DataOutputStream
    ): File {
        if (commandPayload.commandName == commandPayload.commandWithArgs.trim()) {
            output.writeUTF("$ERROR No filename provided")
            throw IllegalCommandArgsException("No filename provided")
        }

        val filename = commandPayload.commandWithArgs.substring(commandPayload.commandName.length + 1)

        val file = createFile(filename)
        if (!file.exists()) {
            output.writeUTF("$ERROR can't create file")
            throw CommandFlowException("Can't create file with filename $filename}")
        }
        output.writeUTF(OK)
        return file
    }
}
