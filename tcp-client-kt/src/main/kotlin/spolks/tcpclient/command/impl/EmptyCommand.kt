package spolks.tcpclient.command.impl

import spolks.tcpclient.command.Command
import spolks.tcpclient.command.CommandAndArgs
import spolks.tcpclient.terminal.ClientInputReader
import java.io.DataInputStream
import java.io.DataOutputStream

class EmptyCommand : Command {
    override val isTerminationCommand = false

    override fun execute(
        commandAndArgs: CommandAndArgs,
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    ) {
    }
}
