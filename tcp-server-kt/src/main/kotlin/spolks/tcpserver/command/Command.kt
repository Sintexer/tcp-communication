package spolks.tcpserver.command

import java.io.DataInputStream
import java.io.DataOutputStream

interface Command {
    val terminationCommand: Boolean
    fun execute(commandPayload: CommandPayload, input: DataInputStream, output: DataOutputStream)
}
