package com.hermes.satellite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class Device(
    val ip: String,
    val name: String,
    val openPorts: List<String>,
    val isRouter: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(modifier: Modifier = Modifier) {
    var devices by remember { mutableStateOf(placeholderDevices) }
    var scanning by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf("尚未扫描") }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with scan button
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "内网设备",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "上次扫描: $lastScan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { /* TODO: trigger nmap scan */ },
                    enabled = !scanning
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (scanning) "扫描中..." else "扫描")
                }
            }
        }

        // Device list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (devices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "连接后扫描内网，设备将显示在这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(devices) { device ->
                DeviceCard(device)
            }
        }

        // Bottom hint
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "💡 连上内网 WiFi 后点击扫描，或直接对我说\"扫一下内网\"",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DeviceCard(device: Device) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isRouter) Icons.Default.Router else Icons.Default.Computer,
                contentDescription = null,
                tint = if (device.isRouter) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.ip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.openPorts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        device.openPorts.forEach { port ->
                            AssistChip(
                                onClick = {},
                                label = { Text(port, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val placeholderDevices = listOf(
    Device("192.168.1.1", "路由器", listOf("53", "80", "443"), isRouter = true),
    Device("192.168.1.100", "ubuntu-server", listOf("22 SSH", "80 HTTP")),
    Device("192.168.1.50", "nas", listOf("22 SSH")),
)
