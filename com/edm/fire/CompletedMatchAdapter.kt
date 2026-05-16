package com.edm.fire

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target

class CompletedMatchAdapter(private val matchesList: List<CompletedMatch>) :
    RecyclerView.Adapter<CompletedMatchAdapter.CompletedMatchViewHolder>() {

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

    inner class CompletedMatchViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val ivBanner: ImageView = itemView.findViewById(R.id.ivTournamentBanner)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTournamentTitle)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvEntryFee: TextView = itemView.findViewById(R.id.tvEntryFee)
        val tvSlotInfo: TextView = itemView.findViewById(R.id.tvSlotInfo)
        val tvPricePool: TextView = itemView.findViewById(R.id.tvPricePool)
        val tvJoinStatus: TextView = itemView.findViewById(R.id.tvJoinStatus)
        val tvTournamentId: TextView = itemView.findViewById(R.id.tvTournamentId)
        val tvType: TextView = itemView.findViewById(R.id.tvType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompletedMatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_completed_match, parent, false)
        return CompletedMatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompletedMatchViewHolder, position: Int) {
        val match = matchesList[position]

        // Banner loading — Glide with disk cache
        if (match.bannerUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(match.bannerUrl)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .dontTransform()
                .placeholder(R.drawable.coming_soon)
                .error(R.drawable.coming_soon)
                .into(holder.ivBanner)
        } else {
            holder.ivBanner.setImageResource(R.drawable.coming_soon)
        }

        // ✅ Convert paisa to coins with decimal support
        val entryFeeInCoins = paisaToCoins(match.entryFee)
        val pricePoolInCoins = paisaToCoins(match.pricePool)

        // Tournament info set karen
        holder.tvTitle.text = match.title
        holder.tvDateTime.text = match.dateTime

        // ✅ UPDATED: Show converted coins with proper format
        holder.tvEntryFee.text = "Entry: ${formatCoins(entryFeeInCoins)} Coins"
        holder.tvSlotInfo.text = "Your Slot: ${match.userSlotNumber}"
        holder.tvPricePool.text = "Prize Pool: ${formatCoins(pricePoolInCoins)} Coins"
        holder.tvJoinStatus.text = "COMPLETED - Watch Now"

        // Tournament ID aur Type display
        holder.tvTournamentId.text = "ID: ${match.tournamentId}"
        holder.tvType.text = "Type: ${match.type}"

        // Watch Now functionality — YouTube video open karega
        holder.tvJoinStatus.setOnClickListener {
            if (match.videoUrl.isNotEmpty()) {
                openYouTubeVideo(holder.itemView.context, match.videoUrl)
            } else {
                Toast.makeText(holder.itemView.context, "Video URL not available yet", Toast.LENGTH_SHORT).show()
            }
        }

        // Item click — MatchDetailActivity ko ACTUAL meta fields pass karenge
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, MatchDetailActivity::class.java).apply {
                putExtra("TOURNAMENT_ID", match.tournamentId)
                putExtra("TOURNAMENT_TYPE", match.tournamentType)
                putExtra("TOURNAMENT_TITLE", match.title)
                putExtra("TOURNAMENT_MAP", match.map)
                putExtra("TOURNAMENT_MODE", match.mode)
                putExtra("TOURNAMENT_TYPE_NAME", match.type)
                putExtra("TOURNAMENT_DATETIME", match.dateTime)
                putExtra("TOURNAMENT_BANNERURL", match.bannerUrl)
                putExtra("TOURNAMENT_STATUS", match.status)
                putExtra("TOURNAMENT_PRIZEPOOL", match.pricePool)
                putExtra("TOURNAMENT_JOININGFEE", match.entryFee)
                putExtra("TOURNAMENT_PERKILL", match.perKill)
                putExtra("TOURNAMENT_SLOTNUMBERS", match.slotNumbers)
                putExtra("TOURNAMENT_JOINEDCOUNT", match.joinedCount)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = matchesList.size

    // YouTube video open karega
    private fun openYouTubeVideo(context: Context, videoUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Failed to open video — silently ignore
        }
    }
}