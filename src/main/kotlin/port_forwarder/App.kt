package port_forwarder

import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

val SOCKET_TIMEOUT: Int = TimeUnit.MINUTES.toMillis(1).toInt()

data class AddressAndPort(val host: String, val port: Int)

class TransferDataFromTo(private val sourceStream: InputStream, private val destStream: OutputStream): Runnable {
    override fun run() {
        while (true) {
            val buffer = ByteArray(20000)
            val bytesReadSource = sourceStream.read(buffer);
            if (bytesReadSource < 0) {
                break;
            }
            destStream.write(buffer, 0, bytesReadSource)
        }
    }
}

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: <bind interface/port> <forward to ip/port>")
        println("Example arguments: 10.0.75.0:3128 localhost:3128")

        exitProcess(1)
    }

    val bindIntf = parseAddressAndPort(args[0])
    val destinationHost = parseAddressAndPort(args[1])
    val serverSocket = ServerSocket(bindIntf.port.toInt(), 5, InetAddress.getByName(bindIntf.host))
    while (true) {
        val clientSocket = serverSocket.accept()
        clientSocket.soTimeout = SOCKET_TIMEOUT

        thread {
            clientSocket.use {
                println("Incoming connection from ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")
                val destSocket = Socket(destinationHost.host, destinationHost.port)
                destSocket.soTimeout = SOCKET_TIMEOUT
                val t1 = Thread(TransferDataFromTo(clientSocket.getInputStream(), destSocket.getOutputStream()))
                val t2 = Thread(TransferDataFromTo(destSocket.getInputStream(), clientSocket.getOutputStream()))
                t1.start()
                t2.start()
                t1.join()
                t2.join()
                println("Connection ${clientSocket.inetAddress.hostAddress}:${clientSocket.port} ended")
            }
        }
    }
}

fun parseAddressAndPort(s: String): AddressAndPort {
    val parts = s.split(':')
    if (parts.size == 2) {
        return AddressAndPort(parts[0], parts[1].toInt(10));
    } else {
        throw IllegalArgumentException("invalid argument: $s (expected host:port)")
    }
}
