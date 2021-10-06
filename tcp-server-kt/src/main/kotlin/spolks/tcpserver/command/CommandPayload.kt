package spolks.tcpserver.command

data class CommandPayload(
    val commandName: String,
    val args: List<String>
)
