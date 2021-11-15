package spolks.tcpclient.command.impl

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import spolks.tcpclient.DEFAULT_SEGMENT_SIZE
import spolks.tcpclient.OK
import spolks.tcpclient.command.CommandAndArgs
import spolks.tcpclient.command.exception.CommandFlowException
import spolks.tcpclient.terminal.ClientInputReader

class DownloadCommand : FileCommand() {
    override val isTerminationCommand = false

    override fun execute(
        commandAndArgs: CommandAndArgs,
        clientIn: ClientInputReader,
        input: DataInputStream,
        output: DataOutputStream
    ) {
        sendCommandToServer(commandAndArgs, input, output)
        val serverDownloadReady = input.readUTF()
        println("#Server is ready for download: $serverDownloadReady")
        if (!serverDownloadReady.startsWith(OK)) {
            throw CommandFlowException(serverDownloadReady)
        }
        val filename = input.readUTF()

        val file = createFile(filename)
        val segmentSize = DEFAULT_SEGMENT_SIZE
        output.writeInt(segmentSize)
        val segmentsToDownload = input.readInt()
        val startFrom = input.readLong()
        val fileOut: OutputStream = BufferedOutputStream(FileOutputStream(file))
        fileOut.use {
            for (i: Int in 0 until segmentsToDownload) {
                val buffer = ByteArray(segmentSize)
                val bytesRead = input.read(buffer)
                fileOut.write(buffer, 0, bytesRead)
                output.writeUTF(OK)
            }
        }
        output.writeUTF(OK)
        val bitrate = input.readLong()
        println("#File downloaded")
    }
}
