package com.edm.fire

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MatchDetailFragment : Fragment() {

    companion object {
        private const val TAG = "MatchDetailFragment"

        fun newInstance(
            tournamentId: String,
            tournamentType: String,
            databaseUrl: String,
            metaTitle: String,
            metaMap: String,
            metaMode: String,
            metaType: String,
            metaDateTime: String,
            metaPrizePool: Int,
            metaJoiningFee: Int,
            metaPerKill: Int,
            metaSlotNumbers: Int,
            metaJoinedCount: Int,
            metaBannerUrl: String,
            metaStatus: String
        ): MatchDetailFragment {
            return MatchDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("TOURNAMENT_ID", tournamentId)
                    putString("TOURNAMENT_TYPE", tournamentType)
                    putString("DATABASE_URL", databaseUrl)
                    putString("META_TITLE", metaTitle)
                    putString("META_MAP", metaMap)
                    putString("META_MODE", metaMode)
                    putString("META_TYPE", metaType)
                    putString("META_DATETIME", metaDateTime)
                    putInt("META_PRIZEPOOL", metaPrizePool)
                    putInt("META_JOININGFEE", metaJoiningFee)
                    putInt("META_PERKILL", metaPerKill)
                    putInt("META_SLOTNUMBERS", metaSlotNumbers)
                    putInt("META_JOINEDCOUNT", metaJoinedCount)
                    putString("META_BANNERURL", metaBannerUrl)
                    putString("META_STATUS", metaStatus)
                }
            }
        }
    }

    private var tvTitle: TextView? = null
    private var tvMap: TextView? = null
    private var tvMode: TextView? = null
    private var tvDateTime: TextView? = null
    private var tvPricePool: TextView? = null
    private var tvEntryFee: TextView? = null
    private var tvPerKill: TextView? = null
    private var tvDescription: TextView? = null
    private var tvProgressText: TextView? = null
    private var progressFill: View? = null
    private var progressBarContainer: View? = null
    private var progressBar: ProgressBar? = null
    private var tvTournamentId: TextView? = null
    private var tvJoinedCount: TextView? = null
    private var tvSpotsLeft: TextView? = null
    private var tvFillPercentage: TextView? = null
    private var tvStatusMessage: TextView? = null

    private var tournamentId: String = ""
    private var tournamentType: String = ""
    private var databaseUrl: String = ""
    private var currentJoinedCount: Int = 0
    private var currentTotalSlots: Int = 0
    private var currentProgressWidth: Int = 0
    private var isAnimationRunning: Boolean = false

    private val client = OkHttpClient()
    private val handler = Handler()
    private var countRunnable: Runnable? = null

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_match_detail, container, false)
        initViews(view)
        Log.d(TAG, "========== onCreateView ==========")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "========== onViewCreated START ==========")
        loadDataFromArguments()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isAnimationRunning = false
        countRunnable?.let { handler.removeCallbacks(it) }
        countRunnable = null
        Log.d(TAG, "========== onDestroyView ==========")
    }

    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tvTitle)
        tvMap = view.findViewById(R.id.tvMap)
        tvMode = view.findViewById(R.id.tvMode)
        tvDateTime = view.findViewById(R.id.tvDateTime)
        tvPricePool = view.findViewById(R.id.tvPricePool)
        tvEntryFee = view.findViewById(R.id.tvEntryFee)
        tvPerKill = view.findViewById(R.id.tvPerKill)
        tvDescription = view.findViewById(R.id.tvDescription)
        tvProgressText = view.findViewById(R.id.tvProgressText)
        progressFill = view.findViewById(R.id.progressFill)
        progressBarContainer = view.findViewById(R.id.progressBarContainer)
        progressBar = view.findViewById(R.id.progressBar)
        tvTournamentId = view.findViewById(R.id.tv_tournament_id)
        tvJoinedCount = view.findViewById(R.id.tvJoinedCount)
        tvSpotsLeft = view.findViewById(R.id.tvSpotsLeft)
        tvFillPercentage = view.findViewById(R.id.tvFillPercentage)
        tvStatusMessage = view.findViewById(R.id.tvStatusMessage)

        Log.d(TAG, "initViews - tvTitle: ${tvTitle != null}, tvMap: ${tvMap != null}, tvTournamentId: ${tvTournamentId != null}")
    }

    private fun loadDataFromArguments() {
        val args = arguments ?: run {
            Log.e(TAG, "❌ Arguments are NULL!")
            return
        }

        tournamentId = args.getString("TOURNAMENT_ID", "")
        tournamentType = args.getString("TOURNAMENT_TYPE", "BattleRoyal")
        databaseUrl = args.getString("DATABASE_URL", "")

        val title = args.getString("META_TITLE", "No Title")
        val map = args.getString("META_MAP", "Unknown")
        val mode = args.getString("META_MODE", "Unknown")
        val type = args.getString("META_TYPE", "Solo")
        val dateTime = args.getString("META_DATETIME", "Not Set")

        // These values are in PAISA from database
        val prizePoolInPaisa = args.getInt("META_PRIZEPOOL", 0)
        val joiningFeeInPaisa = args.getInt("META_JOININGFEE", 0)
        val perKillInPaisa = args.getInt("META_PERKILL", 0)

        currentTotalSlots = args.getInt("META_SLOTNUMBERS", 48)
        currentJoinedCount = args.getInt("META_JOINEDCOUNT", 0)

        Log.d(TAG, "=========================================")
        Log.d(TAG, "📥 DATA FROM ARGUMENTS (Intent se):")
        Log.d(TAG, "   tournamentId: $tournamentId")
        Log.d(TAG, "   tournamentType: $tournamentType")
        Log.d(TAG, "   databaseUrl: $databaseUrl")
        Log.d(TAG, "   title: $title")
        Log.d(TAG, "   map: $map")
        Log.d(TAG, "   mode: $mode")
        Log.d(TAG, "   type: $type")
        Log.d(TAG, "   dateTime: $dateTime")
        Log.d(TAG, "   prizePool (paisa): $prizePoolInPaisa")
        Log.d(TAG, "   joiningFee (paisa): $joiningFeeInPaisa")
        Log.d(TAG, "   perKill (paisa): $perKillInPaisa")
        Log.d(TAG, "   slotNumbers: $currentTotalSlots")
        Log.d(TAG, "   joinedCount: $currentJoinedCount")
        Log.d(TAG, "=========================================")

        // ✅ Convert paisa to coins with decimal support
        val prizePoolInCoins = paisaToCoins(prizePoolInPaisa)
        val joiningFeeInCoins = paisaToCoins(joiningFeeInPaisa)
        val perKillInCoins = paisaToCoins(perKillInPaisa)

        Log.d(TAG, "💰 Converted values:")
        Log.d(TAG, "   prizePool: $prizePoolInPaisa paisa → ${formatCoins(prizePoolInCoins)} Coins")
        Log.d(TAG, "   joiningFee: $joiningFeeInPaisa paisa → ${formatCoins(joiningFeeInCoins)} Coins")
        Log.d(TAG, "   perKill: $perKillInPaisa paisa → ${formatCoins(perKillInCoins)} Coins")

        // Update UI with converted values
        tvTitle?.text = title
        Log.d(TAG, "✅ tvTitle set to: $title")

        tvMap?.text = " $map"
        Log.d(TAG, "✅ tvMap set to: $map")

        tvMode?.text = " $mode"
        tvDateTime?.text = dateTime

        // ✅ UPDATED: Show converted coins with "Coins" word
        tvPricePool?.text = " ${formatCoins(prizePoolInCoins)} Coins"
        tvEntryFee?.text = " ${formatCoins(joiningFeeInCoins)} Coins"
        tvPerKill?.text = "  ${formatCoins(perKillInCoins)} Coins"

        Log.d(TAG, "✅ tvPricePool set to: ${formatCoins(prizePoolInCoins)} Coins")
        Log.d(TAG, "✅ tvEntryFee set to: ${formatCoins(joiningFeeInCoins)} Coins")
        Log.d(TAG, "✅ tvPerKill set to: ${formatCoins(perKillInCoins)} Coins")

        // 🔥 FIX: Tournament ID show karo
        tvTournamentId?.text = tournamentId
        Log.d(TAG, "✅ tvTournamentId set to: $tournamentId")

        updateProgressStats(currentJoinedCount, currentTotalSlots, false)
        fetchDescription()
        startRealtimeUpdates()
    }

    private fun fetchDescription() {
        if (tournamentId.isEmpty() || databaseUrl.isEmpty()) {
            Log.e(TAG, "❌ Cannot fetch description: tournamentId or databaseUrl empty")
            return
        }

        val url = "$databaseUrl/Tournaments/TournamentDetails/$tournamentType/$tournamentId/Description.json"
        Log.d(TAG, "📡 Fetching Description from: $url")

        UniversalReader.readJson(
            url = url,
            onResult = { description ->
                activity?.runOnUiThread {
                    val text = description.trim('"')
                    val size = description.toByteArray().size
                    Log.d(TAG, "✅ Description fetched! Size: $size bytes")
                    Log.d(TAG, "   Description text: ${text.take(100)}...")

                    if (text.isNotEmpty()) {
                        tvDescription?.text = text
                        Log.d(TAG, "✅ tvDescription updated")
                    } else {
                        tvDescription?.text = "No description available"
                        Log.d(TAG, "⚠️ Description empty, set default text")
                    }
                }
            },
            onError = { error ->
                activity?.runOnUiThread {
                    Log.e(TAG, "❌ Failed to fetch description: $error")
                    tvDescription?.text = "No description available"
                }
            }
        )
    }

    private fun startRealtimeUpdates() {
        val countUrl = "$databaseUrl/Tournaments/TournamentMeta/$tournamentType/$tournamentId/JoinedPlayersCount.json"
        val slotsUrl = "$databaseUrl/Tournaments/TournamentMeta/$tournamentType/$tournamentId/SlotNumbers.json"

        Log.d(TAG, "🔄 Starting realtime updates every 30 seconds")
        Log.d(TAG, "   Count URL: $countUrl")
        Log.d(TAG, "   Slots URL: $slotsUrl")

        countRunnable = Runnable {
            if (!isAdded) return@Runnable

            val countRequest = Request.Builder().url(countUrl).get().build()
            client.newCall(countRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "❌ Count fetch failed: ${e.message}")
                    handler.postDelayed(countRunnable!!, 30000)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val countStr = response.body?.string() ?: "0"
                    val newCount = countStr.trim('"').toIntOrNull() ?: 0
                    val countSize = countStr.toByteArray().size
                    Log.d(TAG, "📊 Count fetched: $newCount (${countSize} bytes)")

                    val slotsRequest = Request.Builder().url(slotsUrl).get().build()
                    client.newCall(slotsRequest).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: IOException) {
                            Log.e(TAG, "❌ Slots fetch failed: ${e.message}")
                            activity?.runOnUiThread {
                                if (currentJoinedCount != newCount) {
                                    currentJoinedCount = newCount
                                    updateProgressStats(currentJoinedCount, currentTotalSlots, true)
                                }
                            }
                            handler.postDelayed(countRunnable!!, 30000)
                        }
                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            val slotsStr = response.body?.string() ?: "$currentTotalSlots"
                            val newSlots = slotsStr.trim('"').toIntOrNull() ?: currentTotalSlots
                            val slotsSize = slotsStr.toByteArray().size
                            Log.d(TAG, "📊 Slots fetched: $newSlots (${slotsSize} bytes)")

                            activity?.runOnUiThread {
                                if (currentJoinedCount != newCount || currentTotalSlots != newSlots) {
                                    Log.d(TAG, "🔄 Progress updated: joined $currentJoinedCount -> $newCount, slots $currentTotalSlots -> $newSlots")
                                    currentJoinedCount = newCount
                                    currentTotalSlots = newSlots
                                    updateProgressStats(currentJoinedCount, currentTotalSlots, true)
                                } else {
                                    Log.d(TAG, "⏸️ No change in progress stats")
                                }
                            }
                            handler.postDelayed(countRunnable!!, 30000)
                        }
                    })
                }
            })
        }
        handler.postDelayed(countRunnable!!, 30000)
    }

    private fun updateProgressStats(joinedCount: Int, totalSlots: Int, animate: Boolean = true) {
        val spotsLeft = totalSlots - joinedCount
        val progressText = "$joinedCount Joined / $spotsLeft Spots Left"

        Log.d(TAG, "📊 updateProgressStats called:")
        Log.d(TAG, "   joinedCount: $joinedCount")
        Log.d(TAG, "   totalSlots: $totalSlots")
        Log.d(TAG, "   spotsLeft: $spotsLeft")
        Log.d(TAG, "   progressText: $progressText")

        tvProgressText?.text = progressText
        tvJoinedCount?.text = "👥 Joined: $joinedCount"
        tvSpotsLeft?.text = "🎯 Spots Left: $spotsLeft"

        val percentage = if (totalSlots > 0) (joinedCount.toFloat() / totalSlots.toFloat() * 100).toInt() else 0
        tvFillPercentage?.text = "📈 ${percentage}% Filled"

        Log.d(TAG, "   percentage: $percentage%")

        tvStatusMessage?.let { msg ->
            when {
                spotsLeft == 0 -> {
                    msg.text = "🔥 TOURNAMENT FULL! 🔥"
                    msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                }
                spotsLeft <= 5 -> {
                    msg.text = "⚡ Only $spotsLeft spots left! Join NOW! ⚡"
                    msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                }
                percentage >= 80 -> {
                    msg.text = "🚀 Filling fast! ${totalSlots - joinedCount} spots remaining!"
                    msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
                }
                percentage >= 50 -> {
                    msg.text = "⚡ Good progress! ${totalSlots - joinedCount} spots available!"
                    msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                }
                else -> {
                    msg.text = "🎯 ${totalSlots - joinedCount} spots available. Join now!"
                    msg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light))
                }
            }
            Log.d(TAG, "   statusMessage: ${msg.text}")
        }

        updateProgressBar(joinedCount, totalSlots, animate)
    }

    private fun updateProgressBar(joinedCount: Int, totalSlots: Int, animate: Boolean) {
        val progress = if (totalSlots > 0) (joinedCount.toFloat() / totalSlots.toFloat()).coerceIn(0f, 1f) else 0f
        progressFill?.post {
            val maxWidth = progressBarContainer?.width ?: 0
            val targetWidth = (maxWidth * progress).toInt()
            Log.d(TAG, "📊 Progress bar: maxWidth=$maxWidth, targetWidth=$targetWidth, animate=$animate")
            if (maxWidth > 0) {
                if (animate && targetWidth != currentProgressWidth) {
                    animateProgressBar(targetWidth)
                } else {
                    setProgressBarWidth(targetWidth)
                }
            }
        }
    }

    private fun animateProgressBar(targetWidth: Int) {
        if (isAnimationRunning) return
        isAnimationRunning = true
        val animation = android.animation.ValueAnimator.ofInt(currentProgressWidth, targetWidth).apply {
            duration = 500
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { setProgressBarWidth(it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimationRunning = false
                    currentProgressWidth = targetWidth
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    isAnimationRunning = false
                }
            })
        }
        animation.start()
    }

    private fun setProgressBarWidth(width: Int) {
        progressFill?.layoutParams = progressFill?.layoutParams?.apply { this.width = width }
        progressFill?.requestLayout()
        currentProgressWidth = width
    }
}