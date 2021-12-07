package spolks.tcpclient.command

import spolks.tcpclient.OK
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.terminal.ClientInputReader
import java.io.DataInputStream
import java.io.DataOutputStream

interface Command {
    val isTerminationCommand: Boolean

    fun execute(
        commandAndArgs: CommandAndArgs,
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    )

    fun sendCommandToServer(commandAndArgs: CommandAndArgs, input: DataInputStream, output: DataOutputStream) {
        output.writeUTF(commandAndArgs.commandWithArgs)
        val serverResponse = input.readUTF()
        if (!OK.equals(serverResponse, ignoreCase = true)) {
            throw CommandFlowException(serverResponse)
        }
    }
}
