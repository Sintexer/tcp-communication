package spolks.tcpserver.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import spolks.tcpserver.command.Command
import spolks.tcpserver.command.CommandPayload

class EmptyCommand : Command {
    override val terminationCommand = false

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
    }
}
