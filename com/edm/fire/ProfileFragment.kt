package com.edm.fire

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.edm.fire.data.SimpleCacheManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var cacheManager: SimpleCacheManager

    // UI Components
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tvUsername: TextView
    private lateinit var tvEdmfireUid: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvNoKyc: TextView
    private lateinit var btnGotoKyc: Button
    private lateinit var tvJoiningDate: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvInGameUid: TextView
    private lateinit var tvKycStatus: TextView
    private lateinit var tvMatchesPlayed: TextView
    private lateinit var tvgamelevel: TextView
    private lateinit var btnManualRefresh: Button

    // Cache keys
    private companion object {
        const val CACHE_KEY_PROFILE = "profile_data"
        const val CACHE_KEY_MATCHES = "matches_played"
        const val TTL_PROFILE_HOURS_VERIFIED = 12  // Verified user ke liye 12 hours
        const val TTL_PROFILE_HOURS_NOT_VERIFIED = 0  // Not verified user ke liye 0 (har bar fetch)
        const val TTL_MATCHES_HOURS = 12
        const val TAG = "ProfileFragment" // Debug ke liye
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        remoteConfig = Firebase.remoteConfig

        // Context check karke initialize karo
        if (!isAdded) return view
        cacheManager = SimpleCacheManager(requireContext())

        initializeViews(view)
        setupRemoteConfig()
        setupSwipeRefresh(view)
        loadProfileImage(view)
        setupClickListeners(view)
        loadAppVersion(view)

        // Load profile with cache
        loadUserProfileDataWithCache()

        return view
    }

    private fun setupSwipeRefresh(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            forceRefreshAllData()
        }
    }

    private fun initializeViews(view: View) {
        tvUsername = view.findViewById(R.id.tv_username)
        tvEdmfireUid = view.findViewById(R.id.tv_edmfire_uid)
        tvEmail = view.findViewById(R.id.tv_email)
        tvNoKyc = view.findViewById(R.id.tv_no_kyc)
        btnGotoKyc = view.findViewById(R.id.btn_goto_kyc)
        tvJoiningDate = view.findViewById(R.id.tv_joining_date)
        tvPhone = view.findViewById(R.id.tv_phone)
        tvInGameUid = view.findViewById(R.id.in_game_uid)
        tvKycStatus = view.findViewById(R.id.tv_kyc_status)
        tvMatchesPlayed = view.findViewById(R.id.tv_matches_played)
        tvgamelevel = view.findViewById(R.id.tv_game_level)
        btnManualRefresh = view.findViewById(R.id.btn_manual_refresh)

        btnManualRefresh.setOnClickListener {
            forceRefreshAllData()
        }
    }

    private fun forceRefreshAllData() {
        if (!isAdded) return
        swipeRefreshLayout.isRefreshing = true
        lifecycleScope.launch {
            loadProfileFromFirebase(forceRefresh = true)
            loadMatchesPlayedFromFirebase(forceRefresh = true)
            if (isAdded) {
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(requireContext(), "Profile refreshed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserProfileDataWithCache() {
        if (!isAdded) return
        lifecycleScope.launch {
            try {
                // Pehle check karo current KYC status cache me hai ya nahi
                val cachedProfile = getCachedProfile()
                val isKycVerified = checkIfKycVerifiedFromCache(cachedProfile)

                // Agar KYC verified nahi hai to har bar Firebase se fetch karo
                if (!isKycVerified) {
                    Log.d(TAG, "KYC verified nahi hai, Firebase se fresh data le rahe hain")
                    loadProfileFromFirebase(forceRefresh = false)
                } else {
                    // KYC verified hai to TTL check karo
                    if (cachedProfile != null && !isProfileCacheExpired()) {
                        Log.d(TAG, "KYC verified user - Cache se data load ho raha hai")
                        if (isAdded) displayProfileData(cachedProfile)
                    } else {
                        Log.d(TAG, "KYC verified user - Cache expired ya nahi hai, Firebase se load kar rahe hain")
                        loadProfileFromFirebase(forceRefresh = false)
                    }
                }

                // Matches alag se load karo (yeh always cache use karega 12 hours TTL)
                loadMatchesPlayedWithCache()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile: ${e.message}")
                if (isAdded) setDefaultValues()
            }
        }
    }

    // naya function - check karta hai ki cache se KYC verified hai ya nahi
    private fun checkIfKycVerifiedFromCache(cachedProfile: ProfileCacheData?): Boolean {
        if (cachedProfile == null) return false
        return cachedProfile.kycStatus.equals("Game Kyc Verified", ignoreCase = true) ||
                cachedProfile.kycStatus.equals("Verified", ignoreCase = true)
    }

    // naya function - KYC status ke according TTL return karega
    private fun getProfileTTL(): Int {
        // Current displayed KYC status check karo
        val currentKycStatus = if (::tvKycStatus.isInitialized) {
            tvKycStatus.text.toString()
        } else {
            ""
        }

        return if (currentKycStatus.equals("Game Kyc Verified", ignoreCase = true) ||
            currentKycStatus.equals("Verified", ignoreCase = true)) {
            TTL_PROFILE_HOURS_VERIFIED  // 12 hours for verified users
        } else {
            TTL_PROFILE_HOURS_NOT_VERIFIED  // 0 hours - har bar fetch for non-verified
        }
    }

    private suspend fun getCachedProfile(): ProfileCacheData? {
        if (!isAdded) return null
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences("edm_fire_cache", android.content.Context.MODE_PRIVATE)
        val jsonString = prefs.getString("cached_profile_${auth.currentUser?.uid}", null) ?: return null
        return try {
            val json = org.json.JSONObject(jsonString)
            ProfileCacheData(
                username = json.optString("username", ""),
                email = json.optString("email", ""),
                inGameUid = json.optString("inGameUid", ""),
                gameLevel = json.optString("gameLevel", ""),
                phone = json.optString("phone", ""),
                joiningDate = json.optString("joiningDate", ""),
                kycStatus = json.optString("kycStatus", ""),
                lastUpdated = json.optLong("lastUpdated", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cache parse error: ${e.message}")
            null
        }
    }

    private suspend fun saveProfileToCache(profile: ProfileCacheData) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("edm_fire_cache", android.content.Context.MODE_PRIVATE)
        val json = org.json.JSONObject().apply {
            put("username", profile.username)
            put("email", profile.email)
            put("inGameUid", profile.inGameUid)
            put("gameLevel", profile.gameLevel)
            put("phone", profile.phone)
            put("joiningDate", profile.joiningDate)
            put("kycStatus", profile.kycStatus)
            put("lastUpdated", System.currentTimeMillis())
        }
        prefs.edit().putString("cached_profile_${auth.currentUser?.uid}", json.toString()).apply()
    }

    private fun isProfileCacheExpired(): Boolean {
        val ctx = context ?: return true
        val prefs = ctx.getSharedPreferences("edm_fire_cache", android.content.Context.MODE_PRIVATE)
        val lastUpdated = prefs.getLong("profile_last_updated_${auth.currentUser?.uid}", 0)
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdated) / (1000 * 60 * 60)
        val ttl = getProfileTTL()

        // Agar TTL 0 hai to always expired (har bar fetch)
        if (ttl == 0) return true

        return hoursSinceUpdate >= ttl
    }

    private suspend fun loadProfileFromFirebase(forceRefresh: Boolean) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "User ID null hai - user login nahi hai")
            if (isAdded) setDefaultValues()
            return
        }

        try {
            Log.d(TAG, "Firestore se document fetch kar raha hai: Users/$userId")
            val document = db.collection("Users")
                .document(userId)
                .get()
                .await()

            if (!isAdded) return

            if (document.exists()) {
                Log.d(TAG, "Document mil gaya, fields: ${document.data?.keys}")

                // Type-safe field reading
                val inGameUidValue = when (val value = document.get("InGameUID")) {
                    is String -> value
                    is Long -> value.toString()
                    is Number -> value.toString()
                    else -> "0"
                }

                val levelValue = when (val value = document.get("Level")) {
                    is String -> value
                    is Long -> value.toString()
                    is Number -> value.toString()
                    else -> "0"
                }

                val kycStatusValue = document.getString("KYCStatus")
                    ?: document.getString("kycStatus")
                    ?: "Incomplete"

                val profileData = ProfileCacheData(
                    username = document.getString("UserName")
                        ?: document.getString("userName")
                        ?: document.getString("username")
                        ?: "KYC Required",
                    email = document.getString("email")
                        ?: document.getString("Email")
                        ?: "Not Set",
                    inGameUid = inGameUidValue,
                    gameLevel = levelValue,
                    phone = document.getString("phone")
                        ?: document.getString("Phone")
                        ?: "Not Available",
                    joiningDate = document.getString("JoiningDate")
                        ?: document.getString("joiningDate")
                        ?: "",
                    kycStatus = kycStatusValue,
                    lastUpdated = System.currentTimeMillis()
                )

                saveProfileToCache(profileData)

                val ctx = context ?: return
                val prefs = ctx.getSharedPreferences("edm_fire_cache", android.content.Context.MODE_PRIVATE)
                prefs.edit().putLong("profile_last_updated_${auth.currentUser?.uid}", System.currentTimeMillis()).apply()

                if (isAdded) displayProfileData(profileData)

            } else {
                Log.e(TAG, "Document exist nahi karta: Users/$userId")
                if (isAdded) setDefaultValues()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore error: ${e.message}")
            if (isAdded) setDefaultValues()
        }
    }

    private fun displayProfileData(profile: ProfileCacheData) {
        if (!isAdded) return

        // UID update karo
        tvEdmfireUid.text = auth.currentUser?.uid ?: "N/A"

        tvUsername.text = profile.username.ifEmpty { "Not Set" }
        tvEmail.text = profile.email.ifEmpty { "Not Set" }
        tvInGameUid.text = profile.inGameUid.ifEmpty { "Not Set" }
        tvgamelevel.text = profile.gameLevel.ifEmpty { "N/A" }
        tvPhone.text = profile.phone.ifEmpty { "Not Available" }

        tvJoiningDate.text = if (profile.joiningDate.isNotEmpty()) {
            "Joined: ${profile.joiningDate}"
        } else {
            "Joined: Not Available"
        }

        tvKycStatus.text = profile.kycStatus

        if (profile.kycStatus.equals("Game Kyc Verified", ignoreCase = true) ||
            profile.kycStatus.equals("Verified", ignoreCase = true)) {
            btnGotoKyc.visibility = View.GONE
            tvNoKyc.visibility = View.VISIBLE
            try {
                tvKycStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_light))
            } catch (_: Exception) {}
        } else {
            btnGotoKyc.visibility = View.VISIBLE
            tvNoKyc.visibility = View.VISIBLE
            try {
                tvKycStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_light))
            } catch (_: Exception) {}
        }

        Log.d(TAG, "UI update ho gaya: ${profile.username}, ${profile.email}")
    }

    private fun loadMatchesPlayedWithCache() {
        if (!isAdded) return
        lifecycleScope.launch {
            val ctx = context ?: return@launch
            val prefs = ctx.getSharedPreferences("edm_fire_cache", android.content.Context.MODE_PRIVATE)
            val lastUpdated = prefs.getLong("matches_last_updated_${auth.currentUser?.uid}", 0)
            val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdated) / (1000 * 60 * 60)

            if (hoursSinceUpdate < TTL_MATCHES_HOURS && !swipeRefreshLayout.isRefreshing) {
                val cachedMatches = prefs.getInt("cached_matches_${auth.currentUser?.uid}", 0)
                if (isAdded) tvMatchesPlayed.text = cachedMatches.toString()
            } else {
                loadMatchesPlayedFromFirebase(forceRefresh = false)
            }
        }
    }

    private suspend fun loadMatchesPlayedFromFirebase(forceRefresh: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        try {
            val document = db.collection("Users")
                .document(userId)
                .get()
                .await()

            // Type-safe reading for TotalPlayed
            val totalPlayed = when (val value = document.get("TotalPlayed")) {
                is Long -> value
                is String -> value.toLongOrNull() ?: 0L
                is Number -> value.toLong()
                else -> 0L
            }

            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("edm_fire_cache", android.content.Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("cached_matches_${auth.currentUser?.uid}", totalPlayed.toInt())
                putLong("matches_last_updated_${auth.currentUser?.uid}", System.currentTimeMillis())
            }.apply()

            if (isAdded) {
                tvMatchesPlayed.text = totalPlayed.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Matches load error: ${e.message}")
            if (isAdded) {
                tvMatchesPlayed.text = "0"
            }
        }
    }

    private fun setDefaultValues() {
        if (!isAdded) return

        val currentUserId = auth.currentUser?.uid ?: "N/A"
        tvEdmfireUid.text = currentUserId
        tvUsername.text = "Not Set"
        tvEmail.text = "Not set"
        tvInGameUid.text = "Not Set"
        tvPhone.text = "Phone Not Available"
        tvJoiningDate.text = "Joined: Not Available"
        tvKycStatus.text = "Incomplete"
        tvMatchesPlayed.text = "0"
        btnGotoKyc.visibility = View.VISIBLE
        tvNoKyc.visibility = View.VISIBLE
        tvgamelevel.text = "N/A"
    }

    private fun setupRemoteConfig() {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.setDefaultsAsync(
            mapOf(
                "privacy_policy_page" to "https://www.edmfire.com/privacy",
                "about_us" to "https://www.edmfire.com/about",
                "term_and_condition_page" to "https://www.edmfire.com/terms"
            )
        )

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (!isAdded) return@addOnCompleteListener
            if (!task.isSuccessful) {
                Toast.makeText(requireContext(), "Failed to update configuration", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfileImage(view: View) {
        val ivProfile: ImageView = view.findViewById(R.id.iv_profile)

        Glide.with(this)
            .load(R.drawable.applogo)
            .placeholder(R.drawable.applogo)
            .error(R.drawable.applogo)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .circleCrop()
            .into(ivProfile)
    }

    private fun setupClickListeners(view: View) {
        btnGotoKyc.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityGameIdVerification::class.java))
        }

        view.findViewById<LinearLayout>(R.id.menu_settings)?.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        view.findViewById<LinearLayout>(R.id.menu_reset_password)?.setOnClickListener {
            startActivity(Intent(requireContext(), ResetPasswordActivity::class.java))
        }

        view.findViewById<LinearLayout>(R.id.menu_privacy)?.setOnClickListener {
            val privacyUrl = remoteConfig.getString("privacy_policy_page")
            openWebPage(privacyUrl, "Privacy Policy")
        }

        view.findViewById<LinearLayout>(R.id.menu_terms)?.setOnClickListener {
            val termsUrl = remoteConfig.getString("term_and_condition_page")
            openWebPage(termsUrl, "Terms & Conditions")
        }

        view.findViewById<LinearLayout>(R.id.menu_about)?.setOnClickListener {
            val aboutUrl = remoteConfig.getString("about_us")
            openWebPage(aboutUrl, "About Us")
        }

        view.findViewById<LinearLayout>(R.id.menu_logout)?.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun openWebPage(url: String, title: String) {
        if (url.isNotEmpty() && url.startsWith("http")) {
            val intent = Intent(requireContext(), WebviewActivity::class.java).apply {
                putExtra("url", url)
                putExtra("title", title)
            }
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Invalid link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        val intent = Intent(requireActivity(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun loadAppVersion(view: View) {
        val tvVersion = view.findViewById<TextView>(R.id.tv_version)
        val ctx = context ?: return
        try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            tvVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "Version 1.0"
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume mein fresh data check karo
        if (isAdded && isProfileCacheExpired()) {
            loadUserProfileDataWithCache()
        }
    }

    data class ProfileCacheData(
        val username: String,
        val email: String,
        val inGameUid: String,
        val gameLevel: String,
        val phone: String,
        val joiningDate: String,
        val kycStatus: String,
        val lastUpdated: Long
    )
}