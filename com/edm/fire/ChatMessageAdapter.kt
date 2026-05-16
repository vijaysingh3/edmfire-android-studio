package com.edm.fire

import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatMessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onCopyClick: (String) -> Unit
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_bot
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return MessageViewHolder(view, onCopyClick)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(
        itemView: View,
        private val onCopyClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        // ✅ Copy button — sirf bot message mein hoga (user message mein null hoga)
        private val btnCopy: ImageButton? = itemView.findViewById(R.id.btnCopy)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.content
            tvMessage.setTextIsSelectable(true)
            tvMessage.movementMethod = ScrollingMovementMethod.getInstance()
            tvTime.text = message.getFormattedTime()

            // ✅ Copy button click — sirf bot bubble mein visible hoga
            btnCopy?.setOnClickListener {
                onCopyClick(message.content)
            }
        }
    }
}