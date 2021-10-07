package spolks.tcpserver.session

import spolks.tcpserver.ERROR
import spolks.tcpserver.command.Command
import spolks.tcpserver.command.CommandStatus
import spolks.tcpserver.command.CommandStorage
import spolks.tcpserver.command.parseCommandName
import spolks.tcpserver.command.parseCommandPayload
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.IllegalArgumentException
import java.net.Socket

class SessionProcessing(
    private val client: Socket,
    private val input: DataInputStream = DataInputStream(client.getInputStream()),
    private val output: DataOutputStream = DataOutputStream(client.getOutputStream())
) : Closeable {

    fun run() {
        val clientId = resolveClientId(input.readInt())
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
            val command = receiveCommand(clientId)
            command?.let {
                it.execute()
                stop = it.terminationCommand
                SessionsStorage.getInfo(clientId).apply {
                    status = CommandStatus.COMPLETED
                }
            }
        } while (!stop)
    }

    private fun receiveCommand(clientId: Int): Command? {
        return try {
            val commandPayload = parseCommandPayload(input.readUTF())
            val commandName = parseCommandName(commandPayload.commandName)
            SessionsStorage.getInfo(clientId).apply {
                command = commandName
                status = CommandStatus.IN_PROGRESS
            }
            output.writeUTF(commandName.name)
            CommandStorage.get(commandName)
        } catch (e: IllegalArgumentException) {
            output.writeUTF("$ERROR ${e.message}")
            null
        }
    }

    private fun processPendingCommands(clientId: Int) {
        SessionsStorage.getInfo(clientId).let {
            if (it.status == CommandStatus.IN_PROGRESS) {
                CommandStorage.getResumingCommand(it.command).execute()
            }
            it.status = CommandStatus.COMPLETED
        }
    }

    private fun resolveClientId(receivedClientId: Int): Int {
        return if (receivedClientId == 0) SessionIdentifierGenerator.next else receivedClientId
    }
}
