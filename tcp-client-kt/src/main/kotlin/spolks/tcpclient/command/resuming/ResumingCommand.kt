package spolks.tcpclient.command.resuming

import java.io.DataInputStream
import java.io.DataOutputStream
import spolks.tcpclient.terminal.ClientInputReader

interface ResumingCommand {
    fun execute(
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    )
}
