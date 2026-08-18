package ai.platon.pulsar.chrome.protocol.transport

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the real reconnect mechanics of [KtorTransport]: a closed transport
 * must be able to re-establish the websocket to the same URI (the closed flag
 * reset + a fresh receiver-loop scope are exactly the parts that break without
 * the reconnect support).
 */
class KtorTransportReconnectTest {

    @Test
    fun `transport reconnects to the same uri after close`() {
        runBlocking {
            val server = MiniWsServer()
            try {
                val transport = KtorTransport.create(server.uri)
                assertTrue(transport.isOpen, "initial connect should succeed")
                assertEquals(1, server.accepted.get())

                transport.close()
                assertFalse(transport.isOpen, "transport should be closed")

                assertTrue(transport.reconnect(), "transport should reconnect to the same uri")
                assertTrue(transport.isOpen, "transport should be open after reconnect")
                assertEquals(2, server.accepted.get(), "reconnect should establish a new connection")
            } finally {
                server.stop()
            }
        }
    }

    /**
     * Minimal WebSocket server speaking just enough of RFC 6455 for the test:
     * accepts the upgrade, replies to close frames, and keeps connections open.
     */
    private class MiniWsServer {
        private val server = ServerSocket(0)
        val uri: URI = URI("ws://127.0.0.1:${server.localPort}/devtools/page/1")
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val running = AtomicBoolean(true)
        val accepted = AtomicInteger()

        init {
            Thread {
                while (running.get()) {
                    try {
                        val socket = server.accept()
                        accepted.incrementAndGet()
                        sockets.add(socket)
                        Thread { serve(socket) }.start()
                    } catch (_: Exception) {
                        break
                    }
                }
            }.start()
        }

        private fun serve(socket: Socket) {
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Read the HTTP upgrade request
                val headerBuf = ByteArrayOutputStream()
                while (true) {
                    val b = input.read()
                    if (b == -1) return
                    headerBuf.write(b)
                    val bytes = headerBuf.toByteArray()
                    if (bytes.size >= 4 && bytes[bytes.size - 4] == '\r'.code.toByte() &&
                        bytes[bytes.size - 3] == '\n'.code.toByte() &&
                        bytes[bytes.size - 2] == '\r'.code.toByte() &&
                        bytes[bytes.size - 1] == '\n'.code.toByte()
                    ) break
                }
                val headers = String(headerBuf.toByteArray(), Charsets.ISO_8859_1)
                val key = Regex("Sec-WebSocket-Key: (\\S+)").find(headers)?.groupValues?.get(1) ?: return
                val accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                        (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.ISO_8859_1)
                    )
                )
                output.write(
                    ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
                )
                output.flush()

                // Frame loop: reply to close, ignore everything else
                while (true) {
                    val h1 = input.read()
                    if (h1 == -1) break
                    val h2 = input.read()
                    if (h2 == -1) break
                    val opcode = h1 and 0x0F
                    var payloadLen = (h2 and 0x7F)
                    if (payloadLen == 126) {
                        payloadLen = (input.read() shl 8) or input.read()
                    } else if (payloadLen == 127) {
                        var len = 0L
                        repeat(8) { len = (len shl 8) or input.read().toLong() }
                        payloadLen = len.toInt()
                    }
                    val mask = if (h2 and 0x80 != 0) {
                        ByteArray(4).also { readFully(input, it) }
                        true
                    } else false
                    val payload = ByteArray(payloadLen)
                    readFully(input, payload)
                    when (opcode) {
                        0x8 -> {
                            // close: reply and terminate
                            output.write(byteArrayOf(0x88.toByte(), 0.toByte()))
                            output.flush()
                            break
                        }
                        0x9 -> {
                            // ping: pong
                            output.write(byteArrayOf(0x8A.toByte(), 0.toByte()))
                            output.flush()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { socket.close() }
                sockets.remove(socket)
            }
        }

        private fun readFully(input: InputStream, buf: ByteArray) {
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n == -1) break
                read += n
            }
        }

        fun stop() {
            running.set(false)
            runCatching { server.close() }
            sockets.forEach { runCatching { it.close() } }
        }
    }
}
