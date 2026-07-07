package com.hermes.satellite

import android.app.Application
import com.hermes.satellite.network.SatelliteWebSocket
import com.hermes.satellite.service.SatelliteService

class SatelliteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SatelliteService.start(this)
    }

    companion object {
        val ws = SatelliteWebSocket()
    }
}
