package com.hermes.satellite.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileManager {

    data class FileEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    /** Root directory for file browser */
    val rootPath: String get() = Environment.getExternalStorageDirectory().absolutePath

    /** List files in directory, sorted: folders first, then alphabetical */
    fun listFiles(dirPath: String): List<FileEntry> {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }
            ?.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    /** Check if we have MANAGE_EXTERNAL_STORAGE permission (Android 11+) */
    fun hasFullStorageAccess(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // pre-Android 11, WRITE_EXTERNAL_STORAGE manifest permission is enough
        }
    }

    /** Open settings page for granting full storage access */
    fun openStorageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Format file size to human-readable */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return ""
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /** Format date to readable string */
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** Get parent directory path, or null if at root */
    fun getParent(path: String): String? {
        val parent = File(path).parent
        return if (parent != null && parent.length >= rootPath.length) parent else null
    }
}
