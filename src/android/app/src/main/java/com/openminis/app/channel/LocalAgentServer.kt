package com.openminis.app.channel

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Tiny HTTP listener so a computer on the same device (127.0.0.1) can
 * prompt Minis: POST /v1/prompt  Authorization: Bearer <pairing>
 * {"text":"..."}
 *
 * Binds loopback only unless LAN bind is explicitly enabled.
 */
class LocalAgentServer(
    private val prefs: ChannelPrefs,
    private val runPrompt: suspend (text: String) -> String,
) {
    private var job: Job? = null
    private var server: ServerSocket? = null

    fun isRunning(): Boolean = server?.isClosed == false

    fun start(scope: CoroutineScope, bindLan: Boolean, port: Int = PORT) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            val addr = if (bindLan) null else InetAddress.getByName("127.0.0.1")
            val ss = ServerSocket(port, 8, addr)
            server = ss
            Log.i(TAG, "listening on ${ss.inetAddress.hostAddress}:$port")
            while (!ss.isClosed) {
                val socket = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                launch { handle(socket) }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        job?.cancel()
        job = null
    }

    private suspend fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        socket.use { s ->
            val parsed = readHttp(s.getInputStream()) ?: return
            val (method, path, headers, body) = parsed
            val (code, payload) = dispatch(method, path, headers, body)
            write(s.getOutputStream(), code, payload)
        }
    }

    internal suspend fun dispatch(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String,
    ): Pair<Int, String> {
        val authorized = LocalAgentAuth.authorized(headers["authorization"], prefs.pairingCode())
        if (method == "GET" && path.startsWith("/health")) {
            // Probe is fine on loopback; on a shared port still require the pairing code
            // so LAN bind cannot be used as an unauthenticated existence oracle.
            return if (authorized || prefs.pairingCode().isBlank()) {
                200 to """{"ok":true}"""
            } else {
                401 to """{"error":"unauthorized"}"""
            }
        }
        if (method != "POST" || path != "/v1/prompt") {
            return 404 to """{"error":"not_found"}"""
        }
        if (!authorized) {
            return 401 to """{"error":"unauthorized"}"""
        }
        val text = try {
            JSONObject(body).optString("text").trim()
        } catch (_: Exception) {
            ""
        }
        if (text.isEmpty()) return 400 to """{"error":"text_required"}"""
        val reply = try {
            runPrompt(text)
        } catch (t: Throwable) {
            return 500 to """{"error":"${t.javaClass.simpleName}"}"""
        }
        return 200 to JSONObject().put("reply", reply).toString()
    }

    private fun write(out: OutputStream, code: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val TAG = "LocalAgentServer"
        const val PORT = 18789

        internal fun readHttp(ins: InputStream): HttpRequest? {
            val raw = ByteArrayOutputStream()
            val tmp = ByteArray(4096)
            while (raw.size() < 256_000) {
                val n = ins.read(tmp)
                if (n < 0) break
                raw.write(tmp, 0, n)
                val bytes = raw.toByteArray()
                val sep = indexOfHeaderEnd(bytes) ?: continue
                val headerText = String(bytes, 0, sep, StandardCharsets.ISO_8859_1)
                val lines = headerText.split("\r\n")
                val req = lines.firstOrNull()?.split(' ') ?: return null
                val method = req.getOrElse(0) { "" }
                val path = req.getOrElse(1) { "" }
                val headers = mutableMapOf<String, String>()
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] =
                            line.substring(idx + 1).trim()
                    }
                }
                val declared = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 64_000)
                val bodyStart = sep + 4
                val bodyOut = ByteArrayOutputStream()
                if (bytes.size > bodyStart) {
                    val already = (bytes.size - bodyStart).coerceAtMost(declared)
                    bodyOut.write(bytes, bodyStart, already)
                }
                while (bodyOut.size() < declared) {
                    val m = ins.read(tmp, 0, (declared - bodyOut.size()).coerceAtMost(tmp.size))
                    if (m < 0) break
                    bodyOut.write(tmp, 0, m)
                }
                val body = String(bodyOut.toByteArray(), StandardCharsets.UTF_8)
                return HttpRequest(method, path, headers, body)
            }
            return null
        }

        private fun indexOfHeaderEnd(bytes: ByteArray): Int? {
            var i = 0
            while (i + 3 < bytes.size) {
                if (bytes[i] == '\r'.code.toByte() &&
                    bytes[i + 1] == '\n'.code.toByte() &&
                    bytes[i + 2] == '\r'.code.toByte() &&
                    bytes[i + 3] == '\n'.code.toByte()
                ) {
                    return i
                }
                i++
            }
            return null
        }
    }
}

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

object LocalAgentAuth {
    fun authorized(header: String?, expected: String): Boolean {
        if (expected.isBlank()) return false
        val raw = header.orEmpty().trim()
        val token = when {
            raw.startsWith("Bearer ", ignoreCase = true) -> raw.substring(7).trim()
            raw.startsWith("Bearer", ignoreCase = true) -> raw.substring(6).trim()
            else -> raw
        }
        return token.isNotEmpty() && token == expected
    }
}
