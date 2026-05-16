package com.edm.fire

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.edm.fire.databinding.ActivityMatchDetailBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class MatchDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchDetailBinding
    private var tournamentId: String = ""
    private var tournamentType: String = ""
    private var firebaseDatabaseUrl: String = ""

    // intent se milta hai meta data
    private var metaTitle: String = ""
    private var metaMap: String = ""
    private var metaMode: String = ""
    private var metaType: String = ""
    private var metaDateTime: String = ""
    private var metaPrizePool: Int = 0
    private var metaJoiningFee: Int = 0
    private var metaPerKill: Int = 0
    private var metaSlotNumbers: Int = 48
    private var metaJoinedCount: Int = 0
    private var metaBannerUrl: String = ""
    private var metaStatus: String = ""

    // joined status — Activity ne check kiya, fragments share karenge
    var isCurrentUserJoined: Boolean = false
        private set
    var firestoreCheckDone: Boolean = false
        private set
    private val joinedStatusListeners = mutableListOf<(Boolean) -> Unit>()

    // ChatFragment listener register karo — ek hi Firestore read dono use kare
    fun addJoinedStatusListener(listener: (Boolean) -> Unit) {
        if (firestoreCheckDone) {
            // already check ho chuki hai, turant result do
            listener(isCurrentUserJoined)
        } else {
            // abhi pending hai, store karlo — result aaye to notify karenge
            joinedStatusListeners.add(listener)
        }
    }

    private val selectSlotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        getIntentData()
        setupToolbar()
        loadTournamentBanner()

        // database URL Remote Config se fetch karo
        fetchDatabaseUrlAndSetup()
    }

    override fun onDestroy() {
        super.onDestroy()
        joinedStatusListeners.clear()
    }

    private fun getIntentData() {
        tournamentId = intent.getStringExtra("TOURNAMENT_ID") ?: ""
        tournamentType = intent.getStringExtra("TOURNAMENT_TYPE") ?: "BattleRoyal"

        // meta data intent se milta hai
        metaTitle = intent.getStringExtra("TOURNAMENT_TITLE") ?: "No Title"
        metaMap = intent.getStringExtra("TOURNAMENT_MAP") ?: "Unknown"
        metaMode = intent.getStringExtra("TOURNAMENT_MODE") ?: "Unknown"
        metaType = intent.getStringExtra("TOURNAMENT_TYPE_NAME") ?: "Solo"
        metaDateTime = intent.getStringExtra("TOURNAMENT_DATETIME") ?: "Not Set"
        metaPrizePool = intent.getIntExtra("TOURNAMENT_PRIZEPOOL", 0)
        metaJoiningFee = intent.getIntExtra("TOURNAMENT_JOININGFEE", 0)
        metaPerKill = intent.getIntExtra("TOURNAMENT_PERKILL", 0)
        metaSlotNumbers = intent.getIntExtra("TOURNAMENT_SLOTNUMBERS", 48)
        metaJoinedCount = intent.getIntExtra("TOURNAMENT_JOINEDCOUNT", 0)
        metaBannerUrl = intent.getStringExtra("TOURNAMENT_BANNERURL") ?: ""
        metaStatus = intent.getStringExtra("TOURNAMENT_STATUS") ?: "Upcoming"

        Log.d("MatchDetailActivity", "Intent data received - tournamentId: $tournamentId, type: $tournamentType")
    }

    private fun fetchDatabaseUrlAndSetup() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            firebaseDatabaseUrl = if (task.isSuccessful) {
                remoteConfig.getString("FirebaseDatabase_url")
            } else {
                ""
            }
            Log.d("MatchDetailActivity", "Database URL fetched: $firebaseDatabaseUrl")

            // intent data se UI setup karo
            setupViewPager()
            setupJoinButton()
            updateButtonState(metaStatus, metaJoinedCount, metaSlotNumbers)
            // Firestore se joined check karo — fragments bhi is result ko share karenge
            checkIfUserJoined()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
        binding.toolbar.title = when (tournamentType) {
            "FreeTournaments" -> "Free Tournament Details"
            "BattleRoyal" -> "Battle Royal Details"
            "ClashSquad" -> "Clash Squad Details"
            "LoneWolf" -> "Lone Wolf Details"
            else -> "Tournament Details"
        }
    }

    private fun loadTournamentBanner() {
        if (tournamentId.isEmpty() || metaBannerUrl.isEmpty()) {
            binding.ivTournamentBanner.setImageResource(R.drawable.coming_soon)
            return
        }

        Glide.with(this)
            .load(metaBannerUrl)
            .placeholder(R.drawable.coming_soon)
            .error(R.drawable.coming_soon)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivTournamentBanner)
    }

    private fun setupViewPager() {
        Log.d("MatchDetailActivity", "setupViewPager - databaseUrl: $firebaseDatabaseUrl")

        val adapter = MatchDetailPagerAdapter(
            this,
            tournamentId,
            tournamentType,
            firebaseDatabaseUrl,
            metaTitle, metaMap, metaMode, metaType, metaDateTime,
            metaPrizePool, metaJoiningFee, metaPerKill,
            metaSlotNumbers, metaJoinedCount, metaBannerUrl, metaStatus
        )
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Details"
                1 -> "Players"
                2 -> "Chat"
                else -> "Tab $position"
            }
        }.attach()
    }

    private fun setupJoinButton() {
        binding.btnJoinNow.setOnClickListener {
            val intent = Intent(this, SelectSlotActivity::class.java).apply {
                putExtra("TOURNAMENT_TYPE", tournamentType)
                putExtra("TOURNAMENT_ID", tournamentId)
            }
            selectSlotLauncher.launch(intent)
        }
    }

    private fun updateButtonState(status: String, joinedCount: Int, maxSlots: Int) {
        val isFull = joinedCount >= maxSlots
        binding.btnJoinNow.apply {
            when {
                isFull -> {
                    text = "TOURNAMENT FULL"
                    isEnabled = false
                    alpha = 0.7f
                }
                status.equals("UPCOMING", ignoreCase = true) -> {
                    text = "JOIN NOW"
                    isEnabled = true
                    alpha = 1.0f
                }
                status.equals("ONGOING", ignoreCase = true) -> {
                    text = "LIVE - Watch Now"
                    isEnabled = true
                    alpha = 1.0f
                }
                status.equals("COMPLETED", ignoreCase = true) -> {
                    text = "TOURNAMENT ENDED"
                    isEnabled = false
                    alpha = 0.7f
                }
                else -> {
                    text = "JOIN NOW"
                    isEnabled = true
                    alpha = 1.0f
                }
            }
        }
    }

    // Firestore me JoinedMatches check — fragments bhi is result ko share karenge
    private fun checkIfUserJoined() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrEmpty() || tournamentId.isEmpty()) {
            firestoreCheckDone = true
            isCurrentUserJoined = false
            joinedStatusListeners.forEach { it(false) }
            joinedStatusListeners.clear()
            return
        }

        Log.d("MatchDetailActivity", "🔍 Checking joined status in Firestore: Users/$userId/JoinedMatches/$tournamentId")

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(userId)
            .collection("JoinedMatches")
            .document(tournamentId)
            .get()
            .addOnSuccessListener { docSnapshot ->
                firestoreCheckDone = true
                isCurrentUserJoined = docSnapshot.exists()

                // sabhi listeners ko notify karo (ChatFragment etc.)
                joinedStatusListeners.forEach { it(isCurrentUserJoined) }
                joinedStatusListeners.clear()

                if (isCurrentUserJoined) {
                    val slotNumber = docSnapshot.getLong("slotNumber")?.toInt() ?: 0
                    Log.d("MatchDetailActivity", "✅ User already joined at slot: $slotNumber")
                    runOnUiThread { setUserAlreadyJoinedState(slotNumber) }
                } else {
                    Log.d("MatchDetailActivity", "✅ User not joined yet")
                }
            }
            .addOnFailureListener { error ->
                Log.e("MatchDetailActivity", "❌ Firestore check failed: ${error.message}")
                firestoreCheckDone = true
                isCurrentUserJoined = false
                joinedStatusListeners.forEach { it(false) }
                joinedStatusListeners.clear()
            }
    }

    private fun setUserAlreadyJoinedState(position: Int) {
        binding.btnJoinNow.text = "ALREADY JOINED - Seat #$position"
        binding.btnJoinNow.isEnabled = false
        binding.btnJoinNow.alpha = 0.7f
    }

    private fun refreshData() {
        // reset karo — naya check hoga
        firestoreCheckDone = false
        joinedStatusListeners.clear()
        updateButtonState(metaStatus, metaJoinedCount, metaSlotNumbers)
        setupViewPager()
        // naya check karo — fresh data
        checkIfUserJoined()
    }
}