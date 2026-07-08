package com.hermes.satellite

import android.app.Application
import com.hermes.satellite.network.SatelliteWebSocket
import com.hermes.satellite.service.SatelliteService
import com.hermes.satellite.ui.CrashLogger

class SatelliteApp : Application() {
    override fun onCreate() {
        super.onCreate()

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
    }

    companion object {
        val ws = SatelliteWebSocket()
    }
}
