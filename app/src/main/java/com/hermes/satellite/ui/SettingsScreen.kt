package com.hermes.satellite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.satellite.SatelliteApp
import com.hermes.satellite.network.SatelliteWebSocket
import com.hermes.satellite.ssh.BusyBoxInstaller
import com.hermes.satellite.ssh.SshdServer
import com.hermes.satellite.ssh.SshTunnelManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ws = SatelliteApp.ws
    val wsState by ws.connectionState.collectAsState()

    // Persistent settings via SharedPreferences
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var pairingCode by remember { mutableStateOf(prefs.getString("pairing_code", "") ?: "") }
    var savedServer by remember { mutableStateOf(false) }

    // Persist settings whenever the user modifies them
    fun saveSettings() {
        prefs.edit()
            .putString("server_url", serverUrl)
            .putString("pairing_code", pairingCode)
            .apply()
    }

    // Log viewer state
    var showLogs by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showLogs) "开发者日志" else "设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showLogs) showLogs = false else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->

        if (showLogs) {
            // ── Log Viewer ──
            Column(
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Reload & Share buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            logContent = CrashLogger.getFullLog()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新日志")
                    }
                    Button(
                        onClick = { CrashLogger.share(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("分享日志")
                    }
                }

                // Log text
                val scrollState = rememberScrollState()
                Text(
                    text = logContent.ifEmpty { "点击「刷新日志」获取最新日志" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .verticalScroll(scrollState)
                )
            }
        } else {
            // ── Normal Settings ──
            Column(
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "服务器连接",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; savedServer = false },
                    label = { Text("Hermes 服务器地址") },
                    placeholder = { Text("your-server.com:8767") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = wsState != SatelliteWebSocket.State.CONNECTED
                )

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("配对码") },
                    placeholder = { Text("6 位配对码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = wsState != SatelliteWebSocket.State.CONNECTED
                )

                if (wsState == SatelliteWebSocket.State.CONNECTED) {
                    Button(
                        onClick = { ws.disconnect() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("断开连接")
                    }
                } else {
                    Button(
                        onClick = {
                            saveSettings()
                            ws.connect(serverUrl, pairingCode)
                            savedServer = true
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = serverUrl.isNotBlank() && pairingCode.length >= 4
                                && wsState != SatelliteWebSocket.State.CONNECTING
                                && wsState != SatelliteWebSocket.State.AUTHENTICATING
                    ) {
                        val label = when (wsState) {
                            SatelliteWebSocket.State.CONNECTING -> "连接中..."
                            SatelliteWebSocket.State.AUTHENTICATING -> "验证中..."
                            SatelliteWebSocket.State.ERROR -> "重试连接"
                            else -> "连接"
                        }
                        Text(label)
                    }
                }

                // Status
                val statusText = when (wsState) {
                    SatelliteWebSocket.State.CONNECTED -> "✅ 已连接"
                    SatelliteWebSocket.State.CONNECTING -> "🔄 连接中..."
                    SatelliteWebSocket.State.AUTHENTICATING -> "🔐 验证中..."
                    SatelliteWebSocket.State.RECONNECTING -> "🔄 重连中..."
                    SatelliteWebSocket.State.ERROR -> "❌ 连接失败"
                    SatelliteWebSocket.State.DISCONNECTED -> "⚪ 未连接"
                }
                val errorMsg = ws.lastError.collectAsState().value
                Text(
                    text = "状态: $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (errorMsg.isNotEmpty() && wsState == SatelliteWebSocket.State.ERROR) {
                    Text(
                        text = "错误: $errorMsg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()

                Text(
                    text = "配对说明",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "1. 确保 Hermes 服务器已启动 Satellite relay\n" +
                            "2. 在服务器上设置 SATELLITE_PAIRING_CODE\n" +
                            "3. 在此输入服务器地址和配对码\n" +
                            "4. 点击连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.weight(1f))

                // Developer section
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // SSH Tunnel status
                val sshState = SshTunnelManager.getState()
                val sshStateText = when (sshState) {
                    SshTunnelManager.State.DISCONNECTED -> "⚪ 未连接"
                    SshTunnelManager.State.CONNECTING -> "🟡 连接中..."
                    SshTunnelManager.State.CONNECTED -> "🟢 已连接"
                    SshTunnelManager.State.ERROR -> "🔴 连接失败"
                }
                val bushyBoxPath = BusyBoxInstaller.getBusyBoxPath(context)
                val hasBusyBox = java.io.File(bushyBoxPath).exists()

                Text(
                    text = "SSH 隧道",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "本地 SSHD: 127.0.0.1:2222 " +
                            (if (SshdServer.isRunning) "🟢 运行中" else "⚪ 未启动"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "反向隧道: $sshStateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "BusyBox: ${if (hasBusyBox) "✅ ${bushyBoxPath}" else "❌ 未安装"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "开发工具",
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedButton(
                    onClick = { showLogs = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看日志")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "版本 0.4.1 • Hermes Satellite",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
