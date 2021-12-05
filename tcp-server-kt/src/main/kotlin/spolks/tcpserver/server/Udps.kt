package spolks.tcpserver.server

import spolks.tcpserver.OK
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpConnectionException(message: String): Exception(message)

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
                throw UdpConnectionException("Превышено число попыток соединения с Udp клиентом")
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

fun sendUdpReliably(
    packet: ByteArray,
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
                throw UdpConnectionException("Превышено число попыток соединения с Udp клиентом")
            }
            sendPacket(packet, address, port, socket)
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
    socket.soTimeout = 10_000
    socket.receive(packet)
    return String(packet.data, 0, packet.length)
}

fun sendPacket(payload: String, address: InetAddress, port: Int, socket: DatagramSocket) {
    val buffer = payload.toByteArray()
    val packet = DatagramPacket(buffer, buffer.size, address, port)
    socket.send(packet)
}

fun sendPacket(packet: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket) {
    val pack = DatagramPacket(packet, packet.size, address, port)
    socket.send(pack)
}

fun receiveAck(address: InetAddress, port: Int, socket: DatagramSocket): Boolean {
    val buffer = ByteArray(1024)
    val msg = receivePacket(buffer, address, port, socket)
    if(msg != OK) println(msg)
    return msg == OK
}

fun receiveFileAck(address: InetAddress, port: Int, socket: DatagramSocket): Int {
    val buffer = ByteArray(64)
    return buffer.toString().toInt()
}

fun sendAck(buffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket) {
    return sendPacket(OK, address, port, socket)
}