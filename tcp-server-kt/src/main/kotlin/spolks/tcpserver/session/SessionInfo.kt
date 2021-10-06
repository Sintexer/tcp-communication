package spolks.tcpserver.session

import spolks.tcpserver.command.CommandName
import spolks.tcpserver.command.CommandStatus

data class SessionInfo(
    var command: CommandName = CommandName.UNRESOLVED,
    var status: CommandStatus = CommandStatus.COMPLETED
)
