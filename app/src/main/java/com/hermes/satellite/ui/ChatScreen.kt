package com.hermes.satellite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.satellite.SatelliteApp
import com.hermes.satellite.data.ChatEntry
import com.hermes.satellite.data.ChatHistory
import com.hermes.satellite.network.SatelliteWebSocket
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ws = SatelliteApp.ws
    val wsState by ws.connectionState.collectAsState()

    // Chat history persistence
    val chatHistory = remember { ChatHistory(context) }

    // All messages loaded from persistence + new ones
    var messages by remember { mutableStateOf(chatHistory.load()) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Nested scroll interop for proper keyboard handling (from Element X)
    val nestedScrollInterop = rememberNestedScrollInteropConnection()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        attachedImageUri = uri
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Listen for server messages via WebSocket callbacks
    LaunchedEffect(Unit) {
        ws.onServerMessage = { text ->
            chatHistory.add(text, isUser = false)
            messages = chatHistory.load()
        }
        ws.onConnectedCallback = {
            // On reconnect, reload history (keeps existing messages)
            messages = chatHistory.load()
        }
    }

    // Cleanup callbacks on dispose
    DisposableEffect(Unit) {
        onDispose {
            ws.onServerMessage = null
            ws.onConnectedCallback = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(nestedScrollInterop),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (wsState == SatelliteWebSocket.State.CONNECTED)
                                    "连接成功，开始对话"
                                else
                                    "连接 Hermes 后，在这里与我对话",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                items(messages, key = { "${it.timestamp}-${it.text.hashCode()}" }) { msg ->
                    ChatBubble(
                        message = msg,
                        showTime = shouldShowTime(messages, msg)
                    )
                }
            }

            // Attachment preview
            attachedImageUri?.let { uri ->
                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📎 ${uri.lastPathSegment ?: "图片"}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { attachedImageUri = null }) {
                            Text("取消")
                        }
                    }
                }
            }

            // Input bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Image picker button
                if (attachedImageUri == null) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "发送图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Text input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("输入消息...") },
                    modifier = Modifier
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    )
                )
                Spacer(Modifier.width(4.dp))

                // Send button
                FilledTonalButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotBlank() && wsState == SatelliteWebSocket.State.CONNECTED) {
                            ws.send(text)
                            chatHistory.add(text, isUser = true)
                            messages = chatHistory.load()
                            inputText = ""
                        }
                        // Handle image if attached
                        if (attachedImageUri != null) {
                            ws.send("[图片] ${attachedImageUri!!.lastPathSegment}")
                            chatHistory.add("[图片] ${attachedImageUri!!.lastPathSegment}", isUser = true)
                            messages = chatHistory.load()
                            attachedImageUri = null
                        }
                    },
                    enabled = wsState == SatelliteWebSocket.State.CONNECTED
                            && (inputText.isNotBlank() || attachedImageUri != null),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun ChatBubble(message: ChatEntry, showTime: Boolean) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isUser) 12.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 12.dp,
            ),
            color = if (message.isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Show image placeholder or formatted text
                if (message.text.startsWith("[图片]")) {
                    Text(
                        text = "🖼️ ${message.text.removePrefix("[图片] ")}",
                        fontSize = 15.sp,
                        color = if (message.isUser)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Use new MessageTextRenderer for code blocks, URLs, inline code
                    MessageTextContent(
                        text = message.text,
                        isUser = message.isUser
                    )
                }
            }
        }

        // Timestamp
        if (showTime) {
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(
                    horizontal = 6.dp,
                    vertical = 2.dp
                )
            )
        }
    }
}

/** Show timestamp every 5 minutes or when sender changes */
private fun shouldShowTime(messages: List<ChatEntry>, msg: ChatEntry): Boolean {
    val idx = messages.indexOf(msg)
    if (idx <= 0) return true
    val prev = messages[idx - 1]
    if (prev.isUser != msg.isUser) return true
    return (msg.timestamp - prev.timestamp) > 5 * 60 * 1000
}
