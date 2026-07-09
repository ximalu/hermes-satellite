package com.hermes.satellite.ui

import android.content.Context
import android.content.Intent
import java.io.File

object CrashLogger {

    private val logLines = mutableListOf<String>()
    private const val MAX_LINES = 2000
    private const val FILE_NAME = "crash_log.txt"

    /** Log file path — set via init() before any logging */
    private var fileLogPath: String? = null

    /** Initialize with Context (call from Application.onCreate) */
    fun init(context: Context) {
        val dir = context.filesDir
        dir.mkdirs()
        fileLogPath = File(dir, FILE_NAME).absolutePath
        // Trim file if it's gotten huge
        try {
            val f = File(fileLogPath!!)
            if (f.exists() && f.length() > 500_000) {
                f.delete()
            }
        } catch (_: Exception) {}
    }

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
        // Persist to file
        fileLogPath?.let { path ->
            try {
                File(path).appendText(line + "\n")
            } catch (_: Exception) {}
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

    /** Get combined log text: in-memory logs + file logs + logcat */
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

        // File-persisted logs (survive crash/restart)
        sb.appendLine()
        sb.appendLine("--- Persistent Log File ---")
        try {
            fileLogPath?.let { path ->
                val f = File(path)
                if (f.exists()) {
                    val lines = f.readLines()
                    if (lines.isEmpty()) {
                        sb.appendLine("(empty)")
                    } else {
                        lines.takeLast(200).forEach { sb.appendLine(it) }
                    }
                } else {
                    sb.appendLine("(no persistent log file)")
                }
            } ?: sb.appendLine("(logger not initialized)")
        } catch (e: Exception) {
            sb.appendLine("(read error: ${e.message})")
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
