package com.hermes.satellite

import android.app.Application
import android.content.Context
import com.hermes.satellite.network.SatelliteWebSocket
import com.hermes.satellite.service.SatelliteService
import com.hermes.satellite.ssh.BusyBoxInstaller
import com.hermes.satellite.ssh.SshTunnelManager
import com.hermes.satellite.ui.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SatelliteApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize persistent logger (must be before crash handler)
        CrashLogger.init(this)

        // Capture uncaught crashes to in-memory logs
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.log("CRASH", "Thread: ${thread.name}")
            CrashLogger.log("CRASH", "${throwable.javaClass.simpleName}: ${throwable.message}")
            throwable.stackTrace?.take(20)?.forEach {
                CrashLogger.log("CRASH", "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
            }
            // Re-throw to let Android handle it (force close dialog)
            throwable.printStackTrace()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        SatelliteService.start(this)

        // Install BusyBox on background thread
        CoroutineScope(Dispatchers.IO).launch {
            val installed = BusyBoxInstaller.install(this@SatelliteApp)
            CrashLogger.log("Satellite", "BusyBox installed: $installed")

            // Auto-reconnect if saved settings exist
            val prefs = this@SatelliteApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val savedUrl = prefs.getString("server_url", "") ?: ""
            val savedCode = prefs.getString("pairing_code", "") ?: ""
            if (savedUrl.isNotBlank() && savedCode.isNotBlank()) {
                CrashLogger.log("Satellite", "Auto-connecting to $savedUrl")
                ws.connect(savedUrl, savedCode)
            }
        }

        // When server sends SSH config → establish reverse tunnel
        ws.onSshConfig = { config ->
            CrashLogger.log("Satellite", "SSH config received: ${config.host}:${config.port}")
            CoroutineScope(Dispatchers.IO).launch {
                SshTunnelManager.configureAndConnect(this@SatelliteApp, config)
            }
        }
    }

    companion object {
        val ws = SatelliteWebSocket()
    }
}
