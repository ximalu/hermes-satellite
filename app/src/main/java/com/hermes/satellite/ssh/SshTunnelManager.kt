package com.hermes.satellite.ssh

import android.content.Context
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object SshTunnelManager {

    private const val TAG = "Satellite.Tunnel"
    private const val SSH_KEY_FILE = "ssh_client_key"
    private const val REMOTE_PORT = 2222 // port on the server
    private const val LOCAL_PORT = 2222 // port on the phone (SSHD)

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private var session: Session? = null
    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state

    fun getState(): State = _state.value

    /** Save SSH private key from WebSocket and establish tunnel */
    fun configureAndConnect(context: Context, config: SshConfig) {
        try {
            _state.value = State.CONNECTING

            // Save private key to private storage
            val keyFile = File(context.filesDir, SSH_KEY_FILE)
            keyFile.writeText(config.privateKey.trim())
            keyFile.setReadable(true, true) // owner read only

            // Start SSHD first if not running
            val sshdPassword = generatePassword()
            if (!SshdServer.isRunning) {
                SshdServer.start(context, sshdPassword)
            }

            // Connect with JSch
            val jsch = JSch()
            jsch.addIdentity(keyFile.absolutePath)
            // Skip host key checking for now (server identity is trusted via WebSocket)
            java.util.Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("ServerAliveInterval", "30")
                put("ServerAliveCountMax", "3")
            }.also { props ->
                session = jsch.getSession(config.user, config.host, config.port).apply {
                    setConfig(props)
                    connect(15000) // 15s timeout
                    // Set up reverse tunnel: -R 2222:localhost:2222
                    setPortForwardingR(REMOTE_PORT, "127.0.0.1", LOCAL_PORT)
                }
            }

            _state.value = State.CONNECTED
            Log.i(TAG, "Tunnel established: -R $REMOTE_PORT:localhost:$LOCAL_PORT")

        } catch (e: Exception) {
            Log.e(TAG, "Tunnel failed: ${e.message}")
            _state.value = State.ERROR
        }
    }

    fun disconnect() {
        try {
            session?.disconnect()
        } catch (_: Exception) { }
        session = null
        _state.value = State.DISCONNECTED
        Log.i(TAG, "Tunnel disconnected")
    }

    private fun generatePassword(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
