package spolks.tcpclient.command

import java.util.*
import spolks.tcpclient.command.impl.DisconnectCommand
import spolks.tcpclient.command.impl.DownloadCommand
import spolks.tcpclient.command.impl.EchoCommand
import spolks.tcpclient.command.impl.EmptyCommand
import spolks.tcpclient.command.impl.HelpCommand
import spolks.tcpclient.command.impl.ShowFilesCommand
import spolks.tcpclient.command.impl.ShutdownCommand
import spolks.tcpclient.command.impl.TimeCommand
import spolks.tcpclient.command.impl.UploadCommand
import spolks.tcpclient.command.resuming.ResumeDownloadCommand
import spolks.tcpclient.command.resuming.ResumeUploadCommand
import spolks.tcpclient.command.resuming.ResumingCommand

object CommandStorage {
    private val commands: MutableMap<CommandName, () -> Command> = EnumMap(CommandName::class.java)
    private val resumingCommands: MutableMap<CommandName, () -> ResumingCommand> = EnumMap(CommandName::class.java)

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
    }

    fun get(name: CommandName) = commands.getOrDefault(name, ::EmptyCommand)()
    fun getResumingCommand(name: CommandName) = resumingCommands[name]
}
