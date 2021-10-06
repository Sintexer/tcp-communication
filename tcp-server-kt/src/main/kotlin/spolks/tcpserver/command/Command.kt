package spolks.tcpserver.command

interface Command {
    val terminationCommand: Boolean
    fun execute()
}
