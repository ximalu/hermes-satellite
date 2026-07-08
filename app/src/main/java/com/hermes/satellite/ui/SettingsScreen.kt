package com.hermes.satellite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.hermes.satellite.KeyboardTestActivity
import com.hermes.satellite.SatelliteApp
import com.hermes.satellite.network.SatelliteWebSocket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ws = SatelliteApp.ws
    val wsState by ws.connectionState.collectAsState()
    var serverUrl by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var savedServer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                )
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
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

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            val context = LocalContext.current
            Text(
                text = "诊断工具",
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, KeyboardTestActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("键盘测试（传统 View / 无 EdgeToEdge）")
            }
            Text(
                text = "纯 Android View，未启用 enableEdgeToEdge，用于定位系统层键盘问题",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "版本 0.4.0 • Hermes Satellite",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
