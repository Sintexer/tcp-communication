package spolks.tcpserver.session

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import spolks.tcpserver.CONTINUE
import spolks.tcpserver.ERROR
import spolks.tcpserver.OK
import spolks.tcpserver.command.Command
import spolks.tcpserver.command.CommandName
import spolks.tcpserver.command.CommandPayload
import spolks.tcpserver.command.CommandStatus
import spolks.tcpserver.command.CommandStorage
import spolks.tcpserver.command.parseCommandName
import spolks.tcpserver.command.parseCommandPayload

class SessionProcessing(
    private val client: Socket,
    private val input: DataInputStream = DataInputStream(client.getInputStream()),
    private val output: DataOutputStream = DataOutputStream(client.getOutputStream())
) : Closeable {
    var shutdown = false

    fun run() {
        val clientId = resolveClientId(input.readInt())
        println("#Client id is $clientId")
        output.writeInt(clientId)
        processPendingCommands(clientId)
        processWorkLoop(clientId)
    }

    override fun close() {
        input.close()
        output.close()
    }

    private fun processWorkLoop(clientId: Int) {
        var stop = false
        do {
            val commandPayload = parseCommandPayload(input.readUTF(), clientId)
            val command = receiveCommand(clientId, commandPayload)
            command?.let {
                it.execute(commandPayload, input, output)
                stop = it.terminationCommand
                SessionsStorage.getInfo(clientId).apply {
                    status = CommandStatus.COMPLETED
                }
                shutdown = CommandName.SHUTDOWN.name.equals(commandPayload.commandName, ignoreCase = true)
            }
        } while (!stop)
    }

    private fun receiveCommand(clientId: Int, commandPayload: CommandPayload): Command? {
        return try {
            val commandName = parseCommandName(commandPayload.commandName)
            SessionsStorage.getInfo(clientId).apply {
                command = commandName
                status = CommandStatus.IN_PROGRESS
            }
            if (commandName != CommandName.UNRESOLVED) {
                output.writeUTF(OK)
            } else {
                output.writeUTF("$ERROR unrecognized command")
            }
            CommandStorage.get(commandName)
        } catch (e: IllegalArgumentException) {
            output.writeUTF("$ERROR ${e.message}")
            null
        }
    }

    private fun processPendingCommands(clientId: Int) {
        SessionsStorage.getInfo(clientId).let {
            if (it.status == CommandStatus.IN_PROGRESS) {
                output.writeUTF(it.command.name)
                val clientResponse = input.readUTF()
                if (clientResponse.startsWith(CONTINUE)) {
                    CommandStorage.getResumingCommand(it.command)
                        .execute(CommandPayload(it.command.name, emptyList(), it.command.name, clientId), input, output)
                } else {
                    println(">Client responded with $clientResponse")
                    println(input.readUTF())
                }
                it.status = CommandStatus.COMPLETED
            } else {
                output.writeUTF("")
            }
        }
    }

    private fun resolveClientId(receivedClientId: Int): Int {
        return if (receivedClientId == 0) SessionIdentifierGenerator.next else receivedClientId
    }
}
