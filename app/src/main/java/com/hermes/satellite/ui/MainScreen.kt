package com.hermes.satellite.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

enum class SatelliteScreen { CHAT, FILES, DEVICES, SETTINGS }

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(SatelliteScreen.CHAT) }

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
