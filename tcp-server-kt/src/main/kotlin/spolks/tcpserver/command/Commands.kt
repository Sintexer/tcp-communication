package spolks.tcpserver.command

fun parseCommandPayload(message: String): CommandPayload {
    require(message.isNotBlank())
    val parts = message.split(" +")
    val commandName = parts[0]
    val args = if (parts.size > 1) parts.apply { subList(1, lastIndex) } else emptyList()
    return CommandPayload(commandName, args)
}

fun parseCommandName(commandName: String): CommandName =
    try {
        CommandName.valueOf(commandName)
    } catch (e: Exception) {
        CommandName.UNRESOLVED
    }
