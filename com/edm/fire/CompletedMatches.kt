package com.edm.fire


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
import java.text.SimpleDateFormat
import java.util.Locale

class CompletedMatches : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var tvNoMatches: android.widget.TextView
    private lateinit var remoteConfig: FirebaseRemoteConfig

    // REST API ke liye baseUrl — RemoteConfig se aayega, koi hardcoded URL nahi
    private var baseUrl = ""

    // Auth aur Firestore sirf JoinedMatches read ke liye — lightweight ~50 bytes per doc
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Saare completed matches — fetch ke baad yahan store hoga (sorted, newest first)
    private val allCompletedMatches = mutableListOf<CompletedMatch>()

    // Displayed list — adapter ko yahi list milegi
    // Initially 5 dikhayenge, scroll pe 5 aur add karenge
    private val displayedMatches = mutableListOf<CompletedMatch>()

    private lateinit var adapter: CompletedMatchAdapter

    // Counter tracking — class level kyunki multiple async callbacks mein share hoga
    private var matchesProcessed = 0
    private var totalMatchesToProcess = 0

    // ══════════════════════════════════════════════
    // Pagination — Initially 5, scroll to load more
    // ══════════════════════════════════════════════
    private val INITIAL_LOAD = 5          // Pehle 5 dikhayenge
    private val LOAD_MORE_COUNT = 5       // Har scroll pe 5 aur dikhayenge
    private var isLoadingMore = false     // Double-load prevention
    private var allDataLoaded = false     // Saare items display ho chuke hain

    companion object {
        private const val FIREBASE_DATABASE_URL_KEY = "FirebaseDatabase_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_completed_matches)

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

        // Cache 0 — hamesha fresh URL fetch
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
                initializeActivity()
            }
            .addOnFailureListener {
                initializeActivity()
            }
    }

    // ══════════════════════════════════════════════
    // Activity Initialize
    // ══════════════════════════════════════════════

    private fun initializeActivity() {
        initViews()
        setupToolbar()
        setupRecyclerView()
        loadUserCompletedMatches()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.rvCompletedMatches)
        progressBar = findViewById(R.id.progressBar)
        tvNoMatches = findViewById(R.id.tvNoMatches)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { onBackPressed() }
        }
    }

    private fun setupToolbar() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { onBackPressed() }
            title = "My Completed Matches"
        }
    }

    private fun setupRecyclerView() {
        // Adapter ko displayedMatches pass karenge — yahi dikhenge
        adapter = CompletedMatchAdapter(displayedMatches)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Scroll listener — jab user bottom tak scroll kare tab more load karo
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)

                // Sirf neeche scroll karne pe trigger karo, upar scroll pe nahi
                if (dy <= 0) return

                // Saara data already display ho chuka hai
                if (allDataLoaded || isLoadingMore) return

                val layoutManager = rv.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItems = adapter.itemCount

                // Jab last item dikh jaye toh 5 aur load karo
                if (lastVisibleItem >= totalItems - 1) {
                    loadMore()
                }
            }
        })
    }

    // ══════════════════════════════════════════════
    // Pagination — Load More Logic
    // ══════════════════════════════════════════════

    /**
     * Pehle INITIAL_LOAD (5) items dikhayenge displayedMatches me
     * Scroll karne pe LOAD_MORE_COUNT (5) aur items add honge
     * Adapter displayedMatches ko directly read karta hai — simple!
     */
    private fun showInitialBatch() {
        val count = minOf(INITIAL_LOAD, allCompletedMatches.size)
        displayedMatches.clear()
        for (i in 0 until count) {
            displayedMatches.add(allCompletedMatches[i])
        }
        allDataLoaded = (count >= allCompletedMatches.size)
        adapter.notifyDataSetChanged()
    }

    /**
     * Scroll pe call hota hai — displayedMatches me 5 aur items add karo
     * Yeh method UI thread pe hi call hota hai (scroll listener = UI thread)
     * Koi extra runOnUiThread nahi chahiye!
     */
    private fun loadMore() {
        isLoadingMore = true

        val currentSize = displayedMatches.size
        val end = minOf(currentSize + LOAD_MORE_COUNT, allCompletedMatches.size)

        for (i in currentSize until end) {
            displayedMatches.add(allCompletedMatches[i])
        }

        // Check agar saara data display ho chuka hai
        allDataLoaded = (end >= allCompletedMatches.size)

        // Adapter ko notify — new items add hue
        adapter.notifyItemRangeInserted(currentSize, end - currentSize)

        isLoadingMore = false
    }

    // ══════════════════════════════════════════════
    // Main Data Loading — Firestore (JoinedMatches) + REST API (Meta)
    // ══════════════════════════════════════════════

    private fun loadUserCompletedMatches() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showNoMatchesMessage()
            return
        }

        progressBar.visibility = View.VISIBLE
        tvNoMatches.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // Step 1: Firestore se JoinedMatches read karen
        firestore.collection("Users")
            .document(currentUser.uid)
            .collection("JoinedMatches")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showNoMatchesMessage()
                    return@addOnSuccessListener
                }

                // Reset
                allCompletedMatches.clear()
                displayedMatches.clear()
                matchesProcessed = 0
                totalMatchesToProcess = documents.size()

                for (document in documents) {
                    val tournamentType = document.getString("tournamentType") ?: ""
                    val tournamentId = document.getString("tournamentId") ?: ""
                    val joinTime = document.getLong("joinTime") ?: 0
                    val slotNumber = document.getLong("slotNumber")?.toInt() ?: 0

                    if (tournamentType.isNotEmpty() && tournamentId.isNotEmpty()) {
                        // Step 2: SIRF EK REST call — Meta se SAB fields
                        fetchTournamentMeta(
                            tournamentType = tournamentType,
                            tournamentId = tournamentId,
                            joinTime = joinTime,
                            slotNumber = slotNumber
                        )
                    } else {
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
     * Sabhi matches fetch hone ke baad:
     * 1. Sort by DateTime (newest first)
     * 2. Show initial batch (5 items) — SIRF EK BAAR UI update!
     */
    private fun checkAllDone() {
        if (matchesProcessed < totalMatchesToProcess) return

        runOnUiThread {
            progressBar.visibility = View.GONE

            if (allCompletedMatches.isEmpty()) {
                showNoMatchesMessage()
                return@runOnUiThread
            }

            // Sort by DateTime — newest first (latest completed matches upar)
            sortMatchesByDate()

            // Display ready — sirf EK baar notifyDataSetChanged call hoga
            showMatchesList()
            showInitialBatch()
        }
    }

    /**
     * DateTime se sort — newest first (latest completed matches upar)
     * Multiple format try karega kyunki DB me different formats ho sakte hain
     */
    private fun sortMatchesByDate() {
        allCompletedMatches.sortWith { a, b ->
            val dateA = parseDateTime(a.dateTime)
            val dateB = parseDateTime(b.dateTime)
            // Newest first — dateB - dateA (descending)
            dateB.compareTo(dateA)
        }
    }

    /**
     * DateTime string ko millis me convert karega
     * DB me multiple formats hain: "2026/03/09 10:00pm", "2026/03/11 12:30 PM" etc.
     * Sabko handle karega — space, no-space, uppercase, lowercase sab
     */
    private fun parseDateTime(dateTime: String): Long {
        if (dateTime.isEmpty()) return 0L

        // Step 1: PM/PM ko standardize — space add karo, uppercase karo
        val normalized = dateTime
            .replace("PM", " PM", ignoreCase = true)
            .replace("AM", " AM", ignoreCase = true)
            // Double space remove karo (agar pehle se space tha)
            .replace("  ", " ")
            .trim()

        // Step 2: Multiple formats try karo
        val formats = arrayOf(
            SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.ENGLISH),
            SimpleDateFormat("yyyy/MM/dd hh:mma", Locale.ENGLISH)
        )

        for (format in formats) {
            try {
                return format.parse(normalized)?.time ?: 0L
            } catch (_: Exception) {
                // Try next format
            }
        }
        return 0L
    }

    // ══════════════════════════════════════════════
    // REST API — Tournament Meta Fetch (SINGLE CALL ONLY)
    // ══════════════════════════════════════════════

    private fun fetchTournamentMeta(
        tournamentType: String,
        tournamentId: String,
        joinTime: Long,
        slotNumber: Int
    ) {
        if (baseUrl.isEmpty()) {
            matchesProcessed++
            checkAllDone()
            return
        }

        val url = "$baseUrl/Tournaments/TournamentMeta/$tournamentType/$tournamentId.json"

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                try {
                    if (jsonString == null || jsonString == "null" || jsonString.trim().isEmpty()) {
                        matchesProcessed++
                        checkAllDone()
                        return@readJson
                    }

                    val json = JSONObject(jsonString)
                    val status = json.optString("Status", "")

                    // FILTER: Sirf "Completed" status dikhayenge
                    if (!status.equals("Completed", ignoreCase = true)) {
                        matchesProcessed++
                        checkAllDone()
                        return@readJson
                    }

                    // ── SAB Meta fields ──
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

                    // VideoUrl Meta mein nahi hai — TournamentDetails mein hai
                    val videoUrl = ""

                    val completedMatch = CompletedMatch(
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
                        videoUrl = videoUrl,
                        perKill = perKill,
                        slotNumbers = slotNumbers,
                        joinedCount = joinedCount
                    )

                    allCompletedMatches.add(completedMatch)
                } catch (e: Exception) {
                    // JSON parse error — silently skip
                }
                matchesProcessed++
                checkAllDone()
            },
            onError = { error ->
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