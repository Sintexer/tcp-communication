package spolks.tcpclient.session

import spolks.tcpclient.CONTINUE
import spolks.tcpclient.STOP
import spolks.tcpclient.command.CommandStorage
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.command.parseCommandName
import spolks.tcpclient.terminal.ClientInputReader
import spolks.tcpclient.terminal.CommandAndArgsReader
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class SessionProcessing(
    private val server: Socket,
    private val clientIn: ClientInputReader,
    private val input: DataInputStream = DataInputStream(server.getInputStream()),
    private val output: DataOutputStream = DataOutputStream(server.getOutputStream())
) : Closeable {

    override fun close() {
        input.close()
        output.close()
        println("#Connection closed")
    }

    fun run(desiredClientId: Int) {
        output.writeInt(desiredClientId)
        val clientId = input.readInt()
        println(">Client id is: $clientId")
        processPendingCommand()
        processWorkLoop()
    }

    private fun processWorkLoop() {
        val commandNameReader = CommandAndArgsReader(clientIn)
        do {
            val commandAndArgs = commandNameReader.readNextCommandAndArgs()
            val command = CommandStorage.get(commandAndArgs.commandName)
            var stop = command.isTerminationCommand
            try {
                command.execute(commandAndArgs, clientIn, input, output)
            } catch (e: CommandFlowException) {
                stop = false
                println("#Command processing error: ${e.message}")
            }
        } while (!stop)
    }

    private fun processPendingCommand() {
        val serverMessage = input.readUTF()
        if (serverMessage.isNotBlank()) {
            val resumingCommandConstructor = CommandStorage.getResumingCommand(parseCommandName(serverMessage))
            if (resumingCommandConstructor != null) {
                output.writeUTF(CONTINUE)
                resumingCommandConstructor().execute(clientIn, input, output)
            } else {
                output.writeUTF(STOP)
                output.writeUTF("Resuming command wasn't resolved")
            }
        }
    }
}
