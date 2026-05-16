package com.edm.fire

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton

open class TournamentAdapter(
    private val currentUserId: String,
    private val fragmentType: String,
    private val isFreeCategory: Boolean = false,
    private val onItemClick: (Tournament, String) -> Unit
) : ListAdapter<Tournament, TournamentAdapter.TournamentViewHolder>(TournamentDiffCallback()) {

    private var fragmentRef: Fragment? = null

    private fun paisaToCoins(paisa: Int): Double {
        return paisa / 100.0
    }

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

    inner class TournamentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bannerImage: ImageView = itemView.findViewById(R.id.ivBanner)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val map: TextView = itemView.findViewById(R.id.tvMap)
        val mode: TextView = itemView.findViewById(R.id.tvMode)
        val dateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val pricePool: TextView = itemView.findViewById(R.id.tvPricePool)
        val joinedPlayers: TextView = itemView.findViewById(R.id.tvJoinedPlayers)
        val perKill: TextView = itemView.findViewById(R.id.tvPerKill)
        val tvProgressText: TextView = itemView.findViewById(R.id.tvProgressText)
        val tvEntryFee: TextView = itemView.findViewById(R.id.tvEntryFee)
        val progressFill: View = itemView.findViewById(R.id.progressFill)
        val btnJoin: MaterialButton = itemView.findViewById(R.id.btnJoin)
        val progressBarContainer: LinearLayout = itemView.findViewById(R.id.progressBarContainer)
        val tvJoinStatus: TextView = itemView.findViewById(R.id.tvJoinStatus)
        val currentTournamentId: TextView = itemView.findViewById(R.id.current_tournament_id)
        val tournamentType: TextView = itemView.findViewById(R.id.tv_tournament_type)
    }

    fun attachFragment(fragment: Fragment) {
        fragmentRef = fragment
    }

    fun detachFragment() {
        fragmentRef = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TournamentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tournament, parent, false)
        return TournamentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TournamentViewHolder, position: Int) {
        // ⚡ ListAdapter se data getItem() ke through aata hai
        val tournament = getItem(position)
        val context = holder.itemView.context

        try {
            // ⚡ FIX: Programmatic size calculation hamesha ke liye hata diya gaya hai.
            // Glide ab sidha aapke XML me diye gaye original size ko follow karega.
            Glide.with(context)
                .load(tournament.BannerUrl)
                .placeholder(R.drawable.coming_soon)
                .error(R.drawable.coming_soon)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.bannerImage)

            holder.title.text = tournament.Title
            holder.map.text = "Map: ${tournament.Map}"
            holder.mode.text = "Mode: ${tournament.Mode}"
            holder.dateTime.text = tournament.DateTime
            holder.currentTournamentId.text = tournament.tournamentId
            holder.tournamentType.text = "Type: ${tournament.Type}"

            val prizePoolInCoins = paisaToCoins(tournament.PricePool)
            val perKillInCoins = paisaToCoins(tournament.PerKill)
            val joiningFeeInCoins = paisaToCoins(tournament.JoiningFee)

            holder.pricePool.text = "Prize: ${formatCoins(prizePoolInCoins)} Coins"
            holder.perKill.text = "Per Kill: ${formatCoins(perKillInCoins)} Coins"
            holder.tvEntryFee.text = "Entry: ${formatCoins(joiningFeeInCoins)} Coins"

            val totalSpots = tournament.SlotNumbers
            holder.joinedPlayers.text = "Joined: ${tournament.JoinedPlayersCount}/$totalSpots"

            val spotsLeft = totalSpots - tournament.JoinedPlayersCount
            holder.tvProgressText.text = "${tournament.JoinedPlayersCount} Joined / $spotsLeft Spots Left"

            val progressPercentage = if (totalSpots > 0) {
                (tournament.JoinedPlayersCount.toFloat() / totalSpots.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            holder.progressBarContainer.post {
                val maxWidth = holder.progressBarContainer.width
                val progressWidth = (maxWidth * progressPercentage).toInt()
                val layoutParams = holder.progressFill.layoutParams as LinearLayout.LayoutParams
                layoutParams.width = progressWidth
                holder.progressFill.layoutParams = layoutParams
                applySmoothShimmerEffect(holder.progressFill, context)
            }

            setupJoinStatusAndButton(holder, tournament, context, totalSpots)
            setupClickListeners(holder, tournament, context)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applySmoothShimmerEffect(view: View, context: Context) {
        try {
            view.background = ContextCompat.getDrawable(context, R.drawable.shimmer_gradient)
            val alphaAnimation = android.view.animation.AlphaAnimation(0.3f, 1.0f).apply {
                duration = 1500
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            view.startAnimation(alphaAnimation)
        } catch (e: Exception) { }
    }

    private fun setupJoinStatusAndButton(
        holder: TournamentViewHolder,
        tournament: Tournament,
        context: Context,
        totalSpots: Int
    ) {
        holder.tvJoinStatus.visibility = View.GONE
        holder.btnJoin.visibility = View.VISIBLE

        val hasUserJoined = tournament.JoinedPlayers.values.any { it.userId == currentUserId }
        val userSlot = tournament.JoinedPlayers.values.find { it.userId == currentUserId }?.PositionSeat

        when (fragmentType) {
            "upcoming" -> {
                if (hasUserJoined && currentUserId.isNotEmpty()) {
                    holder.tvJoinStatus.text = "Already Joined - Slot #${userSlot ?: "N/A"}"
                    holder.tvJoinStatus.visibility = View.VISIBLE
                    holder.btnJoin.text = "Manage Now"
                    holder.btnJoin.isEnabled = true
                    holder.btnJoin.alpha = 1.0f
                } else if (tournament.JoinedPlayersCount >= totalSpots) {
                    holder.btnJoin.text = "FULL"
                    holder.btnJoin.isEnabled = false
                    holder.btnJoin.alpha = 0.6f
                } else {
                    holder.btnJoin.text = "JOIN NOW"
                    holder.btnJoin.isEnabled = true
                    holder.btnJoin.alpha = 1.0f
                }
            }
            "ongoing" -> {
                holder.btnJoin.text = "LIVE"
                holder.btnJoin.isEnabled = true
                if (hasUserJoined && currentUserId.isNotEmpty()) {
                    holder.tvJoinStatus.text = "Playing - Slot #${userSlot ?: "N/A"}"
                    holder.tvJoinStatus.visibility = View.VISIBLE
                }
            }
            "past" -> {
                holder.btnJoin.text = "COMPLETED"
                holder.btnJoin.isEnabled = false
                holder.btnJoin.alpha = 0.6f
                if (hasUserJoined && currentUserId.isNotEmpty()) {
                    holder.tvJoinStatus.text = "Completed - Slot #${userSlot ?: "N/A"}"
                    holder.tvJoinStatus.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupClickListeners(holder: TournamentViewHolder, tournament: Tournament, context: Context) {
        val hasUserJoined = tournament.JoinedPlayers.any { it.value.userId == currentUserId }

        val targetDatabasePath = if (isFreeCategory) "FreeTournaments" else tournament.Mode

        holder.btnJoin.setOnClickListener {
            when (fragmentType) {
                "upcoming" -> {
                    if (currentUserId.isNotEmpty()) {
                        val intent = if (hasUserJoined) {
                            Intent(context, UpcomingActivity::class.java).apply {
                                putExtra("TOURNAMENT_ID", tournament.tournamentId)
                                putExtra("TOURNAMENT_TYPE", targetDatabasePath)
                            }
                        } else {
                            Intent(context, SelectSlotActivity::class.java).apply {
                                putExtra("TOURNAMENT_TYPE", targetDatabasePath)
                                putExtra("TOURNAMENT_ID", tournament.tournamentId)
                            }
                        }
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Please login to join", Toast.LENGTH_SHORT).show()
                    }
                }
                "ongoing" -> {
                    val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/live")).apply {
                        `package` = "com.google.android.youtube"
                    }
                    if (youtubeIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(youtubeIntent)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                    }
                }
                "past" -> {
                    Toast.makeText(context, "Tournament completed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(tournament, targetDatabasePath)
        }
        holder.bannerImage.setOnClickListener {
            onItemClick(tournament, targetDatabasePath)
        }
    }

    fun updateList(newList: List<Tournament>) {
        submitList(newList.toList())
    }

    fun cleanup() {
        detachFragment()
    }
}

// ⚡ DiffUtil standalone class
class TournamentDiffCallback : DiffUtil.ItemCallback<Tournament>() {
    override fun areItemsTheSame(oldItem: Tournament, newItem: Tournament): Boolean {
        return oldItem.tournamentId == newItem.tournamentId
    }

    override fun areContentsTheSame(oldItem: Tournament, newItem: Tournament): Boolean {
        return oldItem.JoinedPlayersCount == newItem.JoinedPlayersCount &&
                oldItem.Status == newItem.Status &&
                oldItem.Title == newItem.Title
    }
}