package com.edm.fire

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class DepositActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var rvOffers: RecyclerView
    private lateinit var progressBarOffers: ProgressBar
    private lateinit var etAmount: EditText
    private lateinit var btnProceed: Button
    private lateinit var summaryLayout: LinearLayout
    private lateinit var tvYouAdd: TextView
    private lateinit var tvBonus: TextView
    private lateinit var tvTotalCoins: TextView
    private lateinit var imbClearSummary: ImageView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var btnVerification: Button
    private lateinit var progressBarVerification: ProgressBar

    // ✅ NEW: ScrollView and Handler for auto-scroll
    private lateinit var scrollView: ScrollView
    private val scrollHandler = Handler(Looper.getMainLooper())

    // Firebase
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var database: FirebaseDatabase? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // State Management
    private var selectedOffer: Offer? = null
    private var offersList: List<Offer> = emptyList()
    private var currentAmount: Int = 0
    private var currentBonus: Int = 0
    private var currentTotal: Int = 0

    // Remote Config
    private var lastConfigFetchTime: Long = 0
    private val CONFIG_CACHE_DURATION = 24 * 60 * 60 * 1000L
    private val REMOTE_CONFIG_KEY_DATABASE_URL = "main_database_rtdb"

    companion object {
        private const val PREF_NAME_FIREBASE_CONFIG = "FirebaseConfig"
        private const val PREF_NAME_APP_CACHE = "AppCache"
        private const val KEY_CACHED_DATABASE_URL = "cached_database_url"
        private const val KEY_LAST_CONFIG_FETCH_TIME = "last_config_fetch_time"
        private const val KEY_CACHED_OFFERS = "CACHED_OFFERS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deposite)

        // Initialize Firebase Services
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize Remote Config
        initializeRemoteConfig()

        initializeViews()
        setupRecyclerViewWithCaching()
        setupCustomAmountListener()
        setupOtherListeners()
        checkUserAuthentication()
        loadUserTopUpCoinsFromFirestore()

        // ✅ NEW: Start Auto Scroll Animation (3 seconds)
        performAutoScrollAnimation()
    }

    // ✅ NEW: Auto Scroll Animation - 3 seconds bottom stay
    private fun performAutoScrollAnimation() {
        scrollView.post {
            // ✅ STEP 1: First go to TOP (ensure starting from top)
            scrollView.scrollTo(0, 0)

            // Small delay to ensure layout is stable
            scrollHandler.postDelayed({
                // Get total scrollable height (bottom position)
                val childHeight = scrollView.getChildAt(0).height
                val scrollViewHeight = scrollView.height
                val maxScrollY = childHeight - scrollViewHeight

                // ✅ STEP 2: Smooth full scroll from TOP to BOTTOM
                scrollView.smoothScrollTo(0, maxScrollY)

                // ✅ STEP 3: Stay at bottom for 3 seconds, then back to top
                scrollHandler.postDelayed({
                    scrollView.smoothScrollTo(0, 0)
                }, 3000) // 3000ms = 3 seconds stay at bottom

            }, 300) // 300ms initial delay for stability
        }
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    private fun initializeFirebaseDatabase() {
        val currentTime = System.currentTimeMillis()
        val shouldFetchConfig = currentTime - lastConfigFetchTime > CONFIG_CACHE_DURATION

        if (shouldFetchConfig) {
            fetchDatabaseUrlFromRemoteConfig()
        } else {
            setupDatabaseWithUrl(getCachedDatabaseUrl())
        }
    }

    private fun fetchDatabaseUrlFromRemoteConfig() {
        showLoadingState()

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val databaseUrl = remoteConfig.getString(REMOTE_CONFIG_KEY_DATABASE_URL)
                    lastConfigFetchTime = System.currentTimeMillis()
                    cacheDatabaseUrl(databaseUrl)
                    setupDatabaseWithUrl(databaseUrl)
                } else {
                    setupDatabaseWithUrl(getCachedDatabaseUrl())
                    hideLoadingState()
                }
            }
            .addOnFailureListener {
                setupDatabaseWithUrl(getCachedDatabaseUrl())
                hideLoadingState()
            }
    }

    private fun cacheDatabaseUrl(url: String) {
        try {
            val sharedPref = getSharedPreferences(PREF_NAME_FIREBASE_CONFIG, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString(KEY_CACHED_DATABASE_URL, url)
                putLong(KEY_LAST_CONFIG_FETCH_TIME, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            // Silent fail for caching
        }
    }

    private fun getCachedDatabaseUrl(): String {
        return try {
            val sharedPref = getSharedPreferences(PREF_NAME_FIREBASE_CONFIG, Context.MODE_PRIVATE)
            val cachedUrl = sharedPref.getString(KEY_CACHED_DATABASE_URL, "")
            lastConfigFetchTime = sharedPref.getLong(KEY_LAST_CONFIG_FETCH_TIME, 0)
            cachedUrl ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun initializeViews() {
        rvOffers = findViewById(R.id.rv_offers)
        progressBarOffers = findViewById(R.id.progressBarOffers)
        etAmount = findViewById(R.id.etAmount)
        btnProceed = findViewById(R.id.btnProceed)
        summaryLayout = findViewById(R.id.summaryLayout)
        tvYouAdd = findViewById(R.id.tvYouAdd)
        tvBonus = findViewById(R.id.tvBonus)
        tvTotalCoins = findViewById(R.id.tvTotalCoins)
        imbClearSummary = findViewById(R.id.imb_clear_summary)
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance)
        btnVerification = findViewById(R.id.btnVerification)
        progressBarVerification = findViewById(R.id.progressBarVerification)

        // ✅ NEW: Initialize ScrollView
        scrollView = findViewById(R.id.scrollView)

        // Set amount input filter
        etAmount.filters = arrayOf(InputFilter.LengthFilter(6))
    }

    private fun checkUserAuthentication() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    // --- FIRESTORE TOPUP COINS LOADING ---
    private fun loadUserTopUpCoinsFromFirestore() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("Users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        try {
                            // Load ONLY TopUpCoins field
                            val topUpCoins = document.getLong("TopUpCoins") ?: 0L

                            // Update UI with Coins format
                            tvCurrentBalance.text = formatTopUpCoins(topUpCoins)

                        } catch (e: Exception) {
                            tvCurrentBalance.text = "0 Coins"
                        }
                    } else {
                        tvCurrentBalance.text = "0 Coins"
                    }
                }
                .addOnFailureListener {
                    tvCurrentBalance.text = "0 Coins"
                }
        } ?: run {
            tvCurrentBalance.text = "0 Coins"
        }
    }

    private fun formatTopUpCoins(topUpCoins: Long): String {
        return try {
            when {
                topUpCoins >= 1000000 -> String.format("%.1fM Coins", topUpCoins / 1000000.0)
                topUpCoins >= 1000 -> String.format("%.1fK Coins", topUpCoins / 1000.0)
                else -> String.format("%d Coins", topUpCoins)
            }
        } catch (e: Exception) {
            "0 Coins"
        }
    }

    // --- CACHING & RECYCLERVIEW SETUP ---
    private fun setupRecyclerViewWithCaching() {
        val cachedOffers = loadOffersFromCache()
        if (cachedOffers.isNotEmpty()) {
            offersList = cachedOffers
            setupAdapter(offersList)
            initializeFirebaseDatabase()
        } else {
            showLoadingState()
            initializeFirebaseDatabase()
        }
    }

    private fun showLoadingState() {
        progressBarOffers.visibility = View.VISIBLE
        rvOffers.visibility = View.GONE
    }

    private fun hideLoadingState() {
        progressBarOffers.visibility = View.GONE
        rvOffers.visibility = View.VISIBLE
    }

    private fun fetchOffersFromFirebase() {
        if (database == null) {
            Toast.makeText(this, "Database not initialized", Toast.LENGTH_LONG).show()
            hideLoadingState()
            return
        }

        val offersRef = database!!.getReference("Offers")
        offersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newOffersList = mutableListOf<Offer>()
                for (offerSnapshot in snapshot.children) {
                    try {
                        val offerData = offerSnapshot.value as? Map<String, Any>
                        if (offerData != null) {
                            val title = offerData["OfferTitle"] as? String ?: ""
                            val bonus = offerData["OfferBonus"] as? String ?: ""
                            val totalCoins = (offerData["OfferTotalCoins"] as? Long)?.toInt() ?: 0
                            val isBestValue = title == "Add ₹501"
                            if (title.isNotEmpty()) {
                                newOffersList.add(Offer(title, bonus, totalCoins, isBestValue))
                            }
                        }
                    } catch (e: DatabaseException) {
                        continue
                    }
                }
                offersList = newOffersList
                saveOffersToCache(offersList)
                setupAdapter(offersList)
                hideLoadingState()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DepositActivity, "Failed to load offers", Toast.LENGTH_LONG).show()
                hideLoadingState()
            }
        })
    }

    private fun setupAdapter(offers: List<Offer>) {
        val adapter = OfferAdapter(offers) { clickedOffer ->
            selectOffer(clickedOffer)
        }
        rvOffers.layoutManager = LinearLayoutManager(this@DepositActivity)
        rvOffers.adapter = adapter
    }

    // --- CACHING HELPERS ---
    private fun saveOffersToCache(offers: List<Offer>) {
        try {
            val sharedPreferences = getSharedPreferences(PREF_NAME_APP_CACHE, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val jsonArray = JSONArray()
            offers.forEach { offer ->
                val jsonObject = JSONObject().apply {
                    put("offerTitle", offer.offerTitle)
                    put("offerBonus", offer.offerBonus)
                    put("offerTotalCoins", offer.offerTotalCoins)
                    put("isBestValue", offer.isBestValue)
                }
                jsonArray.put(jsonObject)
            }
            editor.putString(KEY_CACHED_OFFERS, jsonArray.toString())
            editor.apply()
        } catch (e: Exception) {
            // Silent fail for caching
        }
    }

    private fun loadOffersFromCache(): List<Offer> {
        return try {
            val sharedPreferences = getSharedPreferences(PREF_NAME_APP_CACHE, Context.MODE_PRIVATE)
            val cachedJsonString = sharedPreferences.getString(KEY_CACHED_OFFERS, null) ?: return emptyList()

            val jsonArray = JSONArray(cachedJsonString)
            val offers = mutableListOf<Offer>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                offers.add(
                    Offer(
                        jsonObject.getString("offerTitle"),
                        jsonObject.getString("offerBonus"),
                        jsonObject.getInt("offerTotalCoins"),
                        jsonObject.getBoolean("isBestValue")
                    )
                )
            }
            offers
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- UI STATE MANAGEMENT ---
    private fun selectOffer(offer: Offer) {
        selectedOffer = offer
        val amount = extractAmount(offer.offerTitle)
        val bonus = extractBonus(offer.offerBonus)

        currentAmount = amount
        currentBonus = bonus
        currentTotal = amount + bonus

        etAmount.setText(amount.toString())
        updateSummary(amount, bonus)
        showSummaryState()
    }

    private fun showSummaryState() {
        rvOffers.visibility = View.GONE
        summaryLayout.visibility = View.VISIBLE
        etAmount.isEnabled = false
        btnProceed.isEnabled = true
        btnProceed.text = "PROCEED TO PAY ₹$currentAmount"
    }

    private fun showOfferSelectionState() {
        rvOffers.visibility = View.VISIBLE
        summaryLayout.visibility = View.GONE
        etAmount.text.clear()
        etAmount.isEnabled = true
        selectedOffer = null
        currentAmount = 0
        currentBonus = 0
        currentTotal = 0
        btnProceed.text = "PROCEED TO ADD ₹0"
        btnProceed.isEnabled = false
    }

    // --- AMOUNT HANDLING & VALIDATION ---
    private fun setupCustomAmountListener() {
        etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (selectedOffer == null) {
                    val amount = s.toString().toIntOrNull() ?: 0

                    if (amount > 0) {
                        currentAmount = amount
                        currentBonus = calculateBonus(amount)
                        currentTotal = amount + currentBonus

                        updateSummary(amount, currentBonus)
                        btnProceed.isEnabled = true
                        btnProceed.text = "PROCEED TO PAY ₹$amount"
                    } else {
                        summaryLayout.visibility = View.GONE
                        btnProceed.text = "PROCEED TO ADD ₹0"
                        btnProceed.isEnabled = false
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun calculateBonus(amount: Int): Int {
        return when {
            amount >= 501 -> (amount * 25 / 100)
            amount >= 101 -> (amount * 20 / 100)
            amount >= 51 -> (amount * 15 / 100)
            amount >= 25 -> (amount * 10 / 100)
            else -> 0
        }
    }

    // --- PAYMENT PROCESSING ---
    private fun setupOtherListeners() {
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        imbClearSummary.setOnClickListener {
            showOfferSelectionState()
        }

        btnVerification.setOnClickListener {
            startVerificationLoading()
            Handler().postDelayed({
                stopVerificationLoading()
                startActivity(Intent(this, PaymentVerification::class.java))
            }, 1500)
        }

        btnProceed.setOnClickListener {
            processPayment()
        }
    }

    private fun startVerificationLoading() {
        btnVerification.text = "Navigating..."
        btnVerification.isEnabled = false
        progressBarVerification.visibility = View.VISIBLE
    }

    private fun stopVerificationLoading() {
        btnVerification.text = "🔍 VERIFY 12 DIGIT UTR / RRN NUMBER"
        btnVerification.isEnabled = true
        progressBarVerification.visibility = View.GONE
    }

    private fun processPayment() {
        when {
            currentAmount < 1 -> {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return
            }
            currentAmount > 25000 -> {
                Toast.makeText(this, "Maximum amount is ₹25,000", Toast.LENGTH_SHORT).show()
                return
            }
            auth.currentUser == null -> {
                Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
                return
            }
            else -> {
                val intent = Intent(this, PayActivity::class.java).apply {
                    putExtra("EXTRA_AMOUNT", currentAmount.toDouble())
                    putExtra("EXTRA_BONUS", currentBonus)
                    putExtra("EXTRA_TOTAL", currentTotal)
                    putExtra("EXTRA_USER_ID", auth.currentUser?.uid ?: "")
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    private fun updateSummary(amount: Int, bonus: Int) {
        summaryLayout.visibility = View.VISIBLE
        tvYouAdd.text = "₹$amount"
        tvBonus.text = "₹$bonus"
        tvTotalCoins.text = "${amount + bonus}"
    }

    private fun extractAmount(title: String): Int {
        return title.replace("Add ₹", "").trim().toIntOrNull() ?: 0
    }

    private fun extractBonus(bonusString: String): Int {
        return bonusString.replace("and Get ₹", "").replace(" Bonus", "").trim().toIntOrNull() ?: 0
    }

    private fun setupDatabaseWithUrl(databaseUrl: String) {
        try {
            database = FirebaseDatabase.getInstance(databaseUrl)
            fetchOffersFromFirebase()
        } catch (e: Exception) {
            Toast.makeText(this, "Database connection failed", Toast.LENGTH_LONG).show()
            hideLoadingState()
        }
    }

    override fun onResume() {
        super.onResume()
        checkUserAuthentication()
        stopVerificationLoading()
        loadUserTopUpCoinsFromFirestore()
    }

    override fun onPause() {
        super.onPause()
        stopVerificationLoading()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ NEW: Clean up scroll handler to prevent memory leaks
        scrollHandler.removeCallbacksAndMessages(null)
    }
}