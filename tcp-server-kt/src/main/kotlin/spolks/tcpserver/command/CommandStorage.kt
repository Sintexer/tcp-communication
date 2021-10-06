package spolks.tcpserver.command

import spolks.tcpserver.command.impl.EmptyCommand
import java.util.*

object CommandStorage {
    private val commands: MutableMap<CommandName, () -> Command> = EnumMap(CommandName::class.java)
    private val resumingCommands: MutableMap<CommandName, () -> Command> = EnumMap(CommandName::class.java)

    init {
        commands.apply {
            put(CommandName.UNRESOLVED, ::EmptyCommand)
        }
        resumingCommands.apply {
//            put()
        }
    }

    fun get(name: CommandName) = commands.getOrDefault(name, ::EmptyCommand)()
    fun getResumingCommand(name: CommandName) = resumingCommands.getOrDefault(name, ::EmptyCommand)()
}
