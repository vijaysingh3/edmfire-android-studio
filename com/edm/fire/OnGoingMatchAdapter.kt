package com.edm.fire

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

class OnGoingMatchAdapter(private val matchesList: List<OnGoingMatch>) :
    RecyclerView.Adapter<OnGoingMatchAdapter.OnGoingMatchViewHolder>() {

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

    inner class OnGoingMatchViewHolder(itemView: View) :
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
        val tvRoomId: TextView = itemView.findViewById(R.id.tv_room_id)
        val tvRoomPassword: TextView = itemView.findViewById(R.id.tv_room_password)
        val tvWarningToUsers: TextView = itemView.findViewById(R.id.tv_warning_to_users)
        val iconSuccess1: ImageView = itemView.findViewById(R.id.icon_success1)
        val iconSuccess2: ImageView = itemView.findViewById(R.id.icon_success2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnGoingMatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ongoing_match, parent, false)
        return OnGoingMatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnGoingMatchViewHolder, position: Int) {
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
        holder.tvJoinStatus.text = "Playing - Watch Now"

        // Tournament ID aur Type display
        holder.tvTournamentId.text = "ID: ${match.tournamentId}"
        holder.tvType.text = "Type: ${match.type}"

        // Room ID aur Room Password set karen — Meta se aaya hai
        holder.tvRoomId.text = match.roomId
        holder.tvRoomPassword.text = match.roomPassword

        // Initially hide success icons
        holder.iconSuccess1.visibility = View.GONE
        holder.iconSuccess2.visibility = View.GONE

        // Room data available hai ya nahi check karen
        // "Coming Soon...." matlab room abhi create nahi hua
        val hasActualRoomData = match.roomId != "Coming Soon...." &&
                match.roomPassword != "Coming Soon...." &&
                match.roomId.isNotEmpty() &&
                match.roomId != "null"

        // Warning message — sirf actual room data hone pe dikhayenge
        holder.tvWarningToUsers.visibility = if (hasActualRoomData) View.VISIBLE else View.GONE

        // Room ID copy functionality
        holder.tvRoomId.setOnClickListener {
            if (hasActualRoomData) {
                copyToClipboard(holder.itemView.context, "Room ID", match.roomId)
                showCopySuccess(holder.tvRoomId, holder.iconSuccess1)
            } else {
                Toast.makeText(holder.itemView.context, "Room ID will be available when match starts", Toast.LENGTH_SHORT).show()
            }
        }

        // Room Password copy functionality
        holder.tvRoomPassword.setOnClickListener {
            if (hasActualRoomData) {
                copyToClipboard(holder.itemView.context, "Room Password", match.roomPassword)
                showCopySuccess(holder.tvRoomPassword, holder.iconSuccess2)
            } else {
                Toast.makeText(holder.itemView.context, "Room Password will be available when match starts", Toast.LENGTH_SHORT).show()
            }
        }

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

    // Clipboard mein copy karen
    private fun copyToClipboard(context: Context, label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Silent error — clipboard service na mile toh ignore
        }
    }

    // Copy success animation — text hide, icon show, 2 sec baad wapas
    private fun showCopySuccess(textView: TextView, successIcon: ImageView) {
        textView.visibility = View.GONE
        successIcon.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            textView.visibility = View.VISIBLE
            successIcon.visibility = View.GONE
        }, 2000)
    }

    // YouTube video open karega — browser ya YouTube app
    private fun openYouTubeVideo(context: Context, videoUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Failed to open video — silently ignore
        }
    }
}