package com.hermes.satellite.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class NavTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("聊天", Icons.Default.Chat),
    NavTab("文件", Icons.Default.Folder),
    NavTab("设备", Icons.Default.Devices),
    NavTab("设置", Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    // Shared connection state — will be wired to WebSocket later
    val connected = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛰️ Hermes Satellite") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    Text(
                        text = if (connected.value) "🟢 已连接" else "🔴 未连接",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ChatScreen(
                modifier = Modifier.padding(padding),
                connected = connected.value
            )
            1 -> FileScreen(modifier = Modifier.padding(padding))
            2 -> DeviceScreen(modifier = Modifier.padding(padding))
            3 -> SettingsScreen(
                modifier = Modifier.padding(padding),
                onConnectionChanged = { connected.value = it }
            )
        }
    }
}
