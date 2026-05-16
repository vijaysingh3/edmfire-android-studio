package com.edm.fire

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class Activity_LeaderBoard : AppCompatActivity() {

    // ─── Views ────────────────────────────────────────────────────────────────
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    // ✅ FIX 1: lateinit → nullable — "Unresolved reference" crash fix
    private var tvLoadMore: TextView? = null
    private var loadMoreProgress: ProgressBar? = null

    // ─── Adapter & Data ───────────────────────────────────────────────────────
    private lateinit var adapter: LeaderboardAdapter
    private val displayedList  = mutableListOf<LeaderboardModel>()
    private val fullCachedList = mutableListOf<LeaderboardModel>()

    // ─── Pagination ───────────────────────────────────────────────────────────
    private val PAGE_SIZE      = 15
    private val LOAD_MORE_SIZE = 10
    private var currentOffset  = 0
    private var isLoadingMore  = false
    private var allLoaded      = false

    // ─── Cache ────────────────────────────────────────────────────────────────
    private lateinit var prefs: SharedPreferences
    private val CACHE_KEY      = "leaderboard_cache"
    private val CACHE_TIME_KEY = "leaderboard_cache_time"
    private val TTL_MS         = 12 * 60 * 60 * 1000L  // 12 hours

    // ─── Fetch guard ──────────────────────────────────────────────────────────
    private var isFetching = false

    companion object {
        private const val TAG = "LeaderBoard"
    }

    // ══════════════════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_leader_board)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = getSharedPreferences("LeaderboardCache", Context.MODE_PRIVATE)

        initViews()
        setupRecyclerView()
        loadLeaderboard()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════════════════
    private fun initViews() {
        recyclerView     = findViewById(R.id.rvLeaderboard)
        progressBar      = findViewById(R.id.progressBarLeaderboard)
        tvEmpty          = findViewById(R.id.tvEmptyLeaderboard)

        // ✅ FIX 2: Safe nullable find — XML mein ho ya na ho, crash nahi
        tvLoadMore       = findViewById(R.id.tvLoadMore)
        loadMoreProgress = findViewById(R.id.loadMoreProgress)
    }

    private fun setupRecyclerView() {
        adapter = LeaderboardAdapter(displayedList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return  // Sirf neeche scroll par trigger

                val lm          = rv.layoutManager as LinearLayoutManager
                val lastVisible = lm.findLastVisibleItemPosition()
                val total       = adapter.itemCount

                if (!isLoadingMore && !allLoaded && lastVisible >= total - 3) {
                    loadNextPage()
                }
            }
        })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN ENTRY — Cache pehle check karo, Firebase tabhi jab zaroorat ho
    // ══════════════════════════════════════════════════════════════════════════
    private fun loadLeaderboard() {
        if (isFetching) return
        isFetching = true
        showProgress()

        // ✅ FIX 3: withContext hataya → simple Thread (no coroutine import needed)
        Thread {
            val cachedData = getCachedLeaderboard()
            runOnUiThread {
                if (cachedData != null) {
                    Log.d(TAG, "✅ Cache HIT — 0 Firebase reads | Items: ${cachedData.size}")
                    fullCachedList.clear()
                    fullCachedList.addAll(cachedData)
                    resetAndShowFirstPage()
                    hideProgress()
                    isFetching = false
                } else {
                    Log.d(TAG, "⚠️ Cache MISS — fetching from Firebase...")
                    fetchFromFirebase()
                }
            }
        }.start()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FIREBASE FETCH — Original callback style (jo pehle kaam kar raha tha)
    // ✅ FIX 5: "FirebaseDatabase_url" → "Leaderboard_url"
    // ══════════════════════════════════════════════════════════════════════════
    private fun fetchFromFirebase() {
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        )
        remoteConfig.setDefaultsAsync(mapOf("Leaderboard_url" to ""))

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                hideProgress()
                showEmpty("Config failed: ${task.exception?.message}")
                isFetching = false
                return@addOnCompleteListener
            }

            // ✅ New key: Leaderboard_url
            val dbUrl = remoteConfig.getString("Leaderboard_url")
            Log.d(TAG, "RemoteConfig OK | Leaderboard_url = '$dbUrl'")

            if (dbUrl.isEmpty()) {
                hideProgress()
                showEmpty("Database URL not found")
                isFetching = false
                return@addOnCompleteListener
            }

            val database = FirebaseDatabase.getInstance(dbUrl).reference
            fetchLeaderboardData(database)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA FETCH — Single .get() call, NO realtime listeners
    // ══════════════════════════════════════════════════════════════════════════
    private fun fetchLeaderboardData(database: DatabaseReference) {
        database.child("Leaderboard").get().addOnCompleteListener { task ->
            if (!task.isSuccessful || task.result == null) {
                Log.e(TAG, "Fetch error: ${task.exception?.message}")
                hideProgress()
                showEmpty("Error: ${task.exception?.message}")
                isFetching = false
                return@addOnCompleteListener
            }

            val snapshot = task.result!!

            if (!snapshot.exists()) {
                hideProgress()
                showEmpty("No leaderboard data found")
                isFetching = false
                return@addOnCompleteListener
            }

            val tempList = mutableListOf<LeaderboardModel>()

            for (userSnap in snapshot.children) {
                val userId = userSnap.key ?: continue
                tempList.add(
                    LeaderboardModel(
                        userId       = userId,
                        matchPlayed  = userSnap.child("MatchPlayed").getValue(Int::class.java) ?: 0,
                        winningCoins = userSnap.child("WinningCoins").getValue(Int::class.java) ?: 0,
                        winningCount = userSnap.child("WinningCount").getValue(Int::class.java) ?: 0,
                        inGameName   = userSnap.child("InGameName").getValue(String::class.java) ?: "",
                        inGameLevel  = userSnap.child("InGameLevel").getValue(Int::class.java) ?: 0,
                        inGameUID    = userSnap.child("InGameUID").getValue(Long::class.java) ?: 0L
                    )
                )
            }

            tempList.sortByDescending { it.winningCoins }
            tempList.forEachIndexed { index, model ->
                model.rank     = index + 1
                model.userName = model.inGameName.ifEmpty { "Player ${model.rank}" }
            }

            Log.d(TAG, "✅ Firebase fetch done: ${tempList.size} users")

            // Background thread pe cache save karo
            Thread { saveCacheLeaderboard(tempList) }.start()

            fullCachedList.clear()
            fullCachedList.addAll(tempList)

            resetAndShowFirstPage()
            hideProgress()
            isFetching = false
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAGINATION
    // ══════════════════════════════════════════════════════════════════════════
    private fun resetAndShowFirstPage() {
        currentOffset = 0
        allLoaded     = false
        displayedList.clear()
        adapter.notifyDataSetChanged()
        loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoadingMore || allLoaded) return

        // ✅ FIX 4: 'size' unresolved + Collection mismatch — pageSize Int variable fix
        val pageSize    = if (currentOffset == 0) PAGE_SIZE else LOAD_MORE_SIZE
        val from        = currentOffset
        val to          = minOf(from + pageSize, fullCachedList.size)

        if (from >= fullCachedList.size) {
            allLoaded = true
            updateLoadMoreHint(true)
            return
        }

        isLoadingMore = true
        showLoadMoreProgress(true)

        // ✅ FIX 4 cont: .toList() — Collection<LeaderboardModel> type mismatch fix
        val nextItems   = fullCachedList.subList(from, to).toList()
        val insertStart = displayedList.size

        displayedList.addAll(nextItems)
        adapter.notifyItemRangeInserted(insertStart, nextItems.size)

        currentOffset = to
        isLoadingMore = false
        showLoadMoreProgress(false)

        if (currentOffset >= fullCachedList.size) allLoaded = true

        updateLoadMoreHint(allLoaded)

        recyclerView.visibility = View.VISIBLE
        tvEmpty.visibility      = View.GONE

        Log.d(TAG, "📄 Showing: $currentOffset / ${fullCachedList.size}")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CACHE — SharedPreferences + JSON (NO Room, NO kapt)
    // ══════════════════════════════════════════════════════════════════════════
    private fun isCacheValid(): Boolean {
        val savedTime = prefs.getLong(CACHE_TIME_KEY, 0L)
        if (savedTime == 0L) return false
        val age = System.currentTimeMillis() - savedTime
        Log.d(TAG, "Cache age: ${age / 60000} min | TTL: ${TTL_MS / 60000} min")
        return age < TTL_MS
    }

    private fun getCachedLeaderboard(): List<LeaderboardModel>? {
        if (!isCacheValid()) return null
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        return try {
            val arr  = JSONArray(json)
            val list = mutableListOf<LeaderboardModel>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    LeaderboardModel(
                        userId       = obj.optString("userId"),
                        matchPlayed  = obj.optInt("matchPlayed"),
                        winningCoins = obj.optInt("winningCoins"),
                        winningCount = obj.optInt("winningCount"),
                        inGameName   = obj.optString("inGameName"),
                        inGameLevel  = obj.optInt("inGameLevel"),
                        inGameUID    = obj.optLong("inGameUID"),
                        rank         = obj.optInt("rank"),
                        userName     = obj.optString("userName")
                    )
                )
            }
            Log.d(TAG, "📦 Cache loaded: ${list.size} items")
            list
        } catch (e: Exception) {
            Log.e(TAG, "Cache parse error: ${e.message}")
            null
        }
    }

    private fun saveCacheLeaderboard(list: List<LeaderboardModel>) {
        try {
            val arr = JSONArray()
            for (model in list) {
                val obj = JSONObject()
                obj.put("userId",       model.userId)
                obj.put("matchPlayed",  model.matchPlayed)
                obj.put("winningCoins", model.winningCoins)
                obj.put("winningCount", model.winningCount)
                obj.put("inGameName",   model.inGameName)
                obj.put("inGameLevel",  model.inGameLevel)
                obj.put("inGameUID",    model.inGameUID)
                obj.put("rank",         model.rank)
                obj.put("userName",     model.userName)
                arr.put(obj)
            }
            prefs.edit()
                .putString(CACHE_KEY, arr.toString())
                .putLong(CACHE_TIME_KEY, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "💾 Cache saved: ${list.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Cache save error: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private fun showProgress() {
        progressBar.visibility  = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility      = View.GONE
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
    }

    private fun showEmpty(msg: String) {
        tvEmpty.text            = msg
        tvEmpty.visibility      = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    // ✅ FIX 1 cont: Nullable safe call (?.) — no crash even if view missing
    private fun showLoadMoreProgress(show: Boolean) {
        loadMoreProgress?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateLoadMoreHint(allLoaded: Boolean) {
        tvLoadMore?.visibility = View.VISIBLE
        tvLoadMore?.text = if (allLoaded) "✅ All players loaded" else "⬇ Scroll down to load more"
    }
}