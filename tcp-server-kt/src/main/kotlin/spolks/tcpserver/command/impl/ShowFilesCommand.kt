package spolks.tcpserver.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import spolks.tcpserver.command.CommandPayload

class ShowFilesCommand : FileCommand() {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
        val files = getResourcesFolder().list() ?: emptyArray()
        output.writeUTF(files.joinToString(", "))
    }
}
