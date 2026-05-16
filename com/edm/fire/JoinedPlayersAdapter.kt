package com.edm.fire

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class JoinedPlayersAdapter(
    private var playersList: List<Player>
) : RecyclerView.Adapter<JoinedPlayersAdapter.PlayerViewHolder>() {

    // ✅ Bank Method: Paisa → Coins with DECIMAL support
    // Database: 150 paisa → UI Show: 1.5 Coins
    private fun paisaToCoins(paisa: Int): Double {
        return paisa / 100.0
    }

    // ✅ Format coins with proper decimal display
    // 100 → "100", 150 → "1.5", 175 → "1.75"
    private fun formatCoins(coins: Double): String {
        return if (coins % 1 == 0.0) {
            coins.toInt().toString()
        } else {
            val formatted = String.format("%.2f", coins)
            when {
                formatted.endsWith(".00") -> formatted.dropLast(3)
                formatted.endsWith("0") -> formatted.dropLast(1)
                else -> formatted
            }
        }
    }

    inner class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPlayerNumber: TextView = itemView.findViewById(R.id.tvPlayerNumber)
        val tvPlayerName: TextView = itemView.findViewById(R.id.tvPlayerName)
        val tvPlayerUID: TextView = itemView.findViewById(R.id.tvPlayerUID)
        val tvPlayerLevel: TextView = itemView.findViewById(R.id.tvPlayerLevel)
        val tvPositionSeat: TextView = itemView.findViewById(R.id.tvPositionSeat)

        // K/D/A Stats
        val tvKills: TextView = itemView.findViewById(R.id.textView8)
        val tvDeaths: TextView = itemView.findViewById(R.id.textView15)
        val tvAssists: TextView = itemView.findViewById(R.id.textView19)

        // Damage, Rank, Coins
        val tvDamage: TextView = itemView.findViewById(R.id.textView20)
        val tvRank: TextView = itemView.findViewById(R.id.textView21)
        val tvCoinsEarned: TextView = itemView.findViewById(R.id.textView23)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_joined_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = playersList[position]

        // Player number (position + 1)
        holder.tvPlayerNumber.text = (position + 1).toString()

        // Player basic details
        holder.tvPlayerName.text = if (player.InGameName.isNotEmpty()) player.InGameName else "****"
        holder.tvPlayerUID.text = "UID: ${if (player.InGameUID != 0L) player.InGameUID.toString() else "****"}"
        holder.tvPlayerLevel.text = "Level: ${if (player.InGameLevel != 0) player.InGameLevel.toString() else "****"}"
        holder.tvPositionSeat.text = "Seat ${if (player.PositionSeat != 0) player.PositionSeat.toString() else "****"}"

        // K/D/A Stats
        holder.tvKills.text = "K: ${if (player.Kills != 0) player.Kills.toString() else "****"}"
        holder.tvDeaths.text = "D: ${if (player.Deaths != 0) player.Deaths.toString() else "****"}"
        holder.tvAssists.text = "A: ${if (player.Assists != 0) player.Assists.toString() else "****"}"

        // Damage, Rank
        holder.tvDamage.text = "Damage: ${if (player.Damage != 0) player.Damage.toString() else "****"}"
        holder.tvRank.text = if (player.Rank != 0) "Rank #${player.Rank}" else "****"

        // ✅ Coins Earned - Convert PAISA to Coins with decimal support
        val coinsEarnedInCoins = paisaToCoins(player.CoinsEarned)
        val formattedCoins = if (player.CoinsEarned != 0) {
            formatCoins(coinsEarnedInCoins)
        } else {
            "****"
        }
        holder.tvCoinsEarned.text = "Winning: $formattedCoins Coins"
    }

    override fun getItemCount(): Int = playersList.size

    fun updateList(newList: List<Player>) {
        playersList = newList
        notifyDataSetChanged()
    }
}