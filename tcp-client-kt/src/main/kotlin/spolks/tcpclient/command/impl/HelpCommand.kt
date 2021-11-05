package spolks.tcpclient.command.impl

import java.io.DataInputStream
import java.io.DataOutputStream
import spolks.tcpclient.command.Command
import spolks.tcpclient.command.CommandAndArgs
import spolks.tcpclient.terminal.ClientInputReader

class HelpCommand : Command {
    override val isTerminationCommand = false

    override fun execute(
        commandAndArgs: CommandAndArgs,
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    ) {
        sendCommandToServer(commandAndArgs, input, output)
        println("#Showing help:")
        println(input.readUTF())
        println()
    }
}
