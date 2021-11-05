package spolks.tcpserver.command

import java.util.*
import spolks.tcpserver.command.impl.DisconnectCommand
import spolks.tcpserver.command.impl.DownloadCommand
import spolks.tcpserver.command.impl.EchoCommand
import spolks.tcpserver.command.impl.EmptyCommand
import spolks.tcpserver.command.impl.HelpCommand
import spolks.tcpserver.command.impl.ShowFilesCommand
import spolks.tcpserver.command.impl.ShutdownCommand
import spolks.tcpserver.command.impl.TimeCommand
import spolks.tcpserver.command.impl.UploadCommand
import spolks.tcpserver.command.resuming.ResumeDownloadCommand
import spolks.tcpserver.command.resuming.ResumeUploadCommand

object CommandStorage {
    private val commands: MutableMap<CommandName, () -> Command> = EnumMap(CommandName::class.java)
    private val resumingCommands: MutableMap<CommandName, () -> Command> = EnumMap(CommandName::class.java)
    private val commandsHelp: MutableMap<CommandName, String> = EnumMap(CommandName::class.java)

    init {
        commands.apply {
            put(CommandName.UNRESOLVED, ::EmptyCommand)
            put(CommandName.SHUTDOWN, ::ShutdownCommand)
            put(CommandName.HELP, ::HelpCommand)
            put(CommandName.TIME, ::TimeCommand)
            put(CommandName.DISCONNECT, ::DisconnectCommand)
            put(CommandName.EXIT, ::DisconnectCommand)
            put(CommandName.ECHO, ::EchoCommand)
            put(CommandName.DOWNLOAD, ::DownloadCommand)
            put(CommandName.FILES, ::ShowFilesCommand)
            put(CommandName.UPLOAD, ::UploadCommand)
        }
        resumingCommands.apply {
            put(CommandName.DOWNLOAD, ::ResumeDownloadCommand)
            put(CommandName.UPLOAD, ::ResumeUploadCommand)
        }
        commandsHelp.apply {
            put(CommandName.SHUTDOWN, "- Shuts down the server")
            put(CommandName.HELP, "- Shows all known commands and description for them")
            put(CommandName.TIME, "- Shows server's time")
            put(CommandName.DISCONNECT, "- Disconnects client from server (server will still running)")
            put(CommandName.EXIT, "- Alias for DISCONNECT command")
            put(CommandName.ECHO, "[message] - Returns message in response")
            put(CommandName.DOWNLOAD, "(filename) - Download file from server")
            put(CommandName.FILES, "- Shows server files")
            put(CommandName.UPLOAD, "(filename) - Upload file to server")
        }
    }

    fun get(name: CommandName) = commands.getOrDefault(name, ::EmptyCommand)()
    fun getResumingCommand(name: CommandName) = resumingCommands.getOrDefault(name, ::EmptyCommand)()

    fun getHelp(): String {
        return commandsHelp.map { "${it.key.name} ${it.value};" }.joinToString(System.lineSeparator())
    }
}
