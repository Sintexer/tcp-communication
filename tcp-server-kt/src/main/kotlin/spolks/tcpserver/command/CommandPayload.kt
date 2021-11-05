package spolks.tcpserver.command

data class CommandPayload(
    val commandName: String,
    val args: List<String>,
    val commandWithArgs: String,
    val clientId: Int
)
