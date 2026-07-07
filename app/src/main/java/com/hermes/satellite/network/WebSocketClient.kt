package com.hermes.satellite.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*

class SatelliteWebSocket {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""
    private var pairingCode: String = ""
    private var userId: String = "ximalu"

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    enum class State { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, ERROR }

    fun connect(serverUrl: String, pairingCode: String, userId: String = "ximalu") {
        if (_connectionState.value == State.CONNECTED) return

        this.serverUrl = serverUrl
        this.pairingCode = pairingCode
        this.userId = userId
        _connectionState.value = State.CONNECTING
        _messages.value = emptyList()

        val wsUrl = if (serverUrl.startsWith("ws://") || serverUrl.startsWith("wss://"))
            serverUrl
        else
            "ws://$serverUrl/satellite"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d(TAG, "Connecting to $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, sending auth")
                _connectionState.value = State.AUTHENTICATING
                // Send authentication
                val authMsg = """{"type":"auth","pairing_code":"$pairingCode","user_id":"$userId"}"""
                webSocket.send(authMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: ${text.take(100)}")
                try {
                    val json = org.json.JSONObject(text)
                    when (json.optString("type")) {
                        "auth_ok" -> {
                            _connectionState.value = State.CONNECTED
                            Log.d(TAG, "Authenticated as ${json.optString("user_id")}")
                        }
                        "auth_error" -> {
                            _connectionState.value = State.ERROR
                            Log.e(TAG, "Auth error: ${json.optString("text")}")
                        }
                        "chat" -> {
                            val msg = json.optString("text", "")
                            if (msg.isNotEmpty()) {
                                _messages.value = _messages.value + msg
                            }
                        }
                        "pong" -> { /* heartbeat OK */ }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}")
                _connectionState.value = State.ERROR
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closed: $code $reason")
                _connectionState.value = State.DISCONNECTED
            }
        })
    }

    fun send(text: String): Boolean {
        if (_connectionState.value != State.CONNECTED) return false
        val msg = """{"type":"chat","text":"${text.replace("\"", "\\\"")}"}"""
        return webSocket?.send(msg) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = State.DISCONNECTED
    }

    companion object {
        private const val TAG = "Satellite.WS"
    }
}
