package spolks.tcpclient.command

fun parseCommandName(commandName: String): CommandName =
    try {
        CommandName.valueOf(commandName.uppercase())
    } catch (e: Exception) {
        CommandName.UNRESOLVED
    }

fun parseCommandFromString(commandWithArgs: String): CommandAndArgs {
    val indexOfFirstSpace = commandWithArgs.indexOf(' ')
    return if (indexOfFirstSpace < 0) {
        CommandAndArgs(parseCommandName(commandWithArgs), "", commandWithArgs)
    } else {
        val commandNameString = commandWithArgs.substring(0, indexOfFirstSpace)
        CommandAndArgs(
            parseCommandName(commandNameString),
            commandWithArgs.substring(indexOfFirstSpace + 1),
            commandWithArgs
        )
    }
}
