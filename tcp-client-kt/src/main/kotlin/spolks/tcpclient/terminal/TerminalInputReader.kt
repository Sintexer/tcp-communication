package spolks.tcpclient.terminal

import java.util.*

class TerminalInputReader : ClientInputReader {
    private val input = Scanner(System.`in`)

    override fun readInt() = input.nextInt()

    override fun readString() = input.nextLine()!!
}
