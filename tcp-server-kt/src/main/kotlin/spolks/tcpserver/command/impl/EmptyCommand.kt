package spolks.tcpserver.command.impl

import spolks.tcpserver.command.Command

class EmptyCommand : Command {
    override val terminationCommand = false

    override fun execute() {
    }
}
