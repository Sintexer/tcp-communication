package spolks.tcpclient.command

data class CommandAndArgs(
    val commandName: CommandName,
    val args: String,
    val commandWithArgs: String
)
