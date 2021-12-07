package spolks.tcpclient.command.impl

import spolks.tcpclient.command.Command
import spolks.tcpclient.command.CommandAndArgs
import spolks.tcpclient.terminal.ClientInputReader
import java.io.DataInputStream
import java.io.DataOutputStream

class EchoCommand : Command {
    override val isTerminationCommand = false

    override fun execute(
        commandAndArgs: CommandAndArgs,
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    ) {
        sendCommandToServer(commandAndArgs, input, output)
        println("Server responded with: ${input.readUTF()}")
    }
}
