/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.webaccess

import android.content.Context
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class WebAccessServer @Inject constructor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository
) {
    companion object {
        private const val DEFAULT_PORT = 8642
        private const val TOKEN_LENGTH = 32
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val MAX_HANDLER_THREADS = 4
    }

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var executor: ExecutorService? = null
    private val running = AtomicBoolean(false)

    var accessToken: String = ""
        private set
    var serverPort: Int = DEFAULT_PORT
        private set

    fun start(): Boolean {
        if (running.get()) return true

        accessToken = generateToken()
        return try {
            serverSocket = ServerSocket(DEFAULT_PORT)
            serverPort = serverSocket!!.localPort
            executor = Executors.newFixedThreadPool(MAX_HANDLER_THREADS) { runnable ->
                Thread(runnable).apply { isDaemon = true }
            }
            running.set(true)

            serverThread = Thread {
                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        client.soTimeout = SOCKET_TIMEOUT_MS
                        executor?.submit { handleClient(client) }
                    } catch (e: SocketTimeoutException) {
                        // Expected during shutdown
                    } catch (e: Exception) {
                        if (running.get()) {
                            Timber.e(e, "Error accepting connection")
                        }
                    }
                }
            }.apply { isDaemon = true; start() }

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start web server")
            false
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
            executor?.shutdownNow()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping server")
        }
        serverSocket = null
        serverThread = null
        executor = null
        accessToken = ""
    }

    fun isRunning(): Boolean = running.get()

    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..TOKEN_LENGTH).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendError(writer, 400, "Bad Request")
                return
            }

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).lowercase()] = line.substring(colonIdx + 1).trim()
                }
                line = reader.readLine()
            }

            // Route request
            when {
                path == "/" -> serveIndexHtml(writer)
                path == "/style.css" -> serveStyleCss(writer)
                path.startsWith("/api/") -> handleApi(writer, path, headers)
                else -> sendError(writer, 404, "Not Found")
            }
        } catch (e: SocketTimeoutException) {
            Timber.w("Client read timeout")
        } catch (e: Exception) {
            Timber.e(e, "Error handling client")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleApi(writer: PrintWriter, path: String, headers: Map<String, String>) {
        val token = headers["x-access-token"] ?: path.substringAfter("token=", "").substringBefore("&")
        if (token != accessToken) {
            sendJson(writer, 401, """{"error":"Unauthorized"}""")
            return
        }

        when {
            path.startsWith("/api/conversations") -> {
                val json = conversationsJson()
                sendJson(writer, 200, json)
            }
            path.startsWith("/api/messages/") -> {
                val threadId = path.removePrefix("/api/messages/")
                    .substringBefore("?")
                    .toLongOrNull()
                if (threadId == null) {
                    sendJson(writer, 400, """{"error":"Invalid thread ID"}""")
                } else {
                    val json = messagesJson(threadId)
                    sendJson(writer, 200, json)
                }
            }
            else -> sendJson(writer, 404, """{"error":"Not Found"}""")
        }
    }

    private fun conversationsJson(): String {
        val conversations = conversationRepo.getConversationsSnapshot(false)
            .filter { !it.archived && !it.blocked }
            .take(100)

        val array = JSONArray()
        for (conv in conversations) {
            val obj = JSONObject()
            obj.put("id", conv.id)
            obj.put("title", JSONObject.quote(conv.getTitle()).removeSurrounding("\""))
            obj.put("snippet", JSONObject.quote(conv.snippet).removeSurrounding("\""))
            obj.put("date", conv.date)
            obj.put("unread", conv.unread)
            obj.put("recipients", conv.recipients.size)
            array.put(obj)
        }
        return array.toString()
    }

    private fun messagesJson(threadId: Long): String {
        val conversation = conversationRepo.getConversation(threadId)
        if (conversation == null || conversation.archived || conversation.blocked) {
            return """{"messages":[],"title":""}"""
        }

        val messages = messageRepo.getMessagesSync(threadId)
        val array = JSONArray()
        for (msg in messages.take(200)) {
            val obj = JSONObject()
            obj.put("id", msg.id)
            obj.put("body", JSONObject.quote(msg.body).removeSurrounding("\""))
            obj.put("date", msg.date)
            obj.put("isMe", msg.isMe())
            obj.put("read", msg.read)
            array.put(obj)
        }

        val result = JSONObject()
        result.put("title", JSONObject.quote(conversation.getTitle()).removeSurrounding("\""))
        result.put("messages", array)
        return result.toString()
    }

    private fun sendJson(writer: PrintWriter, status: Int, json: String) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        writer.print("HTTP/1.1 $status $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(json)
        writer.flush()
    }

    private fun sendError(writer: PrintWriter, status: Int, message: String) {
        writer.print("HTTP/1.1 $status $message\r\n")
        writer.print("Content-Type: text/plain\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(message)
        writer.flush()
    }

    private fun serveIndexHtml(writer: PrintWriter) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>QUIK Messages</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
    <div id="app">
        <header>
            <h1>QUIK Messages</h1>
            <div id="auth-form">
                <input type="text" id="token-input" placeholder="Access Token">
                <button onclick="authenticate()">Connect</button>
            </div>
        </header>
        <main>
            <div id="conversations-panel">
                <h2>Conversations</h2>
                <ul id="conversations-list"></ul>
            </div>
            <div id="messages-panel">
                <h2 id="thread-title">Select a conversation</h2>
                <div id="messages-list"></div>
            </div>
        </main>
    </div>
    <script>
        let token = '';
        
        function authenticate() {
            token = document.getElementById('token-input').value;
            if (token) {
                loadConversations();
                document.getElementById('auth-form').style.display = 'none';
            }
        }
        
        async function loadConversations() {
            try {
                const res = await fetch('/api/conversations?token=' + token, {
                    headers: { 'X-Access-Token': token }
                });
                if (!res.ok) throw new Error('Unauthorized');
                const data = await res.json();
                renderConversations(data);
            } catch (e) {
                alert('Failed to load: ' + e.message);
            }
        }
        
        function renderConversations(conversations) {
            const list = document.getElementById('conversations-list');
            list.innerHTML = conversations.map(c => 
                '<li class="' + (c.unread ? 'unread' : '') + '" onclick="loadMessages(' + c.id + ')">' +
                '<strong>' + escapeHtml(c.title) + '</strong>' +
                '<span class="snippet">' + escapeHtml(c.snippet) + '</span>' +
                '<span class="date">' + formatDate(c.date) + '</span></li>'
            ).join('');
        }
        
        async function loadMessages(threadId) {
            try {
                const res = await fetch('/api/messages/' + threadId + '?token=' + token, {
                    headers: { 'X-Access-Token': token }
                });
                if (!res.ok) throw new Error('Failed');
                const data = await res.json();
                renderMessages(data);
            } catch (e) {
                alert('Failed to load messages');
            }
        }
        
        function renderMessages(data) {
            document.getElementById('thread-title').textContent = data.title || 'Messages';
            const list = document.getElementById('messages-list');
            list.innerHTML = data.messages.map(m =>
                '<div class="message ' + (m.isMe ? 'sent' : 'received') + '">' +
                '<p>' + escapeHtml(m.body) + '</p>' +
                '<span class="time">' + formatDate(m.date) + '</span></div>'
            ).join('');
            list.scrollTop = list.scrollHeight;
        }
        
        function formatDate(ts) {
            return new Date(ts).toLocaleString();
        }
        
        function escapeHtml(str) {
            const div = document.createElement('div');
            div.textContent = str;
            return div.innerHTML;
        }
    </script>
</body>
</html>
        """.trimIndent()

        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(html)
        writer.flush()
    }

    private fun serveStyleCss(writer: PrintWriter) {
        val css = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }
#app { max-width: 1200px; margin: 0 auto; padding: 16px; }
header { background: #2196F3; color: white; padding: 16px; border-radius: 8px; margin-bottom: 16px; }
header h1 { font-size: 1.5rem; margin-bottom: 8px; }
#auth-form { display: flex; gap: 8px; }
#auth-form input { flex: 1; padding: 8px; border: none; border-radius: 4px; }
#auth-form button { padding: 8px 16px; background: white; color: #2196F3; border: none; border-radius: 4px; cursor: pointer; }
main { display: grid; grid-template-columns: 300px 1fr; gap: 16px; height: calc(100vh - 150px); }
#conversations-panel, #messages-panel { background: white; border-radius: 8px; overflow: hidden; display: flex; flex-direction: column; }
#conversations-panel h2, #messages-panel h2 { padding: 12px 16px; background: #f0f0f0; font-size: 1rem; }
#conversations-list { list-style: none; overflow-y: auto; flex: 1; }
#conversations-list li { padding: 12px 16px; border-bottom: 1px solid #eee; cursor: pointer; }
#conversations-list li:hover { background: #f9f9f9; }
#conversations-list li.unread { font-weight: bold; }
#conversations-list li strong { display: block; margin-bottom: 4px; }
#conversations-list .snippet { color: #666; font-size: 0.9rem; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
#conversations-list .date { color: #999; font-size: 0.8rem; }
#messages-list { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
.message { max-width: 70%; padding: 10px 14px; border-radius: 16px; }
.message.sent { align-self: flex-end; background: #2196F3; color: white; }
.message.received { align-self: flex-start; background: #e0e0e0; }
.message p { margin-bottom: 4px; word-wrap: break-word; }
.message .time { font-size: 0.7rem; opacity: 0.7; }
@media (max-width: 768px) { main { grid-template-columns: 1fr; } #conversations-panel { max-height: 40vh; } }
        """.trimIndent()

        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/css; charset=utf-8\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(css)
        writer.flush()
    }
}
