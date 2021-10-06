package spolks.tcpserver.command

import spolks.tcpserver.command.impl.EmptyCommand
import java.util.*

object CommandStorage {
    private val commands: MutableMap<CommandName, () -> Command> = EnumMap(CommandName::class.java)

    init {
        commands.apply {
            put(CommandName.UNRESOLVED, ::EmptyCommand)
        }
    }

    fun get(name: CommandName) = commands.getOrDefault(name, ::EmptyCommand)()
}
