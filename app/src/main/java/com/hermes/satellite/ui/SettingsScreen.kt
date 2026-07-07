package com.hermes.satellite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onConnectionChanged: (Boolean) -> Unit = {}
) {
    var serverUrl by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }

    Column(
        modifier = modifier
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
            onValueChange = { serverUrl = it },
            label = { Text("Hermes 服务器地址") },
            placeholder = { Text("your-server.com:8767") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = pairingCode,
            onValueChange = { pairingCode = it },
            label = { Text("配对码") },
            placeholder = { Text("6 位配对码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = { /* TODO: connect to server */ },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = serverUrl.isNotBlank() && pairingCode.length == 6
        ) {
            Text("连接")
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        Text(
            text = "关于",
            style = MaterialTheme.typography.titleMedium
        )

        SettingsRow("版本", "0.1.0")
        SettingsRow("项目", "Hermes Satellite")
        SettingsRow("说明", "手机卫星节点 - 文件管理 + 内网扫描 + SSH 跳板")

        Spacer(Modifier.weight(1f))

        Text(
            text = "配对码由 Hermes 服务器生成。\n在 WebSocket 连接页面获取 6 位码后在此输入。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
