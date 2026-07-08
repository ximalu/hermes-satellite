package com.hermes.satellite.ssh

/**
 * SSH configuration delivered from Hermes server via WebSocket.
 */
data class SshConfig(
    val host: String,
    val port: Int,
    val user: String,
    val privateKey: String
)
