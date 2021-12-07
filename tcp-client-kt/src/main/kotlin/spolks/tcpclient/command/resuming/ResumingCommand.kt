package spolks.tcpclient.command.resuming

import spolks.tcpclient.terminal.ClientInputReader
import java.io.DataInputStream
import java.io.DataOutputStream

interface ResumingCommand {
    fun execute(
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    )
}
