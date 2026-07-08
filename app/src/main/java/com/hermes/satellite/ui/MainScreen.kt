package com.hermes.satellite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

enum class SatelliteScreen { CHAT, FILES, DEVICES, SETTINGS }

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(SatelliteScreen.CHAT) }

    // System back button: Chat → 退出, 其他页 → 返回 Chat
    BackHandler(currentScreen != SatelliteScreen.CHAT) {
        currentScreen = SatelliteScreen.CHAT
    }

    when (currentScreen) {
        SatelliteScreen.CHAT -> ChatScreen(
            onNavigateToFiles = { currentScreen = SatelliteScreen.FILES },
            onNavigateToDevices = { currentScreen = SatelliteScreen.DEVICES },
            onNavigateToSettings = { currentScreen = SatelliteScreen.SETTINGS }
        )
        SatelliteScreen.FILES -> FileScreen(
            onBack = { currentScreen = SatelliteScreen.CHAT }
        )
        SatelliteScreen.DEVICES -> DeviceScreen(
            onBack = { currentScreen = SatelliteScreen.CHAT }
        )
        SatelliteScreen.SETTINGS -> SettingsScreen(
            onBack = { currentScreen = SatelliteScreen.CHAT }
        )
    }
}
