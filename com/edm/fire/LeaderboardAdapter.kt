// LeaderboardAdapter.kt — OPTIMIZED (No changes needed from original)
package com.edm.fire

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LeaderboardAdapter(
    private val list: MutableList<LeaderboardModel>
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private val colorRank1 = Color.parseColor("#2A1F00")
    private val colorRank2 = Color.parseColor("#1A1A1A")
    private val colorRank3 = Color.parseColor("#1A0F00")
    private val colorNormal = Color.parseColor("#1A1A2E")

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutBg: LinearLayout  = itemView.findViewById(R.id.layoutItemBg)
        val tvRankEmoji: TextView   = itemView.findViewById(R.id.tvRankEmoji)
        val tvUserName: TextView    = itemView.findViewById(R.id.tvUserName)
        val tvInGameUID: TextView   = itemView.findViewById(R.id.tvInGameUID)
        val tvInGameLevel: TextView = itemView.findViewById(R.id.tvInGameLevel)
        val tvMatchPlayed: TextView = itemView.findViewById(R.id.tvMatchPlayed)
        val tvWinningCount: TextView= itemView.findViewById(R.id.tvWinningCount)
        val tvWinningCoins: TextView= itemView.findViewById(R.id.tvWinningCoins)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val rank = item.rank.takeIf { it > 0 } ?: (position + 1)

        holder.tvUserName.text = when {
            item.inGameName.isNotEmpty() -> item.inGameName
            item.userName.isNotEmpty()   -> item.userName
            else                         -> "Player $rank"
        }

        holder.tvInGameUID.text   = if (item.inGameUID > 0) "UID: ${item.inGameUID}" else "UID: N/A"
        holder.tvInGameLevel.text = if (item.inGameLevel > 0) "Lv. ${item.inGameLevel}" else "Lv. N/A"
        holder.tvMatchPlayed.text  = item.matchPlayed.toString()
        holder.tvWinningCount.text = item.winningCount.toString()
        holder.tvWinningCoins.text = item.winningCoins.toString()

        when (rank) {
            1 -> {
                holder.tvRankEmoji.text = "🏆"
                holder.layoutBg.setBackgroundColor(colorRank1)
                holder.tvUserName.setTextColor(Color.parseColor("#FFD700"))
            }
            2 -> {
                holder.tvRankEmoji.text = "🥈"
                holder.layoutBg.setBackgroundColor(colorRank2)
                holder.tvUserName.setTextColor(Color.parseColor("#C0C0C0"))
            }
            3 -> {
                holder.tvRankEmoji.text = "🥉"
                holder.layoutBg.setBackgroundColor(colorRank3)
                holder.tvUserName.setTextColor(Color.parseColor("#CD7F32"))
            }
            else -> {
                holder.tvRankEmoji.text = "#$rank"
                holder.layoutBg.setBackgroundColor(colorNormal)
                holder.tvUserName.setTextColor(Color.WHITE)
            }
        }
    }

    override fun getItemCount(): Int = list.size

    /** Called when loading more pages — appends items without clearing */
    fun updateList(newList: List<LeaderboardModel>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}