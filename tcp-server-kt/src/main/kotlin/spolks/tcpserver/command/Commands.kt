package spolks.tcpserver.command

fun parseCommandPayload(message: String, clientId: Int): CommandPayload {
    require(message.isNotBlank()) { "command shouldn't be blank" }
    val parts = message.split(" ")
    val commandName = parts[0]
    val args = if (parts.size > 1) parts.apply { subList(1, lastIndex) } else emptyList()
    return CommandPayload(commandName, args, message, clientId)
}

fun parseCommandName(commandName: String): CommandName =
    try {
        CommandName.valueOf(commandName.uppercase())
    } catch (e: Exception) {
        CommandName.UNRESOLVED
    }
