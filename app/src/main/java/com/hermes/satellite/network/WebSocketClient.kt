package com.hermes.satellite.network

import android.util.Log
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class SatelliteWebSocket {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // No read timeout for WebSocket
        .writeTimeout(0, TimeUnit.SECONDS) // No write timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""
    private var pairingCode: String = ""
    private var userId: String = "ximalu"
    private var shouldReconnect: Boolean = false
    private var savedServerUrl: String = ""
    private var savedPairingCode: String = ""

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError

    enum class State { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, ERROR }

    fun connect(serverUrl: String, pairingCode: String, userId: String = "ximalu") {
        // Clean up existing connection
        webSocket?.close(1000, "New connection requested")
        webSocket = null
        _connectionState.value = State.CONNECTING
        _messages.value = emptyList()
        _lastError.value = ""
        shouldReconnect = true

        this.serverUrl = serverUrl
        this.pairingCode = pairingCode
        this.userId = userId
        savedServerUrl = serverUrl
        savedPairingCode = pairingCode

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
                val authMsg = """{"type":"auth","pairing_code":"$pairingCode","user_id":"$userId"}"""
                val sent = webSocket.send(authMsg)
                Log.d(TAG, "Auth message sent: $sent")
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
                            val err = json.optString("text", "认证失败")
                            _lastError.value = err
                            Log.e(TAG, "Auth error: $err")
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
                _lastError.value = t.message ?: "未知错误"
                _connectionState.value = State.ERROR
                // Auto reconnect in 5 seconds if we're supposed to
                if (shouldReconnect && savedServerUrl.isNotEmpty()) {
                    Log.d(TAG, "Scheduling reconnect in 5s")
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        if (shouldReconnect && _connectionState.value == State.ERROR) {
                            Log.d(TAG, "Auto-reconnecting...")
                            connect(savedServerUrl, savedPairingCode, userId)
                        }
                    }, 5000)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closed: $code $reason")
                _connectionState.value = State.DISCONNECTED
                // Auto reconnect in 5 seconds for unexpected closes
                if (shouldReconnect && code != 1000 && code != 1001) {
                    Log.d(TAG, "Scheduling reconnect in 5s (close code=$code)")
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        if (shouldReconnect && _connectionState.value == State.DISCONNECTED) {
                            Log.d(TAG, "Auto-reconnecting after close...")
                            connect(savedServerUrl, savedPairingCode, userId)
                        }
                    }, 5000)
                }
            }
        })
    }

    fun send(text: String): Boolean {
        if (_connectionState.value != State.CONNECTED) return false
        val safeText = text.replace("\\", "\\\\").replace("\"", "\\\"")
        val msg = """{"type":"chat","text":"$safeText"}"""
        return webSocket?.send(msg) ?: false
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = State.DISCONNECTED
    }

    companion object {
        private const val TAG = "Satellite.WS"
    }
}
