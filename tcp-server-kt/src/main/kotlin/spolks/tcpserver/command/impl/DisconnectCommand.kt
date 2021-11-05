package spolks.tcpserver.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import spolks.tcpserver.command.Command
import spolks.tcpserver.command.CommandPayload

class DisconnectCommand : Command {
    override val terminationCommand = true

    override fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream) {
    }
}
