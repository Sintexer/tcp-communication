package spolks.tcpserver.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.LocalDateTime
import spolks.tcpserver.command.Command
import spolks.tcpserver.command.CommandPayload

class TimeCommand : Command {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
        output.writeUTF(LocalDateTime.now().toString())
    }
}
