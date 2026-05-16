package com.edm.fire

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ConversationStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("edm_chat_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val KEY_DRAFT = "input_draft"        // ✅ Draft key
        private const val EXPIRY_MS = 24 * 60 * 60 * 1000L
    }

    // ✅ Draft save karo — har character type pe
    fun saveDraft(text: String) {
        prefs.edit().putString(KEY_DRAFT, text).apply()
    }

    // ✅ Draft wapas lo — fragment open hone pe
    fun getDraft(): String {
        return prefs.getString(KEY_DRAFT, "") ?: ""
    }

    // ✅ Draft clear karo — message send hone ke baad
    fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    fun saveMessage(message: ChatMessage) {
        val array = getRawArray()
        val obj = JSONObject().apply {
            put("text", message.content)
            put("isUser", message.isUser)
            put("timestamp", message.timestamp)
            put("userId", message.userId)
            put("userName", message.userName)
        }
        array.put(obj)
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun getHistory(): List<ChatMessage> {
        val now = System.currentTimeMillis()
        val array = getRawArray()
        val result = mutableListOf<ChatMessage>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val timestamp = obj.getLong("timestamp")
            if (now - timestamp <= EXPIRY_MS) {
                result.add(
                    ChatMessage(
                        content = obj.getString("text"),
                        isUser = obj.getBoolean("isUser"),
                        timestamp = timestamp,
                        userId = obj.optString("userId", ""),
                        userName = obj.optString("userName", "")
                    )
                )
            }
        }

        if (result.size < array.length()) {
            val pruned = JSONArray()
            result.forEach { msg ->
                pruned.put(JSONObject().apply {
                    put("text", msg.content)
                    put("isUser", msg.isUser)
                    put("timestamp", msg.timestamp)
                    put("userId", msg.userId)
                    put("userName", msg.userName)
                })
            }
            prefs.edit().putString(KEY_MESSAGES, pruned.toString()).apply()
        }

        return result
    }

    fun getHistoryAsJson(): JSONArray {
        val history = getHistory()
        val array = JSONArray()
        history.forEach { msg ->
            array.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.content)
                put("timestamp", msg.timestamp)
            })
        }
        return array
    }

    fun clearHistory() {
        prefs.edit()
            .remove(KEY_MESSAGES)
            .remove(KEY_DRAFT)   // ✅ History clear pe draft bhi clear
            .apply()
    }

    private fun getRawArray(): JSONArray {
        val str = prefs.getString(KEY_MESSAGES, null)
        return if (str.isNullOrEmpty()) JSONArray() else JSONArray(str)
    }
}