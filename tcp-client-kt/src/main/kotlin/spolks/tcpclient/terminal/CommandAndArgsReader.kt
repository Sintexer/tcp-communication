package spolks.tcpclient.terminal

import spolks.tcpclient.command.CommandAndArgs
import spolks.tcpclient.command.CommandName
import spolks.tcpclient.command.parseCommandFromString

class CommandAndArgsReader(
    private val clientInputReader: ClientInputReader
) {

    fun readNextCommandAndArgs(): CommandAndArgs {
        var commandAndArgs = readCommand()
        while (commandAndArgs.commandName == CommandName.UNRESOLVED) {
            println("Can't recognize command '$commandAndArgs'")
            commandAndArgs = readCommand()
        }
        return commandAndArgs
    }

    private fun readCommand(): CommandAndArgs {
        val input = clientInputReader.readString()
        return parseCommandFromString(input)
    }
}
