package spolks.tcpserver.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.command.CommandPayload
import spolks.tcpserver.command.impl.exception.IllegalCommandArgsException
import kotlin.math.ceil

class DownloadCommand : FileCommand() {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
        if (commandPayload.commandName == commandPayload.commandWithArgs.trim()) {
            output.writeUTF("$ERROR No filename provided")
            throw IllegalCommandArgsException("No filename provided")
        }

        val filename = commandPayload.commandWithArgs.substring(commandPayload.commandName.length + 1)

        val file = getFile(filename)
        if (file == null || !file.exists()) {
            output.writeUTF("$ERROR file not found")
            throw FileNotFoundException("File with name $filename wasn't found")
        }
        output.writeUTF(OK)

        output.writeUTF(filename)
        val fileSize = file.length()

        val segmentSize = input.readInt()
        val segmentsAmount = ceil(fileSize.toDouble() / segmentSize).toInt()
        output.writeInt(segmentsAmount)

        val clientId = commandPayload.clientId
        downloadFile(file, clientId, filename, segmentSize, segmentsAmount, 0, output, input)
        println("#File download finished")
    }
}
