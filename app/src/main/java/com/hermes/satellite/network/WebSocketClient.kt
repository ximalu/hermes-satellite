package com.hermes.satellite.network

import android.util.Log
import android.os.Handler
import android.os.Looper
import com.hermes.satellite.ssh.SshConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class SatelliteWebSocket {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
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

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError

    enum class State { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, RECONNECTING, ERROR }

    fun connect(serverUrl: String, pairingCode: String, userId: String = "ximalu") {
        reconnectHandler?.removeCallbacksAndMessages(null)
        reconnectHandler = null

        // Close old connection without clearing history
        webSocket?.close(1000, "New connection requested")
        webSocket = null
        _connectionState.value = State.CONNECTING
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
                webSocket.send(authMsg)
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
                            // Notify listeners that we're connected (for history reload)
                            onConnectedCallback?.invoke()
                        }
                        "auth_error" -> {
                            _connectionState.value = State.ERROR
                            _lastError.value = json.optString("text", "认证失败")
                        }
                        "chat" -> {
                            val msg = json.optString("text", "")
                            if (msg.isNotEmpty()) {
                                onServerMessage?.invoke(msg)
                            }
                        }
                        "pong" -> { /* heartbeat OK */ }
                        "ssh_config" -> {
                            val host = json.optString("host", "")
                            val port = json.optInt("port", 22)
                            val user = json.optString("user", "ximalu")
                            val key = json.optString("private_key", "")
                            if (host.isNotEmpty() && key.isNotEmpty()) {
                                val config = SshConfig(host, port, user, key)
                                onSshConfig?.invoke(config)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: ""
                Log.e(TAG, "Connection failed: $msg")
                _lastError.value = msg

                val isExpectedAbort = msg.contains("abort", ignoreCase = true) ||
                        msg.contains("reset", ignoreCase = true) ||
                        msg.contains("closed", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("refused", ignoreCase = true)

                if (shouldReconnect && savedServerUrl.isNotEmpty()) {
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

    // Callbacks for ChatScreen integration
    var onServerMessage: ((String) -> Unit)? = null
    var onConnectedCallback: (() -> Unit)? = null

    // Callback for SSH config from server
    var onSshConfig: ((SshConfig) -> Unit)? = null

    companion object {
        private const val TAG = "Satellite.WS"
    }
}
