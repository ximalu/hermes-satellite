package com.hermes.satellite.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ChatEntry(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatHistory(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    private val _messages = mutableListOf<ChatEntry>()

    fun load(): List<ChatEntry> {
        if (_messages.isNotEmpty()) return _messages.toList()
        val json = prefs.getString("messages", "[]") ?: "[]"
        val arr = JSONArray(json)
        _messages.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            _messages.add(ChatEntry(
                text = obj.getString("text"),
                isUser = obj.getBoolean("isUser"),
                timestamp = obj.optLong("timestamp", 0L)
            ))
        }
        return _messages.toList()
    }

    fun add(text: String, isUser: Boolean) {
        val entry = ChatEntry(text = text, isUser = isUser)
        _messages.add(entry)
        save()
    }

    fun clear() {
        _messages.clear()
        prefs.edit().remove("messages").apply()
    }

    private fun save() {
        val arr = JSONArray()
        for (msg in _messages) {
            arr.put(JSONObject().apply {
                put("text", msg.text)
                put("isUser", msg.isUser)
                put("timestamp", msg.timestamp)
            })
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }
}
