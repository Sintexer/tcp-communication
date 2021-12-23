package spolks.tcpserver.command

fun parseCommandPayload(message: String, clientId: Int, offset: Int = 0): CommandPayload {
    val payload = message.substring(offset)
    require(payload.isNotBlank()) { "command shouldn't be blank" }
    val parts = payload.split(" ")
    val commandName = parts[0]
    val args = if (parts.size > 1) parts.apply { subList(1, lastIndex) } else emptyList()
    return CommandPayload(commandName, args, payload, clientId)
}

fun parseCommandName(commandName: String): CommandName =
    try {
        CommandName.valueOf(commandName.uppercase())
    } catch (e: Exception) {
        CommandName.UNRESOLVED
    }
