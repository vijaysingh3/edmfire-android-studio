package com.edm.fire

import UpcomingMatch
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.json.JSONObject

class UpcomingActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var tvNoMatches: android.widget.TextView
    private lateinit var remoteConfig: FirebaseRemoteConfig

    // REST API ke liye baseUrl — RemoteConfig se aayega, koi hardcoded URL nahi
    private var baseUrl = ""

    // Auth aur Firestore sirf JoinedMatches read ke liye — lightweight ~50 bytes per doc
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val upcomingMatchesList = mutableListOf<UpcomingMatch>()
    private lateinit var adapter: UpcomingMatchesAdapter

    // Counter tracking — class level kyunki multiple async callbacks mein share hoga
    private var matchesProcessed = 0
    private var totalMatchesToProcess = 0

    companion object {
        private const val FIREBASE_DATABASE_URL_KEY = "FirebaseDatabase_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_upcoming)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // RemoteConfig se fresh URL lenge — cache disabled
        initializeRemoteConfig()
    }

    // ══════════════════════════════════════════════
    // RemoteConfig Setup — Cache 0, hamesha fresh
    // ══════════════════════════════════════════════

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()

        // Cache 0 — hamesha fresh URL fetch, koi stale data nahi
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaultConfigMap = mutableMapOf<String, Any>()
        defaultConfigMap[FIREBASE_DATABASE_URL_KEY] = ""
        remoteConfig.setDefaultsAsync(defaultConfigMap)

        fetchRemoteConfig()
    }

    private fun fetchRemoteConfig() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    baseUrl = remoteConfig.getString(FIREBASE_DATABASE_URL_KEY)
                }
                // Chahe success ho ya fail, activity initialize karen
                initializeActivity()
            }
            .addOnFailureListener {
                initializeActivity()
            }
    }

    // ══════════════════════════════════════════════
    // Activity Initialize — Views, Toolbar, RecyclerView
    // ══════════════════════════════════════════════

    private fun initializeActivity() {
        initViews()
        setupToolbar()
        setupRecyclerView()
        loadUserUpcomingMatches()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.rvOnGoingMatches)
        progressBar = findViewById(R.id.progressBar)
        tvNoMatches = findViewById(R.id.tvNoMatches)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { onBackPressed() }
        }
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { onBackPressed() }
            title = "My Upcoming Matches"
        }
    }

    private fun setupRecyclerView() {
        adapter = UpcomingMatchesAdapter(upcomingMatchesList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    // ══════════════════════════════════════════════
    // Main Data Loading — Firestore (JoinedMatches) + REST API (Meta)
    // ══════════════════════════════════════════════

    private fun loadUserUpcomingMatches() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showNoMatchesMessage()
            return
        }

        progressBar.visibility = View.VISIBLE
        tvNoMatches.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // Step 1: Firestore se JoinedMatches read karen — user ne join kiye sab tournaments
        // Ye ~50 bytes per document hai, bahut lightweight
        // Har document mein: tournamentType, tournamentId, slotNumber, joinTime
        firestore.collection("Users")
            .document(currentUser.uid)
            .collection("JoinedMatches")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showNoMatchesMessage()
                    return@addOnSuccessListener
                }

                // List aur counter reset karen
                upcomingMatchesList.clear()
                matchesProcessed = 0
                totalMatchesToProcess = documents.size()

                for (document in documents) {
                    val tournamentType = document.getString("tournamentType") ?: ""
                    val tournamentId = document.getString("tournamentId") ?: ""
                    val joinTime = document.getLong("joinTime") ?: 0

                    // SlotNumber JoinedMatches document mein stored hai
                    // Backend ne join time pe ye field likha hoga
                    val slotNumber = document.getLong("slotNumber")?.toInt() ?: 0

                    if (tournamentType.isNotEmpty() && tournamentId.isNotEmpty()) {
                        // Step 2: SIRF EK REST call — Meta se SAB fields milega
                        // RoomID/RoomPassword bhi ab Meta mein hain, koi doosri call nahi!
                        fetchTournamentMeta(
                            tournamentType = tournamentType,
                            tournamentId = tournamentId,
                            joinTime = joinTime,
                            slotNumber = slotNumber
                        )
                    } else {
                        // Empty fields waala document — skip but counter badhao
                        matchesProcessed++
                        checkAllDone()
                    }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                showNoMatchesMessage()
            }
    }

    /**
     * Sabhi matches fetch hone ke baad final UI update
     * CRITICAL: OkHttp callback background thread se call hota hai — runOnUiThread mein wrap
     */
    private fun checkAllDone() {
        if (matchesProcessed < totalMatchesToProcess) return

        runOnUiThread {
            progressBar.visibility = View.GONE
            if (upcomingMatchesList.isEmpty()) {
                showNoMatchesMessage()
            } else {
                showMatchesList()
                adapter.notifyDataSetChanged()
            }
        }
    }

    // ══════════════════════════════════════════════
    // REST API — Tournament Meta Fetch (SINGLE CALL ONLY)
    // ══════════════════════════════════════════════

    /**
     * EK HI REST call mein SAB fields milenge — RoomID/RoomPassword bhi ab Meta mein hain!
     *
     * Path: Tournaments/TournamentMeta/{tournamentType}/{tournamentId}
     * Size: ~450 bytes per tournament
     *
     * Fields milenge: Title, DateTime, JoiningFee, PricePool, PerKill, SlotNumbers,
     *                 JoinedPlayersCount, BannerUrl, Map, Mode, Type, Status,
     *                 RoomID, RoomPassword, CreatedAt — SAB ek jagah!
     *
     * NOTE: Pehle 2 REST calls lagti thi — Meta + Details (RoomID/Pass ke liye)
     *       Ab sirf 1 call — 98.5% waste eliminated!
     */
    private fun fetchTournamentMeta(
        tournamentType: String,
        tournamentId: String,
        joinTime: Long,
        slotNumber: Int
    ) {
        // Safety check — agar RemoteConfig se URL nahi aaya toh skip
        if (baseUrl.isEmpty()) {
            matchesProcessed++
            checkAllDone()
            return
        }

        // EK HI CALL — sab kuch yahan se milega
        val url = "$baseUrl/Tournaments/TournamentMeta/$tournamentType/$tournamentId.json"

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                try {
                    // Tournament exist nahi karta ya deleted hai
                    if (jsonString == null || jsonString == "null" || jsonString.trim().isEmpty()) {
                        matchesProcessed++
                        checkAllDone()
                        return@readJson
                    }

                    val json = JSONObject(jsonString)
                    val status = json.optString("Status", "")

                    // FILTER: Sirf "Upcoming" status dikhayenge
                    // Ongoing aur Completed alag tabs me dikhenge
                    if (!status.equals("Upcoming", ignoreCase = true)) {
                        matchesProcessed++
                        checkAllDone()
                        return@readJson
                    }

                    // ── SAB Meta fields — ek hi JSON se ──
                    val title = json.optString("Title", "Tournament")
                    val dateTime = json.optString("DateTime", "")
                    val entryFee = getSafeInt(json, "JoiningFee", 0)

                    // ✅ FIX 1: "PrizePool" → "PricePool" (DB mein "PricePool" hai)
                    val prizePool = getSafeInt(json, "PricePool", 0)
                    val perKill = getSafeInt(json, "PerKill", 0)
                    val slotNumbers = getSafeInt(json, "SlotNumbers", 0)
                    val joinedCount = getSafeInt(json, "JoinedPlayersCount", 0)
                    val bannerUrl = json.optString("BannerUrl", "")
                    val map = json.optString("Map", "")
                    val mode = json.optString("Mode", "")
                    val type = json.optString("Type", "")

                    // RoomID aur RoomPassword — ab Meta mein hain!
                    // Agar empty hain toh "Coming Soon...." dikhayenge (room abhi create nahi hua)
                    val fetchedRoomId = json.optString("RoomID", "")
                    val fetchedRoomPass = json.optString("RoomPassword", "")

                    val roomId = if (fetchedRoomId.isNotEmpty()) fetchedRoomId else "Coming Soon...."
                    val roomPassword = if (fetchedRoomPass.isNotEmpty()) fetchedRoomPass else "Coming Soon...."

                    val upcomingMatch = UpcomingMatch(
                        tournamentType = tournamentType,
                        tournamentId = tournamentId,
                        title = title,
                        dateTime = dateTime,
                        entryFee = entryFee,
                        pricePool = prizePool,
                        bannerUrl = bannerUrl,
                        map = map,
                        mode = mode,
                        type = type,
                        userSlotNumber = slotNumber,
                        joinTime = joinTime,
                        status = status,
                        roomId = roomId,
                        roomPassword = roomPassword,
                        perKill = perKill,
                        slotNumbers = slotNumbers,
                        joinedCount = joinedCount
                    )

                    upcomingMatchesList.add(upcomingMatch)
                } catch (e: Exception) {
                    // JSON parse error — silently skip, counter badhao
                }
                matchesProcessed++
                checkAllDone()
            },
            onError = { error ->
                // Network error ya server error — silently skip, counter badhao
                matchesProcessed++
                checkAllDone()
            }
        )
    }

    // ✅ FIX 2: Safe int parser — string/empty/null value se crash nahi hoga
    private fun getSafeInt(json: JSONObject, key: String, default: Int = 0): Int {
        return try {
            when {
                !json.has(key) -> default
                json.isNull(key) -> default
                else -> {
                    val value = json.get(key)
                    when (value) {
                        is Int -> value
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull() ?: default
                        else -> default
                    }
                }
            }
        } catch (e: Exception) {
            default
        }
    }

    // ══════════════════════════════════════════════
    // UI Helpers
    // ══════════════════════════════════════════════

    private fun showNoMatchesMessage() {
        progressBar.visibility = View.GONE
        tvNoMatches.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun showMatchesList() {
        progressBar.visibility = View.GONE
        tvNoMatches.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}