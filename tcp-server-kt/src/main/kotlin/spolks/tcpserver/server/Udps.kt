package spolks.tcpserver.server

import spolks.tcpserver.OK
import spolks.tcpserver.UDP_MAX_WAIT_FAILS
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import spolks.tcpserver.UDP_CLIENT_ID_SIZE

class UdpConnectionException(message: String): Exception(message)

fun sendUdpReliably(
    str: String,
    address: InetAddress,
    port: Int,
    socket: DatagramSocket,
    retries: Int = UDP_MAX_WAIT_FAILS
) {
    var good = false
    var count = 0
    while (!good) {
        good = try {
            ++count
            if (count > retries) {
                throw UdpConnectionException("Failed to send udp after $retries retries")
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
                throw UdpConnectionException("Failed to send udp after $retries retries")
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

fun receiveFilePacket(buffer: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket): Int {
    val packet = DatagramPacket(buffer, buffer.size, address, port)
    socket.receive(packet)
    return packet.length
}

fun sendPacket(payload: String, address: InetAddress, port: Int, socket: DatagramSocket) {
    val buffer = payload.toByteArray()
    val packet = DatagramPacket(buffer, buffer.size, address, port)
    socket.send(packet)
}

fun sendPacket(packet: ByteArray, address: InetAddress, port: Int, socket: DatagramSocket, packetSize: Int = packet.size) {
    val pack = DatagramPacket(packet, packetSize, address, port)
    socket.send(pack)
}

fun receiveAck(address: InetAddress, port: Int, socket: DatagramSocket): Boolean {
    val buffer = ByteArray(1024)
    val msg = receivePacket(buffer, address, port, socket).substring(UDP_CLIENT_ID_SIZE)
    if(msg != OK) println(msg)
    return msg == OK
}

fun receiveAck(message: String): Boolean {
    val msg = message.substring(UDP_CLIENT_ID_SIZE)
    if(msg != OK) println(msg)
    return msg == OK
}

fun receiveFileAck(address: InetAddress, port: Int, socket: DatagramSocket): Int {
    val buffer = ByteArray(64)
    val packet = DatagramPacket(buffer, buffer.size, address, port)
    socket.receive(packet)
    return String(buffer, 0, packet.length).toInt()
}

fun sendAck(address: InetAddress, port: Int, socket: DatagramSocket) {
    return sendPacket(OK, address, port, socket)
}

fun sendFileAck(id: Int, address: InetAddress, port: Int, socket: DatagramSocket) {
    sendPacket(id.toString(), address, port, socket)
}

fun dropPacket(address: InetAddress, port: Int, socket: DatagramSocket): Boolean {
    val prevSoTimeout = socket.soTimeout
    socket.soTimeout = 10
    return try {
        receivePacket(ByteArray(DEFAULT_BUFFER_SIZE), address, port, socket)
        true
    } catch (e: Exception) {
        false
    } finally {
        socket.soTimeout = prevSoTimeout
    }
}

fun getClientId(packet: ByteArray): Int {
    return String(packet, 0, packet.size).substring(0, UDP_CLIENT_ID_SIZE).toInt()
}