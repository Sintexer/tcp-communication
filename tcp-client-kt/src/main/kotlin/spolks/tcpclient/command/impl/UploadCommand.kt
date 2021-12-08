package spolks.tcpclient.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import spolks.tcpclient.DEFAULT_SEGMENT_SIZE
import spolks.tcpclient.OK
import spolks.tcpclient.command.CommandAndArgs
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.terminal.ClientInputReader
import spolks.tcpclient.utils.uploadFile

class UploadCommand : FileCommand() {
    override val isTerminationCommand = false

    override fun execute(
        commandAndArgs: CommandAndArgs,
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    ) {
        val file = checkUploadPreconditions(commandAndArgs)
        sendCommandToServer(commandAndArgs, input, output)
        val serverUploadReady = input.readUTF()
        println("#Server is ready for upload: $serverUploadReady")
        if (!serverUploadReady.startsWith(OK)) {
            throw CommandFlowException(serverUploadReady)
        }
        val fileSize = file.length()
        output.writeLong(fileSize)
        val segmentSize = DEFAULT_SEGMENT_SIZE
        output.writeInt(segmentSize)
        val segmentsAmount = input.readInt()
        val startFrom = input.readLong()
        val bitrate = uploadFile(file, startFrom, segmentsAmount, segmentSize, output, input)
        println("#Upload successful")
    }

    private fun checkUploadPreconditions(
        commandAndArgs: CommandAndArgs
    ): File {
        if (commandAndArgs.commandName.name == commandAndArgs.commandWithArgs.trim()) {
            throw CommandFlowException("No filename provided")
        }
        val filename = commandAndArgs.commandWithArgs.substring(commandAndArgs.commandName.name.length + 1)
        val file = getFile(filename)
        if (file == null || !file.exists()) {
            throw CommandFlowException("Can't open file with filename $filename}")
        }
        return file
    }
}
