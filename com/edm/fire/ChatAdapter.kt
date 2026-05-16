package com.edm.fire

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private var chatList: List<ChatMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun getItemViewType(position: Int): Int {
        val message = chatList[position]
        return if (message.userId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = chatList[position]

        when (holder) {
            is SentMessageViewHolder -> {
                holder.tvMessage.text = message.content
                holder.tvTime.text = message.getSimpleTime()
            }
            is ReceivedMessageViewHolder -> {
                holder.tvMessage.text = message.content
                holder.tvUserName.text = if (message.userName.isNotEmpty()) message.userName else "User"
                holder.tvTime.text = message.getSimpleTime()
            }
        }
    }

    override fun getItemCount(): Int = chatList.size

    fun updateList(newList: List<ChatMessage>) {
        chatList = newList
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }
}