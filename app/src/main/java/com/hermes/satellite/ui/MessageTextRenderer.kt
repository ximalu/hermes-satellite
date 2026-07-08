package com.hermes.satellite.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Parsed segment of a message.
 */
private sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()
    data class CodeBlock(val content: String, val lang: String = "") : MessageSegment()
    data class InlineCode(val content: String) : MessageSegment()
    data class Url(val url: String) : MessageSegment()
}

/**
 * Parse a message string into segments: code blocks, inline code, URLs, plain text.
 */
private fun parseMessage(text: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    var remaining = text

    while (remaining.isNotEmpty()) {
        // Try to find a ``` code block first
        val codeBlockStart = remaining.indexOf("```")
        if (codeBlockStart >= 0) {
            // Text before code block
            if (codeBlockStart > 0) {
                val before = remaining.substring(0, codeBlockStart)
                segments.addAll(parseInline(before))
            }
            // Find closing ``` (after the opening one + optional lang)
            val afterOpen = remaining.substring(codeBlockStart + 3)
            val newlineIdx = afterOpen.indexOf('\n')
            val lang = if (newlineIdx >= 0) afterOpen.substring(0, newlineIdx).trim() else ""
            val contentStart = if (newlineIdx >= 0) newlineIdx + 1 else 0
            val codeBlockEnd = afterOpen.indexOf("```", contentStart)
            if (codeBlockEnd >= 0) {
                val code = afterOpen.substring(contentStart, codeBlockEnd)
                segments.add(MessageSegment.CodeBlock(code.trimEnd(), lang))
                remaining = afterOpen.substring(codeBlockEnd + 3)
            } else {
                // No closing ``` — treat rest as inline
                segments.addAll(parseInline(afterOpen))
                remaining = ""
            }
        } else {
            // No code block — parse inline content
            segments.addAll(parseInline(remaining))
            remaining = ""
        }
    }

    return segments
}

/**
 * Parse inline content: inline code (`...`) and URLs, rest is plain text.
 */
private fun parseInline(text: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    var remaining = text

    while (remaining.isNotEmpty()) {
        // Try inline code first: `...`
        val inlineStart = remaining.indexOf('`')
        if (inlineStart >= 0) {
            if (inlineStart > 0) {
                segments.addAll(parseUrls(remaining.substring(0, inlineStart)))
            }
            val afterOpen = remaining.substring(inlineStart + 1)
            val inlineEnd = afterOpen.indexOf('`')
            if (inlineEnd >= 0) {
                segments.add(MessageSegment.InlineCode(afterOpen.substring(0, inlineEnd)))
                remaining = afterOpen.substring(inlineEnd + 1)
            } else {
                // No closing backtick — treat as URL/text
                segments.addAll(parseUrls(afterOpen))
                remaining = ""
            }
        } else {
            segments.addAll(parseUrls(remaining))
            remaining = ""
        }
    }

    return segments
}

// Simple URL regex — matches http/https URLs
private val URL_REGEX = Regex("https?://[\\w\\-./?=&%+#@!~()',;:*_]+[\\w\\-./?=&%+#@!~()';:*_]")

/**
 * Parse URLs from text, splitting into Text and Url segments.
 */
private fun parseUrls(text: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    var lastEnd = 0
    for (match in URL_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) {
            segments.add(MessageSegment.Text(text.substring(lastEnd, match.range.first)))
        }
        segments.add(MessageSegment.Url(match.value))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        segments.add(MessageSegment.Text(text.substring(lastEnd)))
    }
    return segments
}

/**
 * Renders a message's text content with formatted code blocks, inline code, and clickable URLs.
 */
@Composable
fun MessageTextContent(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseMessage(text) }
    val context = LocalContext.current
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (segment in segments) {
            when (segment) {
                is MessageSegment.Text -> {
                    if (segment.content.isNotBlank()) {
                        Text(
                            text = segment.content,
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                }

                is MessageSegment.InlineCode -> {
                    InlineCodeChip(segment.content, textColor)
                }

                is MessageSegment.CodeBlock -> {
                    CodeBlockSurface(segment.content, segment.lang, context)
                }

                is MessageSegment.Url -> {
                    UrlText(segment.url, textColor)
                }
            }
        }
    }
}

/**
 * Renders inline code `like this` with monospace font and subtle background.
 */
@Composable
private fun InlineCodeChip(
    code: String,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Renders a code block ```...``` with monospace font, dark background, and a copy button.
 */
@Composable
private fun CodeBlockSurface(
    code: String,
    lang: String,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                // Language label (if present)
                if (lang.isNotBlank()) {
                    Text(
                        text = lang,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Scrollable code content
                SelectionContainer {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }

            // Copy button
            IconButton(
                onClick = {
                    copyToClipboard(context, code)
                    copied = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = if (copied) "已复制" else "复制代码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Renders a clickable URL link.
 */
@Composable
private fun UrlText(
    url: String,
    textColor: Color
) {
    val context = LocalContext.current
    val annotatedString = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(url)
        }
    }

    ClickableText(
        text = annotatedString,
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (_: Exception) {
                // No browser available — silently ignore
            }
        },
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("code", text)
    clipboard.setPrimaryClip(clip)
}
