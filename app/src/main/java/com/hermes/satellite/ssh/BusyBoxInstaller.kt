package com.hermes.satellite.ssh

import android.content.Context
import java.io.File

object BusyBoxInstaller {

    private const val ASSET_NAME = "busybox"
    private const val BIN_DIR = "bin"
    private const val BUSYBOX_NAME = "busybox"

    /** Path to the installed BusyBox binary */
    fun getBusyBoxPath(context: Context): String {
        return File(context.filesDir, "$BIN_DIR/$BUSYBOX_NAME").absolutePath
    }

    /** Path to the shell to use (BusyBox sh or fallback) */
    fun getShellPath(context: Context): String {
        val bb = getBusyBoxPath(context)
        if (File(bb).exists()) return bb
        return "/system/bin/sh"
    }

    /** Get arguments for the shell command. With BusyBox: "sh", without: "-" */
    fun getShellArgs(context: Context): Array<String> {
        return if (File(getBusyBoxPath(context)).exists()) {
            arrayOf("sh")
        } else {
            arrayOf("-") // /system/bin/sh with no login
        }
    }

    /** Install BusyBox from APK assets to app private directory */
    fun install(context: Context): Boolean {
        return try {
            val binDir = File(context.filesDir, BIN_DIR)
            binDir.mkdirs()

            val dest = File(binDir, BUSYBOX_NAME)
            // If already installed and same size, skip
            if (dest.exists() && dest.length() > 100000) return true

            context.assets.open(ASSET_NAME).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setExecutable(true)

            // Symlink sh → busybox (BusyBox handles multi-call via argv[0])
            val shLink = File(binDir, "sh")
            if (shLink.exists()) shLink.delete()
            // On Android, we can't create real symlinks without root in some cases
            // Instead, we'll use /bin/sh -> busybox sh by passing "sh" as arg

            true
        } catch (e: Exception) {
            android.util.Log.e("BusyBox", "Install failed: ${e.message}")
            false
        }
    }
}
