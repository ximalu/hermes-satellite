package com.hermes.satellite.ui

import android.content.Context
import android.content.Intent

object CrashLogger {

    private val logLines = mutableListOf<String>()
    private const val MAX_LINES = 2000

    /** Add a log line (called from try-catch blocks) */
    fun log(tag: String, message: String) {
        val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "[$ts] [$tag] $message"
        synchronized(logLines) {
            logLines.add(line)
            if (logLines.size > MAX_LINES) {
                logLines.removeAt(0)
            }
        }
    }

    /** Capture recent logcat output */
    fun captureLogcat(): List<String> {
        val result = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec("logcat -d -t 500 HermesSatellite:V Satellite:V *:S")
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { result.add(it) }
            }
            process.waitFor()
        } catch (_: Exception) { }
        return result
    }

    /** Get combined log text: in-memory logs + logcat */
    fun getFullLog(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Hermes Satellite Logs ===")
        sb.appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine()

        sb.appendLine("--- In-Memory Logs ---")
        synchronized(logLines) {
            if (logLines.isEmpty()) {
                sb.appendLine("(no in-memory logs)")
            } else {
                logLines.forEach { sb.appendLine(it) }
            }
        }

        sb.appendLine()
        sb.appendLine("--- Logcat (last 500 lines) ---")
        try {
            val logcat = captureLogcat()
            if (logcat.isEmpty()) {
                sb.appendLine("(no logcat output)")
            } else {
                logcat.forEach { sb.appendLine(it) }
            }
        } catch (e: Exception) {
            sb.appendLine("(logcat error: ${e.message})")
        }

        return sb.toString()
    }

    /** Share logs as text file */
    fun share(context: Context) {
        try {
            val logText = getFullLog()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logText)
            }
            context.startActivity(Intent.createChooser(intent, "分享日志"))
        } catch (_: Exception) { }
    }

    fun clear() {
        synchronized(logLines) { logLines.clear() }
    }
}
