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
    private var reconnectHandler: Handler? = null

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError

    enum class State { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, RECONNECTING, ERROR }

    fun connect(serverUrl: String, pairingCode: String, userId: String = "ximalu") {
        // Cancel any pending reconnect
        reconnectHandler?.removeCallbacksAndMessages(null)
        reconnectHandler = null

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

        val wsUrl = if (serverUrl.trim().startsWith("ws://") || serverUrl.trim().startsWith("wss://"))
            serverUrl.trim()
        else
            "ws://${serverUrl.trim()}/satellite"

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
                            _lastError.value = ""
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
                val msg = t.message ?: ""
                Log.e(TAG, "Connection failed: $msg")
                _lastError.value = msg

                // "Software caused connection abort" = app backgrounded, reconnect silently
                // "Socket closed" / "reset" = expected on network switch, reconnect silently
                val isExpectedAbort = msg.contains("abort", ignoreCase = true) ||
                        msg.contains("reset", ignoreCase = true) ||
                        msg.contains("closed", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("refused", ignoreCase = true)

                if (shouldReconnect && savedServerUrl.isNotEmpty()) {
                    // Go to RECONNECTING (no red ERROR flash for expected disconnects)
                    _connectionState.value = if (isExpectedAbort) State.RECONNECTING else State.ERROR
                    Log.d(TAG, "Reconnecting in 2s...")
                    scheduleReconnect(2000)
                } else {
                    _connectionState.value = State.ERROR
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closed: $code $reason")
                if (shouldReconnect && code != 1000 && code != 1001) {
                    _connectionState.value = State.RECONNECTING
                    Log.d(TAG, "Reconnecting in 2s (close code=$code)")
                    scheduleReconnect(2000)
                } else {
                    _connectionState.value = State.DISCONNECTED
                }
            }
        })
    }

    private fun scheduleReconnect(delayMs: Long) {
        reconnectHandler?.removeCallbacksAndMessages(null)
        reconnectHandler = Handler(Looper.getMainLooper())
        reconnectHandler?.postDelayed({
            reconnectHandler = null
            if (shouldReconnect && savedServerUrl.isNotEmpty()) {
                Log.d(TAG, "Auto-reconnecting...")
                connect(savedServerUrl, savedPairingCode, userId)
            }
        }, delayMs)
    }

    fun send(text: String): Boolean {
        if (_connectionState.value != State.CONNECTED) return false
        val safeText = text.replace("\\", "\\\\").replace("\"", "\\\"")
        val msg = """{"type":"chat","text":"$safeText"}"""
        return webSocket?.send(msg) ?: false
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectHandler?.removeCallbacksAndMessages(null)
        reconnectHandler = null
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = State.DISCONNECTED
    }

    companion object {
        private const val TAG = "Satellite.WS"
    }
}
