package spolks.tcpclient.session

import spolks.tcpclient.OK
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

val charset = Charsets.UTF_8

class UdpConnectionException(message: String) : Exception(message)

fun sendUdpReliably(
    str: String,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket,
    retries: Int = 5
) {
    var good = false
    var count = 0
    while (!good) {
        good = try {
            ++count
            if (count > retries) {
                throw UdpConnectionException("Превышено число попыток соединения с Udp сервером")
            }
            sendPacket(str, address, port, socket)
            receiveAck(address, port, socket)
            true
        } catch (e: UdpConnectionException) {
            println("Failed to send udp after $retries retries")
            throw e
        } catch (e: Exception) {
            false
        }
    }
}

fun receivePacket(buffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket): String {
    val packet = DatagramPacket(buffer, buffer.size, address, port)
    socket.receive(packet)
    return String(packet.data, 0, packet.length)
}

fun sendPacket(payload: String, address: InetAddress, port: Int, socket: DatagramSocket) {
    val buf = payload.toByteArray()
    val packet = DatagramPacket(buf, buf.size, address, port)
    socket.send(packet)
}

fun receiveAck(address: InetAddress, port: Int, socket: DatagramSocket): Boolean {
    val buffer = ByteArray(1024)
    return receivePacket(buffer, address, port, socket) == OK
}

fun sendAck(address: InetAddress, port: Int, socket: DatagramSocket) {
    return sendPacket(OK, address, port, socket)
}