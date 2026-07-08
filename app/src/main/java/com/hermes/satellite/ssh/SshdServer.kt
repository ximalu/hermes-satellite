package com.hermes.satellite.ssh

import android.content.Context
import android.util.Log
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicBoolean

object SshdServer {

    private const val TAG = "Satellite.SSHD"
    private const val SSHD_PORT = 2222
    private const val HOST_KEY_FILE = "ssh_host_key"

    private var server: SshServer? = null
    private var password: String = ""

    val isRunning: Boolean get() = server?.isStarted ?: false

    /** Start the local SSH server */
    fun start(context: Context, authPassword: String): Boolean {
        if (isRunning) return true

        return try {
            password = authPassword
            val hostKeyPath = File(context.filesDir, HOST_KEY_FILE).absolutePath

            server = SshServer.setUpDefaultServer().apply {
                host = "127.0.0.1"
                port = SSHD_PORT

                // Host key (generated on first run, persisted)
                keyPairProvider = SimpleGeneratorHostKeyProvider(File(hostKeyPath).toPath())

                // Password authentication
                passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                    username == "ximalu" && password == authPassword
                }

                // Shell: launch BusyBox sh, fall back to /system/bin/sh
                shellFactory = ProcessShellFactory(
                    BusyBoxInstaller.getShellPath(context),
                    *BusyBoxInstaller.getShellArgs(context)
                )
            }
            server?.start()
            Log.i(TAG, "SSHD started on 127.0.0.1:$SSHD_PORT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SSHD: ${e.message}")
            stop()
            false
        }
    }

    fun stop() {
        try {
            server?.stop(true)
            server?.close(true)
        } catch (_: Exception) { }
        server = null
        Log.i(TAG, "SSHD stopped")
    }
}
