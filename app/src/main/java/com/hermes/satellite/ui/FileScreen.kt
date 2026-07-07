package com.hermes.satellite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class FileEntry(
    val name: String,
    val isDir: Boolean,
    val size: String = "",
    val date: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScreen(modifier: Modifier = Modifier) {
    // Placeholder: root path display
    var currentPath by remember { mutableStateOf("/sdcard") }
    var entries by remember { mutableStateOf(placeholderFiles) }

    Column(modifier = modifier.fillMaxSize()) {
        // Path bar
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "📁 $currentPath",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // File list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(entries) { entry ->
                FileRow(entry)
            }
        }

        // Bottom info
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${entries.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "连接后在此浏览手机文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: navigate or select */ }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDir)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDir && entry.size.isNotEmpty()) {
                Text(
                    text = entry.size,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (entry.date.isNotEmpty()) {
            Text(
                text = entry.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { /* TODO: context menu */ }) {
            Icon(Icons.Default.MoreVert, "更多", modifier = Modifier.size(18.dp))
        }
    }
}

private val placeholderFiles = listOf(
    FileEntry("Download", true, date = "2026-07-07"),
    FileEntry("DCIM", true, date = "2026-07-05"),
    FileEntry("Documents", true, date = "2026-07-03"),
    FileEntry("Android", true, date = "2026-06-28"),
    FileEntry("TVBoxC_release.apk", false, "14.2 MB", "2026-07-06"),
    FileEntry("排程表_v2.png", false, "120 KB", "2026-07-07"),
    FileEntry("notes.txt", false, "2 KB", "2026-07-04"),
)
