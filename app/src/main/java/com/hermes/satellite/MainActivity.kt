package com.hermes.satellite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hermes.satellite.ui.MainScreen
import com.hermes.satellite.ui.theme.SatelliteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SatelliteTheme {
                MainScreen()
            }
        }
    }
}
