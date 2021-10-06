package spolks.tcpserver.session

import spolks.tcpserver.command.CommandStorage
import spolks.tcpserver.command.parseCommandName
import spolks.tcpserver.command.parseCommandPayload
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class SessionProcessing(
    private val client: Socket,
    private val input: DataInputStream = DataInputStream(client.getInputStream()),
    private val output: DataOutputStream = DataOutputStream(client.getOutputStream())
) {

    fun run() {
        val clientId = resolveClientId(input.readInt())
        output.writeInt(clientId)
        processWorkLoop()
    }

    private fun processWorkLoop() {
        var stop: Boolean
        do {
            val commandPayload = parseCommandPayload(input.readUTF())
            val command = CommandStorage.get(parseCommandName(commandPayload.commandName))
            command.execute()
            stop = command.terminationCommand
        } while (!stop)
    }

    private fun resolveClientId(receivedClientId: Int): Int {
        return if (receivedClientId == 0) SessionIdentifierGenerator.next else receivedClientId
    }
}
