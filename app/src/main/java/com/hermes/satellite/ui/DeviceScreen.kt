package com.hermes.satellite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<NetworkScanner.Device>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf("") }
    var scanStatus by remember { mutableStateOf("点击按钮开始扫描") }

    suspend fun doScan() {
        scanning = true
        scanStatus = "读取 ARP 表..."
        val arpDevices = NetworkScanner.scanArpTable()
        scanStatus = "Ping 扫描中 (并发 254 个)..."
        val pingDevices = NetworkScanner.pingScan()
        val merged = NetworkScanner.mergeResults(arpDevices, pingDevices)
        devices = merged
        scanning = false
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lastScanTime = now
        scanStatus = if (merged.isEmpty()) "未发现设备" else "发现 ${merged.size} 个活跃设备"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    FilledTonalButton(
                        onClick = {
                            scope.launch { doScan() }
                        },
                        enabled = !scanning,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (scanning) "扫描中" else "扫描")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(modifier = modifier
            .padding(padding)
            .fillMaxSize()) {

            // Status line
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
                        if (lastScanTime.isNotEmpty()) {
                            Text(
                                text = "上次扫描: $lastScanTime",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = scanStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Device list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (devices.isEmpty() && !scanning) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "点击右上角「扫描」按钮发现内网设备\n\n" +
                                        "WiFi 和 5G 同时开启时，扫描的是蜂窝数据网段\n" +
                                        "建议关闭移动数据、连接 WiFi 后扫描",
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
                    text = "💡 ARP 表 + 并发 Ping，约 3 秒完成扫描。MAC 地址来自 ARP 表，主机名来自反向 DNS。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(device: NetworkScanner.Device) {
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
            // Icon: router detection via IP ending in .1 or .254
            val isLikelyRouter = device.ip.endsWith(".1") || device.ip.endsWith(".254")
            Icon(
                imageVector = if (isLikelyRouter) Icons.Default.Router else Icons.Default.Computer,
                contentDescription = null,
                tint = if (isLikelyRouter) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (device.hostname.isNotEmpty()) device.hostname else device.ip,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (device.hostname.isNotEmpty()) {
                    Text(
                        text = device.ip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (device.mac.isNotEmpty()) {
                    Text(
                        text = "MAC: ${device.mac}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (device.interfaceName.isNotEmpty()) {
                    Text(
                        text = "接口: ${device.interfaceName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
