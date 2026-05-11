package dev.octoshrimpy.quik.feature.webaccess

import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import io.realm.Realm
import io.realm.Sort
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object WebAccessServer {
    private const val DEFAULT_PORT = 8090
    private const val MAX_MESSAGES = 500

    private val running = AtomicBoolean(false)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var worker: Thread? = null
    @Volatile var token: String = ""
        private set
    @Volatile var port: Int = DEFAULT_PORT
        private set

    val isRunning: Boolean get() = running.get()
    val localUrl: String get() = "http://${getLocalIpAddress()}:$port/?token=$token"

    fun start() {
        if (!running.compareAndSet(false, true)) return

        token = UUID.randomUUID().toString().replace("-", "").take(16)
        worker = Thread {
            try {
                serverSocket = try {
                    ServerSocket(DEFAULT_PORT)
                } catch (_: Exception) {
                    ServerSocket(0)
                }
                port = serverSocket?.localPort ?: DEFAULT_PORT

                while (running.get()) {
                    val client = serverSocket?.accept() ?: break
                    Thread { handleClient(client) }.start()
                }
            } catch (_: Exception) {
                running.set(false)
            } finally {
                serverSocket?.close()
                serverSocket = null
            }
        }.apply {
            name = "quik-web-access"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        serverSocket?.close()
        serverSocket = null
        worker = null
        token = ""
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))
            val requestLine = reader.readLine().orEmpty()
            while (!reader.readLine().isNullOrEmpty()) {
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                writer.writeResponse(405, "text/plain", "Only GET is supported")
                return
            }

            val request = parseRequest(parts[1])
            val body = when {
                request.path == "/" -> webAppHtml(request.query["token"].orEmpty())
                request.query["token"] != token -> return writer.writeResponse(403, "application/json", """{"error":"Forbidden"}""")
                request.path == "/api/conversations" -> conversationsJson()
                request.path.startsWith("/api/conversations/") && request.path.endsWith("/messages") -> {
                    val threadId = request.path
                        .removePrefix("/api/conversations/")
                        .removeSuffix("/messages")
                        .toLongOrNull()
                    if (threadId == null) """{"error":"Invalid conversation"}""" else messagesJson(threadId)
                }
                else -> return writer.writeResponse(404, "application/json", """{"error":"Not found"}""")
            }

            writer.writeResponse(200, if (request.path == "/") "text/html; charset=utf-8" else "application/json", body)
        }
    }

    private fun conversationsJson(): String = Realm.getDefaultInstance().use { realm ->
        val conversations = realm.where(Conversation::class.java)
            .notEqualTo("id", 0L)
            .equalTo("archived", false)
            .equalTo("blocked", false)
            .isNotNull("lastMessage")
            .sort("lastMessage.date", Sort.DESCENDING)
            .findAll()

        conversations.joinToString(prefix = "[", postfix = "]") { conversation ->
            """{"id":${conversation.id},"title":"${json(conversation.getTitle())}","snippet":"${json(conversation.snippet.orEmpty())}","date":${conversation.date},"unread":${conversation.unread},"me":${conversation.me}}"""
        }
    }

    private fun messagesJson(threadId: Long): String = Realm.getDefaultInstance().use { realm ->
        val conversation = realm.where(Conversation::class.java)
            .equalTo("id", threadId)
            .findFirst()
        val messages = realm.where(Message::class.java)
            .equalTo("threadId", threadId)
            .equalTo("isEmojiReaction", false)
            .sort("date", Sort.DESCENDING)
            .limit(MAX_MESSAGES.toLong())
            .findAll()
            .sort("date", Sort.ASCENDING)

        val messagesJson = messages.joinToString(prefix = "[", postfix = "]") { message ->
            """{"id":${message.id},"date":${message.date},"fromMe":${message.isMe()},"address":"${json(message.address)}","text":"${json(message.getText())}"}"""
        }
        """{"id":$threadId,"title":"${json(conversation?.getTitle().orEmpty())}","messages":$messagesJson}"""
    }

    private fun parseRequest(target: String): Request {
        val rawPath = target.substringBefore("?")
        val query = target.substringAfter("?", "")
            .split("&")
            .filter { it.contains("=") }
            .associate {
                val key = decode(it.substringBefore("="))
                val value = decode(it.substringAfter("="))
                key to value
            }

        return Request(rawPath, query)
    }

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    private fun BufferedWriter.writeResponse(status: Int, contentType: String, body: String) {
        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        write("HTTP/1.1 $status $reason\r\n")
        write("Content-Type: $contentType\r\n")
        write("Content-Length: ${body.toByteArray().size}\r\n")
        write("Connection: close\r\n")
        write("\r\n")
        write(body)
        flush()
    }

    private fun webAppHtml(initialToken: String): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Quik Web Access</title>
          <style>
            body{margin:0;font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#f7f7f7;color:#111}
            header{padding:16px 20px;background:#202124;color:white}
            main{display:grid;grid-template-columns:320px 1fr;height:calc(100vh - 57px)}
            #threads{overflow:auto;border-right:1px solid #ddd;background:white}
            button{display:block;width:100%;padding:12px 16px;border:0;border-bottom:1px solid #eee;background:white;text-align:left}
            button:hover{background:#f1f3f4}
            .snippet{color:#666;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            #messages{overflow:auto;padding:16px}
            .msg{max-width:720px;margin:0 0 12px;padding:10px 12px;border-radius:8px;background:white}
            .me{margin-left:auto;background:#e8f0fe}
            .meta{font-size:12px;color:#666;margin-bottom:4px}
            input{padding:8px;margin-left:12px}
            @media(max-width:700px){main{grid-template-columns:1fr;height:auto}#threads{max-height:40vh;border-right:0;border-bottom:1px solid #ddd}}
          </style>
        </head>
        <body>
          <header>Quik Web Access <input id="token" placeholder="token" value="${html(initialToken)}"></header>
          <main><section id="threads"></section><section id="messages"></section></main>
          <script>
            const token = document.getElementById('token');
            const threads = document.getElementById('threads');
            const messages = document.getElementById('messages');
            const fmt = ms => new Date(ms).toLocaleString();
            async function api(path){ const r = await fetch(path + (path.includes('?')?'&':'?') + 'token=' + encodeURIComponent(token.value)); if(!r.ok) throw new Error(await r.text()); return r.json(); }
            async function loadThreads(){
              const rows = await api('/api/conversations');
              threads.innerHTML = rows.map(c => '<button onclick="loadMessages('+c.id+')"><strong>'+esc(c.title)+'</strong><div class="snippet">'+esc(c.snippet)+'</div></button>').join('');
            }
            async function loadMessages(id){
              const data = await api('/api/conversations/' + id + '/messages');
              messages.innerHTML = '<h2>'+esc(data.title)+'</h2>' + data.messages.map(m => '<div class="msg '+(m.fromMe?'me':'')+'"><div class="meta">'+fmt(m.date)+' '+esc(m.address)+'</div><div>'+esc(m.text).replace(/\n/g,'<br>')+'</div></div>').join('');
            }
            function esc(s){return String(s ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));}
            loadThreads().catch(e => threads.textContent = e.message);
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun getLocalIpAddress(): String {
        NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
            if (!networkInterface.isUp || networkInterface.isLoopback) return@forEach
            networkInterface.inetAddresses.toList().forEach { address ->
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress ?: "127.0.0.1"
                }
            }
        }
        return "127.0.0.1"
    }

    private fun json(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun html(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private data class Request(val path: String, val query: Map<String, String>)
}
