package spolks.tcpclient.terminal

interface ClientInputReader {
    fun readInt(): Int
    fun readString(): String
}
