package com.edm.fire

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ActivityGameIdVerification : AppCompatActivity() {

    private lateinit var etFreeFireUid: EditText
    private lateinit var spinnerRegion: Spinner
    private lateinit var btnVerify: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var layoutUserInfo: LinearLayout
    private lateinit var tvPlayerName: TextView
    private lateinit var tvPlayerLevel: TextView
    private lateinit var tvPlayerRegion: TextView
    private lateinit var tvRequestStatus: TextView
    private lateinit var tvAttemptsLeft: TextView
    private lateinit var tvStorageInfo: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig

    // ✅ PRODUCTION LIMITS - 5 attempts per 12 hours
    private val MAX_ATTEMPTS_PER_PERIOD = 5
    private val LOCKOUT_DURATION_HOURS = 12L
    private val LOCKOUT_DURATION_MS = LOCKOUT_DURATION_HOURS * 60 * 60 * 1000L

    // ✅ Firebase Function URL Key
    private val REMOTE_CONFIG_FREE_FIRE_KYC_URL = "FREE_FIRE_KYC_URL"

    private val FIRESTORE_USER_VERIFICATIONS = "user_verifications"
    private val FIRESTORE_ATTEMPTS_TRACKING = "attempts_tracking"

    private val regions = arrayOf("ind", "br", "sg", "ru", "id", "tw", "us", "vn", "th", "me", "pk", "cis", "bd")
    private val regionNames = arrayOf("India", "Brazil", "Singapore", "Russia", "Indonesia", "Taiwan", "USA", "Vietnam", "Thailand", "Middle East", "Pakistan", "CIS", "Bangladesh")

    private var isVerifying = false
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_id_verification)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid

        initializeRemoteConfig()
        initializeViews()
        setupRegionSpinner()
        setupButtonClick()
        checkExistingVerification()
        checkAttemptsLimit()
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            fetchTimeoutInSeconds = 10
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                println("✅ RemoteConfig loaded: ${remoteConfig.getString(REMOTE_CONFIG_FREE_FIRE_KYC_URL)}")
            } else {
                println("⚠️ RemoteConfig fetch failed, using defaults")
            }
        }
    }

    private fun getFirebaseFunctionUrl(): String {
        return try {
            remoteConfig.getString(REMOTE_CONFIG_FREE_FIRE_KYC_URL)
        } catch (e: Exception) {
            // Fallback URL - should be set in RemoteConfig
            "https://asia-south1-edm-fire-app.cloudfunctions.net/verifyFreeFireID"
        }
    }

    private fun initializeViews() {
        etFreeFireUid = findViewById(R.id.etFreeFireUid)
        spinnerRegion = findViewById(R.id.spinnerRegion)
        btnVerify = findViewById(R.id.btnVerify)
        progressBar = findViewById(R.id.progressBar)
        tvResult = findViewById(R.id.tvResult)
        layoutUserInfo = findViewById(R.id.layoutUserInfo)
        tvPlayerName = findViewById(R.id.tvPlayerName)
        tvPlayerLevel = findViewById(R.id.tvPlayerLevel)
        tvPlayerRegion = findViewById(R.id.tvPlayerRegion)
        tvRequestStatus = findViewById(R.id.tvRequestStatus)
        tvAttemptsLeft = findViewById(R.id.tvAttemptsLeft)
        tvStorageInfo = findViewById(R.id.tvStorageInfo)
    }

    private fun setupRegionSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, regionNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRegion.adapter = adapter
        spinnerRegion.setSelection(0)
    }

    private fun setupButtonClick() {
        btnVerify.setOnClickListener {
            if (isVerifying) return@setOnClickListener
            verifyFreeFireId()
        }
    }

    private fun checkExistingVerification() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                val isVerified = sharedPref.getBoolean("freefire_verified", false)

                if (isVerified) {
                    val playerName = sharedPref.getString("player_name", "")
                    val playerLevel = sharedPref.getInt("player_level", 0)
                    val playerRegion = sharedPref.getString("player_region", "")

                    if (!playerName.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            updatePlayerInfo(playerName, playerLevel, playerRegion ?: "IND")
                            layoutUserInfo.visibility = android.view.View.VISIBLE
                            showResult("✅ Account already verified!", true)
                            btnVerify.text = "Verified ✅"
                            disableInputs()
                            tvAttemptsLeft.text = "Permanently Verified"
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent handling
            }
        }
    }

    private fun checkAttemptsLimit() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            tvAttemptsLeft.text = "Login to verify"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val attemptsDoc = firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                    .document(currentUser.uid)
                    .get()
                    .await()

                val currentTime = System.currentTimeMillis()

                if (attemptsDoc.exists()) {
                    val attemptsData = attemptsDoc.data
                    val attemptsCount = attemptsData?.get("attemptsCount") as? Long ?: 0
                    val isLocked = attemptsData?.get("isLocked") as? Boolean ?: false
                    val lockUntil = attemptsData?.get("lockUntil") as? Long ?: 0
                    val periodStart = attemptsData?.get("periodStart") as? Long ?: currentTime

                    val periodExpired = (currentTime - periodStart) >= LOCKOUT_DURATION_MS

                    withContext(Dispatchers.Main) {
                        if (periodExpired) {
                            resetAttemptsCount(currentUser.uid)
                            tvAttemptsLeft.text = "Attempts left: $MAX_ATTEMPTS_PER_PERIOD (resets every 12 hrs)"
                            btnVerify.isEnabled = true
                            btnVerify.text = "Verify FreeFire ID"
                        } else if (isLocked && currentTime < lockUntil) {
                            val hoursLeft = ((lockUntil - currentTime) / (60 * 60 * 1000)).toInt()
                            val minutesLeft = ((lockUntil - currentTime) % (60 * 60 * 1000)) / (60 * 1000)
                            tvAttemptsLeft.text = "Locked for ${hoursLeft}h ${minutesLeft}m (12hr limit reached)"
                            btnVerify.isEnabled = false
                            btnVerify.text = "Try Again Later"
                        } else if (isLocked && currentTime >= lockUntil) {
                            resetAttemptsCount(currentUser.uid)
                            tvAttemptsLeft.text = "Attempts left: $MAX_ATTEMPTS_PER_PERIOD (resets every 12 hrs)"
                            btnVerify.isEnabled = true
                            btnVerify.text = "Verify FreeFire ID"
                        } else {
                            val attemptsUsed = attemptsCount.toInt()
                            val attemptsLeft = MAX_ATTEMPTS_PER_PERIOD - attemptsUsed

                            if (attemptsLeft <= 0) {
                                lockUserFor12Hours(currentUser.uid)
                                tvAttemptsLeft.text = "Locked for $LOCKOUT_DURATION_HOURS hours (max attempts reached)"
                                btnVerify.isEnabled = false
                                btnVerify.text = "Try Again Later"
                            } else {
                                tvAttemptsLeft.text = "Attempts left: $attemptsLeft (resets every 12 hrs)"
                                btnVerify.isEnabled = true
                                btnVerify.text = "Verify FreeFire ID"
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvAttemptsLeft.text = "Attempts left: $MAX_ATTEMPTS_PER_PERIOD (resets every 12 hrs)"
                        btnVerify.isEnabled = true
                        btnVerify.text = "Verify FreeFire ID"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvAttemptsLeft.text = "Attempts: $MAX_ATTEMPTS_PER_PERIOD/12hrs"
                }
            }
        }
    }

    private suspend fun incrementAttemptsCount(userId: String): Boolean {
        return try {
            val attemptsDoc = firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                .document(userId)
                .get()
                .await()

            val currentTime = System.currentTimeMillis()

            if (attemptsDoc.exists()) {
                val currentAttempts = attemptsDoc.getLong("attemptsCount") ?: 0
                val periodStart = attemptsDoc.getLong("periodStart") ?: currentTime
                val periodExpired = (currentTime - periodStart) >= LOCKOUT_DURATION_MS

                if (periodExpired) {
                    val newAttempts = 1L
                    val updates = hashMapOf<String, Any>(
                        "attemptsCount" to newAttempts,
                        "periodStart" to currentTime,
                        "lastAttemptTime" to currentTime,
                        "updatedAt" to currentTime,
                        "isLocked" to false,
                        "lockUntil" to 0
                    )
                    firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                        .document(userId)
                        .update(updates)
                        .await()
                } else {
                    val newAttempts = currentAttempts + 1
                    val updates = hashMapOf<String, Any>(
                        "attemptsCount" to newAttempts,
                        "lastAttemptTime" to currentTime,
                        "updatedAt" to currentTime
                    )

                    if (newAttempts >= MAX_ATTEMPTS_PER_PERIOD) {
                        updates["isLocked"] = true
                        updates["lockUntil"] = currentTime + LOCKOUT_DURATION_MS
                    }

                    firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                        .document(userId)
                        .update(updates)
                        .await()
                }
            } else {
                val newData = hashMapOf<String, Any>(
                    "attemptsCount" to 1,
                    "periodStart" to currentTime,
                    "lastAttemptTime" to currentTime,
                    "createdAt" to currentTime,
                    "updatedAt" to currentTime,
                    "isLocked" to false,
                    "lockUntil" to 0
                )
                firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                    .document(userId)
                    .set(newData)
                    .await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun resetAttemptsCount(userId: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val resetData = hashMapOf<String, Any>(
                "attemptsCount" to 0,
                "periodStart" to currentTime,
                "isLocked" to false,
                "lockUntil" to 0,
                "lastAttemptTime" to currentTime,
                "updatedAt" to currentTime
            )
            firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                .document(userId)
                .set(resetData, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private suspend fun lockUserFor12Hours(userId: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val lockData = hashMapOf<String, Any>(
                "isLocked" to true,
                "lockUntil" to currentTime + LOCKOUT_DURATION_MS,
                "updatedAt" to currentTime
            )
            firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                .document(userId)
                .update(lockData)
                .await()
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private suspend fun saveSuccessfulVerification(userId: String, playerUid: String, playerName: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val verificationData = hashMapOf<String, Any>(
                "playerUid" to playerUid,
                "playerName" to playerName,
                "verifiedAt" to currentTime,
                "isVerified" to true,
                "updatedAt" to currentTime
            )
            firestore.collection(FIRESTORE_USER_VERIFICATIONS)
                .document(userId)
                .set(verificationData)
                .await()
            resetAttemptsCount(userId)
        } catch (e: Exception) {
            // Silent fail
        }
    }

    // ============================================================
    // ✅ MAIN VERIFICATION FUNCTION - FIREBASE FUNCTION CALL
    // ============================================================
    private fun verifyFreeFireId() {
        if (isVerifying) return

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showResult("Please login to verify your FreeFire ID", false)
            return
        }

        val freeFireUid = etFreeFireUid.text.toString().trim()
        val selectedRegionPosition = spinnerRegion.selectedItemPosition
        val region = regions[selectedRegionPosition]

        if (freeFireUid.isEmpty()) {
            showResult("Please enter FreeFire UID", false)
            return
        }

        if (freeFireUid.length < 5 || freeFireUid.length > 15) {
            showResult("UID must be between 5-15 digits", false)
            return
        }

        isVerifying = true
        showLoading(true)
        resetRequestUI()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check attempts before proceeding
                val attemptsDoc = firestore.collection(FIRESTORE_ATTEMPTS_TRACKING)
                    .document(currentUser.uid)
                    .get()
                    .await()

                val currentTime = System.currentTimeMillis()
                var isLocked = false
                var lockUntil = 0L

                if (attemptsDoc.exists()) {
                    isLocked = attemptsDoc.getBoolean("isLocked") ?: false
                    lockUntil = attemptsDoc.getLong("lockUntil") ?: 0

                    if (isLocked && currentTime < lockUntil) {
                        withContext(Dispatchers.Main) {
                            val hoursLeft = ((lockUntil - currentTime) / (60 * 60 * 1000)).toInt()
                            showResult("Too many attempts! Try again after $hoursLeft hours", false)
                        }
                        return@launch
                    }
                }

                // Increment attempt count
                incrementAttemptsCount(currentUser.uid)

                withContext(Dispatchers.Main) {
                    updateRequestStatus("Connecting to server...", false)
                }

                // ✅ Call Firebase Function
                val result = callFirebaseFreeFireKyc(
                    userId = currentUser.uid,
                    playerUid = freeFireUid,
                    region = region
                )

                withContext(Dispatchers.Main) {
                    handleDirectResponse(result, freeFireUid, currentUser.uid)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleErrorResponse(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    isVerifying = false
                    checkAttemptsLimit()
                }
            }
        }
    }

    // ============================================================
    // ✅ CALL FIREBASE FUNCTION (NO SUPABASE)
    // ============================================================
    private suspend fun callFirebaseFreeFireKyc(
        userId: String,
        playerUid: String,
        region: String
    ): String {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val functionUrl = getFirebaseFunctionUrl()
                val url = URL(functionUrl)
                connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.useCaches = false

                // ✅ Request body - only what Firebase function needs
                val requestBody = JSONObject().apply {
                    put("userId", userId)
                    put("PlayerUid", playerUid)
                    put("region", region)
                }.toString()

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val responseCode = connection.responseCode

                return@withContext if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                } else {
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).use { reader ->
                            reader.readText()
                        }
                    } else {
                        "{\"success\":false,\"error\":\"HTTP $responseCode\"}"
                    }
                }
            } catch (e: Exception) {
                throw Exception("Network call failed: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    // ============================================================
    // ✅ HANDLE RESPONSE FROM FIREBASE FUNCTION
    // ============================================================
    private fun handleDirectResponse(result: String, freeFireUid: String, userId: String) {
        try {
            val response = JSONObject(result)
            val errorCode = response.optString("error", "")

            when {
                errorCode == "LEVEL_TOO_LOW" -> {
                    val currentLevel = response.optInt("currentLevel", 0)
                    val minimumRequired = response.optInt("minimumRequired", 20)
                    showResult("❌ Minimum Level $minimumRequired Required!\nYour level: $currentLevel", false)
                    return
                }
                errorCode == "WRONG_UID" -> {
                    showResult("❌ Invalid UID! Please check your FreeFire ID and region.", false)
                    return
                }
                errorCode == "RS200" -> {
                    showResult("⏳ Request queued. You'll be notified once verified.", false)
                    return
                }
            }

            // ✅ Handle success response
            if (response.optBoolean("success", false)) {
                val data = response.getJSONObject("data")
                val playerName = data.getString("AccountName")
                val playerLevel = data.getInt("AccountLevel")
                val playerUid = data.getString("AccountUID")
                val playerRegion = data.getString("AccountRegion")

                updatePlayerInfo(playerName, playerLevel, playerRegion)

                showResult("✅ Verified! $playerName (Level $playerLevel)", true)

                lifecycleScope.launch(Dispatchers.IO) {
                    saveSuccessfulVerification(userId, freeFireUid, playerName)
                }

                layoutUserInfo.visibility = android.view.View.VISIBLE
                saveVerificationStatus(true, playerName, playerLevel, playerRegion, freeFireUid)
                btnVerify.text = "Verified ✅"
                disableInputs()
                tvAttemptsLeft.text = "Permanently Verified"

            } else {
                val errorMessage = response.optString("error", "Verification failed")
                showResult("❌ $errorMessage", false)
            }
        } catch (e: Exception) {
            showResult("Error: ${e.message}", false)
        }
    }

    private fun updateRequestStatus(status: String, isSuccess: Boolean) {
        runOnUiThread {
            tvRequestStatus.text = status
            tvRequestStatus.setTextColor(
                if (isSuccess) ContextCompat.getColor(this, R.color.success_green)
                else ContextCompat.getColor(this, R.color.colorPrimary)
            )
            tvRequestStatus.visibility = android.view.View.VISIBLE
        }
    }

    private fun handleErrorResponse(error: Exception) {
        val errorMessage = when {
            error.message?.contains("configuration") == true -> "Service configuration error. Please try again later."
            error.message?.contains("401") == true -> "Authentication failed. Please restart the app."
            error.message?.contains("404") == true -> "Player not found with this UID."
            error.message?.contains("500") == true -> "Server error. Please try again later."
            error.message?.contains("Timeout") == true -> "Request timeout. Please check your connection."
            error.message?.contains("Network") == true -> "No internet connection."
            else -> error.message ?: "Please try again"
        }
        showResult("❌ $errorMessage", false)
    }

    private fun updatePlayerInfo(name: String, level: Int, region: String) {
        runOnUiThread {
            tvPlayerName.text = name
            tvPlayerLevel.text = "Level $level"
            tvPlayerRegion.text = region.uppercase()
        }
    }

    private fun disableInputs() {
        runOnUiThread {
            etFreeFireUid.isEnabled = false
            spinnerRegion.isEnabled = false
            btnVerify.isEnabled = false
        }
    }

    private fun showLoading(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            btnVerify.isEnabled = !show
            btnVerify.text = if (show) "Verifying..." else "Verify FreeFire ID"
        }
    }

    private fun showResult(message: String, isSuccess: Boolean) {
        runOnUiThread {
            tvResult.text = message
            tvResult.setTextColor(
                if (isSuccess) ContextCompat.getColor(this, R.color.success_green)
                else if (message.contains("⏳") || message.contains("queued"))
                    ContextCompat.getColor(this, R.color.warning_orange)
                else ContextCompat.getColor(this, R.color.error_red)
            )
            tvResult.visibility = android.view.View.VISIBLE
        }
    }

    private fun resetRequestUI() {
        runOnUiThread {
            tvRequestStatus.visibility = android.view.View.GONE
            tvResult.visibility = android.view.View.GONE
            tvStorageInfo.visibility = android.view.View.GONE
        }
    }

    private fun saveVerificationStatus(
        verified: Boolean,
        playerName: String,
        level: Int,
        region: String,
        freeFireUid: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean("freefire_verified", verified)
                    putString("player_name", playerName)
                    putInt("player_level", level)
                    putString("player_region", region)
                    putString("freefire_uid", freeFireUid)
                    putLong("verification_timestamp", System.currentTimeMillis())
                    apply()
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAttemptsLimit()
    }

    override fun onDestroy() {
        super.onDestroy()
        isVerifying = false
    }
}