package com.edm.fire

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edm.fire.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var remoteConfig: FirebaseRemoteConfig

    // Remote Config
    private var lastConfigFetchTime: Long = 0
    private val CONFIG_CACHE_DURATION = 24 * 60 * 60 * 1000L
    private val REMOTE_CONFIG_KEY_FUNCTION_URL = "editprofile_url"

    private val API_KEY = "edm_mobile_app_2025_secure_key_12345"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()

        // Initialize Remote Config
        initializeRemoteConfig()

        setupUI()
        loadCurrentProfileData()
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    private fun getSupabaseFunctionUrl(): String {
        val currentTime = System.currentTimeMillis()
        val shouldFetchConfig = currentTime - lastConfigFetchTime > CONFIG_CACHE_DURATION

        return if (shouldFetchConfig) {
            fetchFunctionUrlFromRemoteConfig()
            getCachedFunctionUrl()
        } else {
            getCachedFunctionUrl()
        }
    }

    private fun fetchFunctionUrlFromRemoteConfig() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val functionUrl = remoteConfig.getString(REMOTE_CONFIG_KEY_FUNCTION_URL)
                    lastConfigFetchTime = System.currentTimeMillis()
                    cacheFunctionUrl(functionUrl)
                }
            }
    }

    private fun cacheFunctionUrl(url: String) {
        try {
            val sharedPref = getSharedPreferences("FirebaseConfig", android.content.Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("cached_function_url", url)
                putLong("last_config_fetch_time", System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
        }
    }

    private fun getCachedFunctionUrl(): String {
        return try {
            val sharedPref = getSharedPreferences("FirebaseConfig", android.content.Context.MODE_PRIVATE)
            val cachedUrl = sharedPref.getString("cached_function_url", "")
            lastConfigFetchTime = sharedPref.getLong("last_config_fetch_time", 0)
            cachedUrl ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun setupUI() {
        val backButton = binding.root.findViewById<android.widget.ImageView>(R.id.back_button)
        if (backButton != null) {
            backButton.setOnClickListener {
                finish()
            }
        } else {
            val toolbar = binding.root.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            toolbar?.setNavigationOnClickListener {
                finish()
            }
        }

        binding.btnUpdateProfile.setOnClickListener {
            updateProfileData()
        }
    }

    private fun loadCurrentProfileData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "यूज़र लॉग इन नहीं है।", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.etInGameName.hint = "अपना गेम नाम दर्ज करें"
        binding.etInGameUID.hint = "9-अंकों का UID दर्ज करें"
        binding.etGameLevel.hint = "वर्तमान गेम लेवल दर्ज करें (1-100)"
    }

    private fun updateProfileData() {
        val inGameName = binding.etInGameName.text.toString().trim()
        val inGameUID = binding.etInGameUID.text.toString().trim()
        val gameLevel = binding.etGameLevel.text.toString().trim()

        if (inGameName.isEmpty()) {
            showError("कृपया अपना In-Game Name दर्ज करें")
            binding.etInGameName.requestFocus()
            return
        }

        if (inGameName.length < 3) {
            showError("In-Game Name कम से कम 3 characters का होना चाहिए")
            binding.etInGameName.requestFocus()
            return
        }

        if (inGameUID.isEmpty()) {
            showError("कृपया अपना In-Game UID दर्ज करें")
            binding.etInGameUID.requestFocus()
            return
        }

        if (inGameUID.length < 9) {
            showError("UID कम से कम 9 अंकों का होना चाहिए")
            binding.etInGameUID.requestFocus()
            return
        }

        if (gameLevel.isEmpty()) {
            showError("कृपया अपना Game Level दर्ज करें")
            binding.etGameLevel.requestFocus()
            return
        }

        val inGameUIDLong = inGameUID.toLongOrNull()
        val gameLevelInt = gameLevel.toIntOrNull()

        if (inGameUIDLong == null) {
            showError("कृपया एक वैध UID दर्ज करें")
            binding.etInGameUID.requestFocus()
            return
        }

        if (inGameUIDLong <= 0) {
            showError("UID एक positive number होना चाहिए")
            binding.etInGameUID.requestFocus()
            return
        }

        if (gameLevelInt == null) {
            showError("कृपया एक वैध Level दर्ज करें")
            binding.etGameLevel.requestFocus()
            return
        }

        if (gameLevelInt <= 0) {
            showError("Level एक positive number होना चाहिए")
            binding.etGameLevel.requestFocus()
            return
        }

        if (gameLevelInt > 100) {
            showError("Level 100 से अधिक नहीं हो सकता")
            binding.etGameLevel.requestFocus()
            return
        }

        saveProfileViaSupabaseFunction(inGameName, inGameUIDLong, gameLevelInt)
    }

    private fun saveProfileViaSupabaseFunction(inGameName: String, inGameUID: Long, gameLevel: Int) {
        val functionUrl = getSupabaseFunctionUrl()

        if (functionUrl.isEmpty()) {
            showError("Service URL not available")
            return
        }

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnUpdateProfile.isEnabled = false
        binding.btnUpdateProfile.text = "Updating..."

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("यूज़र लॉग इन नहीं है।")
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnUpdateProfile.isEnabled = true
            binding.btnUpdateProfile.text = "🚀 प्रोफाइल अपडेट करें"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestData = mapOf(
                    "userId" to currentUser.uid,
                    "userName" to inGameName,
                    "inGameUID" to inGameUID,
                    "level" to gameLevel
                )

                val requestBody = gson.toJson(requestData).toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(functionUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("x-api-key", API_KEY)
                    .build()

                val response = client.newCall(request).execute()

                val responseBody = response.body
                val responseBodyString = responseBody?.string()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnUpdateProfile.isEnabled = true
                    binding.btnUpdateProfile.text = "🚀 प्रोफाइल अपडेट करें"

                    if (response.isSuccessful && responseBodyString != null) {
                        try {
                            val jsonResponse = gson.fromJson(responseBodyString, Map::class.java)

                            if (jsonResponse["success"] == true) {
                                showSuccess(jsonResponse["message"].toString())

                                android.os.Handler().postDelayed({
                                    finish()
                                }, 1500)
                            } else {
                                showError(jsonResponse["error"].toString())
                            }
                        } catch (e: Exception) {
                            showError("Response parsing error: ${e.message}")
                        }
                    } else {
                        val errorCode = response.code
                        val errorMessage = response.message

                        when (errorCode) {
                            401 -> showError("Authentication failed: Invalid API key")
                            400 -> showError("Validation error: Check your input data")
                            503 -> showError("Service temporarily unavailable")
                            else -> showError("Server error: $errorCode")
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnUpdateProfile.isEnabled = true
                    binding.btnUpdateProfile.text = "🚀 प्रोफाइल अपडेट करें"

                    showError("Network error: ${e.message}")
                }
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        try {
            val ivSuccess = binding.root.findViewById<android.widget.ImageView>(R.id.ivSuccess)
            ivSuccess?.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}