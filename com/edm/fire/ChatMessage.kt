package com.edm.fire

import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Message data class - Used by ChatAdapter and HelpFragment
 */
data class ChatMessage(
    val content: String,           // Message content
    val isUser: Boolean,           // True = user, False = bot/AI
    val timestamp: Long,           // Time in milliseconds
    val userId: String = "",       // For chat with multiple users
    val userName: String = ""      // Sender name
) {
    // For backward compatibility with ChatAdapter
    val message: String
        get() = content

    fun getFormattedTime(): String {
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    fun getSimpleTime(): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}