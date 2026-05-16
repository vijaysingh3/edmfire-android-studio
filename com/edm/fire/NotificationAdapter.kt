package com.edm.fire

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NotificationAdapter(
    private val list: MutableList<NotificationModel>,
    private val onItemClick: ((NotificationModel) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    var isSelectionMode = false
        private set

    private val selectedIds = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val iconType: ImageView = view.findViewById(R.id.iconType)
        val unreadDot: View = view.findViewById(R.id.unreadDot)
        val rootLayout: View = view.findViewById(R.id.rootLayout)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = list[position]
        val notificationId = notification.id

        holder.tvTitle.text = notification.title.orEmpty()
        holder.tvMessage.text = notification.message.orEmpty()
        holder.tvTime.text = formatTimestamp(notification.timestamp)

        holder.unreadDot.visibility = if (!notification.read) View.VISIBLE else View.GONE
        setNotificationIcon(holder.iconType, notification.type)

        val defaultBgColor = if (!notification.read) {
            ContextCompat.getColor(holder.itemView.context, R.color.unread_notification_bg)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.read_notification_bg)
        }
        holder.rootLayout.setBackgroundColor(defaultBgColor)

        holder.cbSelect.setOnCheckedChangeListener(null)

        if (isSelectionMode && notificationId != null) {
            holder.cbSelect.visibility = View.VISIBLE
            holder.cbSelect.isChecked = selectedIds.contains(notificationId)

            if (selectedIds.contains(notificationId)) {
                holder.rootLayout.setBackgroundColor(Color.parseColor("#2A3A2A"))
            }

            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(notificationId)
                    holder.rootLayout.setBackgroundColor(Color.parseColor("#2A3A2A"))
                } else {
                    selectedIds.remove(notificationId)
                    holder.rootLayout.setBackgroundColor(defaultBgColor)
                }
                onSelectionChanged?.invoke(selectedIds.size)
            }

            holder.itemView.setOnClickListener {
                holder.cbSelect.isChecked = !holder.cbSelect.isChecked
            }
        } else {
            holder.cbSelect.visibility = View.GONE
            holder.cbSelect.isChecked = false
            holder.itemView.setOnClickListener {
                onItemClick?.invoke(notification)
            }
        }
    }

    override fun getItemCount() = list.size

    fun enterSelectionMode() {
        isSelectionMode = true
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedIds.clear()
        list.forEach { item ->
            item.id?.let { selectedIds.add(it) }
        }
        onSelectionChanged?.invoke(selectedIds.size)
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedIds.clear()
        onSelectionChanged?.invoke(0)
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun hasSelection() = selectedIds.isNotEmpty()

    // yaha new IST format aur old format dono safely handle ho rahe hain
    private fun formatTimestamp(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return ""

        val displayFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale("en", "IN")).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        )

        displayFormats.forEach { format ->
            try {
                val parsedDate = format.parse(timestamp)
                if (parsedDate != null) {
                    return SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale("en", "IN")).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                    }.format(parsedDate)
                }
            } catch (_: Exception) {
            }
        }

        return timestamp
    }

    private fun setNotificationIcon(imageView: ImageView, type: String?) {
        val iconRes = when (type) {
            "tournament_joined" -> R.drawable.ic_tournament
            "tournament_winnings" -> R.drawable.ic_winning
            "profile_verified" -> R.drawable.ic_profile_verified
            "kyc_required" -> R.drawable.ic_kyc
            "tournament_join_failed" -> R.drawable.ic_error
            "no_notifications" -> R.drawable.ic_info
            "error" -> R.drawable.ic_error
            else -> R.drawable.ic_notification
        }
        imageView.setImageResource(iconRes)
    }
}