package com.edm.fire

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.bumptech.glide.Glide
import org.json.JSONObject

class PaymentVerification : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etUTR: EditText
    private lateinit var btnVerify: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var imageView: ImageView
    private lateinit var scrollView: ScrollView

    private lateinit var auth: FirebaseAuth
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private var appwriteFunctionUrl = ""

    // Double Click Prevention
    private var isVerifying = false
    private var isConfigLoaded = false
    private var currentRequest: JsonObjectRequest? = null

    // Remote Config Keys
    companion object {
        private const val REMOTE_CONFIG_KEY = "Fun_PaymentVerification"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment_verification)

        setupWindowInsets()
        initializeFirebase()
        initializeViews()
        setupClickListeners()
        setupUTRInput()
        fetchRemoteConfig()

        // Load image with Glide
        Glide.with(this).load(R.drawable.utr_demo).into(imageView)

        // Auto‑scroll to the image after 2 seconds
        scrollView.postDelayed({
            imageView?.let {
                scrollView.smoothScrollTo(0, it.top)
            }
        }, 2000)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()

        remoteConfig = FirebaseRemoteConfig.getInstance()

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 1800 // 30 minutes cache
            fetchTimeoutInSeconds = 15
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    private fun initializeViews() {
        // Initialize all UI components
        toolbar = findViewById(R.id.toolbar)
        etUTR = findViewById(R.id.etUTR)
        btnVerify = findViewById(R.id.btnVerify)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvInstructions = findViewById(R.id.tvInstructions)
        imageView = findViewById(R.id.imageView)
        scrollView = findViewById(R.id.main)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.verify_payment_toolbar_title_18)
        toolbar.setNavigationOnClickListener {
            if (!isVerifying) {
                onBackPressed()
            } else {
                Toast.makeText(this, getString(R.string.verification_in_progress_toast_0), Toast.LENGTH_SHORT).show()
            }
        }

        // Initial UI state
        progressBar.visibility = View.GONE
        tvStatus.visibility = View.GONE
        btnVerify.isEnabled = false
        isVerifying = false
        isConfigLoaded = false
    }

    private fun fetchRemoteConfig() {
        // Show loading for remote config
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = getString(R.string.loading_config_status_2)
        btnVerify.isEnabled = false

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val updated = task.result

                    // Get the URL from Remote Config
                    appwriteFunctionUrl = remoteConfig.getString(REMOTE_CONFIG_KEY)

                    if (appwriteFunctionUrl.isNotEmpty()) {
                        isConfigLoaded = true
                        hideLoadingState()
                        btnVerify.isEnabled = etUTR.text.length == 12
                    } else {
                        handleConfigError("Configuration empty")
                    }
                } else {
                    handleConfigError("Configuration load failed")
                }
            }
            .addOnFailureListener { exception ->
                handleConfigError("Configuration error")
            }
    }

    private fun handleConfigError(errorMessage: String) {
        isConfigLoaded = false
        hideLoadingState()
        btnVerify.isEnabled = false

        Toast.makeText(this, getString(R.string.config_error_restart_toast_1), Toast.LENGTH_LONG).show()
    }

    private fun hideLoadingState() {
        progressBar.visibility = View.GONE
        tvStatus.visibility = View.GONE
    }

    private fun setupUTRInput() {
        // UTR input filter - only 12 digits
        etUTR.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                // Filter only digits
                val filtered = input.filter { it.isDigit() }
                if (input != filtered) {
                    etUTR.setText(filtered)
                    etUTR.setSelection(filtered.length)
                }

                // Enable button only if 12 digits AND not currently verifying AND config loaded
                btnVerify.isEnabled = filtered.length == 12 && !isVerifying && isConfigLoaded

                // Update instructions
                updateInstructions(filtered.length)
            }
        })
    }

    private fun updateInstructions(length: Int) {
        when {
            !isConfigLoaded -> {
                tvInstructions.text = getString(R.string.loading_config_status_2)
                tvInstructions.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
            length == 0 -> {
                tvInstructions.text = getString(R.string.enter_utr_instruction_3)
                tvInstructions.setTextColor(getColor(android.R.color.darker_gray))
            }
            length < 12 -> {
                tvInstructions.text = String.format(getString(R.string.enter_more_digits_instruction_4), 12 - length)
                tvInstructions.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
            else -> {
                if (isVerifying) {
                    tvInstructions.text = getString(R.string.verification_progress_instruction_5)
                    tvInstructions.setTextColor(getColor(android.R.color.holo_blue_dark))
                } else {
                    tvInstructions.text = getString(R.string.ready_to_verify_instruction_6)
                    tvInstructions.setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }
        }
    }

    private fun setupClickListeners() {
        btnVerify.setOnClickListener {
            verifyUTR()
        }
    }

    private fun verifyUTR() {
        // Security checks
        if (isVerifying) {
            Toast.makeText(this, getString(R.string.verification_in_progress_toast_0), Toast.LENGTH_SHORT).show()
            return
        }

        if (!isConfigLoaded) {
            Toast.makeText(this, getString(R.string.config_not_ready_toast_7), Toast.LENGTH_SHORT).show()
            fetchRemoteConfig()
            return
        }

        val utr = etUTR.text.toString().trim()

        // Final validation
        if (utr.length != 12) {
            Toast.makeText(this, getString(R.string.invalid_utr_toast_8), Toast.LENGTH_SHORT).show()
            return
        }

        // Check user authentication
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.login_to_continue_toast_9), Toast.LENGTH_SHORT).show()
            return
        }

        val uid = currentUser.uid

        isVerifying = true
        btnVerify.isEnabled = false
        etUTR.isEnabled = false

        setLoadingState(true)

        val requestData = JSONObject().apply {
            put("uid", uid)
            put("utr", utr)
        }

        currentRequest = JsonObjectRequest(
            Request.Method.POST,
            appwriteFunctionUrl,
            requestData,
            { response ->
                handleApiResponse(response, utr)
            },
            { error ->
                handleApiError()
            }
        ).apply {
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                25000,
                0,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }

        // Add request to queue
        Volley.newRequestQueue(this).add(currentRequest!!)
    }

    private fun handleApiResponse(response: JSONObject, utr: String) {
        try {
            val jsonData = response.optJSONObject("json") ?: response
            val success = jsonData.optBoolean("success", false)
            val message = jsonData.optString("message", "")

            if (success) {
                // Payment successful
                val amount = jsonData.optDouble("amount", 0.0)
                val bonusCoins = jsonData.optInt("bonusCoins", 0)
                val totalCoins = jsonData.optInt("totalCoins", 0)
                val transactionId = jsonData.optString("transactionId", "")

                showSuccessState(amount, bonusCoins, totalCoins, utr, transactionId)
                Toast.makeText(this, getString(R.string.payment_verified_toast_11), Toast.LENGTH_LONG).show()

            } else {
                // Payment failed
                showErrorState(message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            showErrorState(getString(R.string.error_processing_response_toast_12))
            Toast.makeText(this, getString(R.string.error_processing_response_toast_12), Toast.LENGTH_SHORT).show()
        } finally {
            resetVerifyingState()
        }
    }

    private fun handleApiError() {
        showErrorState(getString(R.string.network_error_toast_13))
        Toast.makeText(this, getString(R.string.network_error_toast_13), Toast.LENGTH_LONG).show()
        resetVerifyingState()
    }

    private fun setLoadingState(isLoading: Boolean) {
        runOnUiThread {
            if (isLoading) {
                progressBar.visibility = View.VISIBLE
                btnVerify.visibility = View.GONE
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.verifying_utr_status_10)
                tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark))
            } else {
                progressBar.visibility = View.GONE
                btnVerify.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSuccessState(amount: Double, bonusCoins: Int, totalCoins: Int, utr: String, transactionId: String) {
        runOnUiThread {
            tvStatus.text = "Payment Verified!\n\n" +
                    "Amount: ₹$amount\n" +
                    "Bonus: $bonusCoins coins\n" +
                    "Total: $totalCoins coins"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnVerify.text = getString(R.string.verified_button_text_15)
            btnVerify.isEnabled = false
            etUTR.isEnabled = false
        }
    }

    private fun showErrorState(errorMessage: String) {
        runOnUiThread {
            // Reverted to hardcoded string to prevent potential format errors
            tvStatus.text = "Verification Failed\n\n$errorMessage"
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnVerify.text = getString(R.string.verify_payment_button_text_17)
            btnVerify.isEnabled = true
            etUTR.isEnabled = true
        }
    }

    private fun resetVerifyingState() {
        runOnUiThread {
            isVerifying = false
            setLoadingState(false)
            currentRequest = null

            if (btnVerify.text != getString(R.string.verified_button_text_15)) {
                btnVerify.isEnabled = etUTR.text.length == 12 && isConfigLoaded
                etUTR.isEnabled = true
            }

            updateInstructions(etUTR.text.length)
        }
    }

    override fun onBackPressed() {
        if (isVerifying) {
            Toast.makeText(this, getString(R.string.verification_in_progress_toast_0), Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        currentRequest?.let {
            Volley.newRequestQueue(this).cancelAll { true }
        }
        super.onDestroy()
    }
}