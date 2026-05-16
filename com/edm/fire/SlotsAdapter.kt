package com.edm.fire

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

// 🔥 Slot data class yahan define karo
data class Slot(
    val slotNumber: Int,
    val slotStatus: String,
    val playerName: String = "",
    val playerUID: String = "",
    val userId: String = "",
    val isSelected: Boolean = false,
    val isBeingSelectedByOthers: Boolean = false
)

class SlotsAdapter(
    private var slotsList: List<Slot>,
    private val onSlotClickListener: (Slot) -> Unit
) : RecyclerView.Adapter<SlotsAdapter.SlotViewHolder>() {

    fun getSlotsList(): List<Slot> = slotsList

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSlotNumber: TextView = itemView.findViewById(R.id.tvSlotNumber)
        val tvSlotStatus: TextView = itemView.findViewById(R.id.tvSlotStatus)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSlotClickListener(slotsList[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slotsList[position]
        holder.tvSlotNumber.text = slot.slotNumber.toString()

        when {
            slot.isBeingSelectedByOthers && slot.slotStatus == "Available" -> {
                holder.tvSlotStatus.text = "SELECTING...\nBY ANOTHER"
                holder.tvSlotStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                holder.tvSlotNumber.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
                holder.itemView.isEnabled = false
                holder.itemView.alpha = 0.7f
            }
            slot.slotStatus == "Occupied" -> {
                holder.tvSlotStatus.text = if (slot.playerName.isNotEmpty()) "${slot.playerName}\nUID: ${slot.playerUID}" else "OCCUPIED"
                holder.tvSlotStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                holder.tvSlotNumber.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                holder.tvSlotNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                holder.itemView.isEnabled = false
                holder.itemView.alpha = 0.6f
            }
            slot.isSelected -> {
                holder.tvSlotStatus.text = "✅ SELECTED\nBY YOU"
                holder.tvSlotStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                holder.tvSlotNumber.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
                holder.tvSlotNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
                holder.itemView.isEnabled = true
                holder.itemView.alpha = 1.0f
            }
            slot.slotStatus == "Available" -> {
                holder.tvSlotStatus.text = "AVAILABLE\nCLICK TO SELECT"
                holder.tvSlotStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
                holder.tvSlotNumber.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                holder.tvSlotNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.transparent))
                holder.itemView.isEnabled = true
                holder.itemView.alpha = 1.0f
            }
            else -> {
                holder.tvSlotStatus.text = "NOT AVAILABLE"
                holder.tvSlotStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                holder.tvSlotNumber.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                holder.tvSlotNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                holder.itemView.isEnabled = false
                holder.itemView.alpha = 0.6f
            }
        }
    }

    override fun getItemCount(): Int = slotsList.size

    fun updateSlots(newSlots: List<Slot>) {
        slotsList = newSlots
        notifyDataSetChanged()
    }
}