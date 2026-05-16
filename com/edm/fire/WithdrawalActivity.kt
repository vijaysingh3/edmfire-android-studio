package com.edm.fire

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.DecimalFormat

class WithdrawalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private lateinit var tvTotalWithdrawalCoins: TextView
    private lateinit var etBankAddress: EditText
    private lateinit var etAmount: EditText
    private lateinit var btnProceed: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvWarning: TextView
    private lateinit var tvGotoRedeem: TextView
    private lateinit var tvCooldown: TextView

    private var firebaseFunctionUrl: String = ""
    private var isProcessing = false
    private var currentRequest: JsonObjectRequest? = null
    private var userWinningCoinsInPaisa: Long = 0
    private var userWithdrawalCount: Int = 0

    private var countDownTimer: CountDownTimer? = null
    private val decimalFormat = DecimalFormat("#.##")

    private companion object {
        const val PREF_NAME                      = "WithdrawalPrefs"
        const val KEY_BANK_ADDRESS               = "saved_bank_address"
        const val KEY_LAST_SUCCESSFUL_ADDRESS    = "last_successful_address"
        const val REMOTE_CONFIG_KEY_FUNCTION_URL = "Withdrawal_fun_url"
        const val MAX_WITHDRAWAL_IN_RUPEES       = 5000L
        const val KEY_COOLDOWN_END_TIME          = "withdrawal_cooldown_end_time"
        const val COOLDOWN_DURATION_MS           = 5 * 60 * 1000L
        const val KEY_ERROR_COOLDOWN_END_TIME    = "withdrawal_error_cooldown_end_time"
        const val ERROR_COOLDOWN_DURATION_MS     = 1 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_withdrawal)

        setupWindowInsets()
        initializeViews()
        setupFirebase()
        initializeRemoteConfig()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeViews() {
        tvTotalWithdrawalCoins = findViewById(R.id.tv_totalwithdrawal_coins)
        etBankAddress          = findViewById(R.id.etBankAddress)
        etAmount               = findViewById(R.id.etAmount)
        btnProceed             = findViewById(R.id.btnProceed)
        progressBar            = findViewById(R.id.progressBar)
        tvStatus               = findViewById(R.id.tvStatus)
        tvWarning              = findViewById(R.id.tvWarning)
        tvGotoRedeem           = findViewById(R.id.goto_redeem_activity)
        tvCooldown             = findViewById(R.id.cooldown_txt)

        progressBar.visibility = View.GONE
        tvStatus.visibility    = View.GONE
        tvCooldown.visibility  = View.GONE
        btnProceed.isEnabled   = false
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
            fetchTimeoutInSeconds         = 30
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        showProgress("Initializing...")

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                hideProgress()
                if (task.isSuccessful) {
                    firebaseFunctionUrl = remoteConfig.getString(REMOTE_CONFIG_KEY_FUNCTION_URL)

                    if (firebaseFunctionUrl.isNotEmpty() && firebaseFunctionUrl.startsWith("https://")) {
                        setupClickListeners()
                        setupInputListeners()
                        loadUserData()
                        loadSavedBankAddress()
                        checkAndApplyCooldown()
                    } else {
                        showErrorState("Service URL not configured")
                        Toast.makeText(this, "Service unavailable. Please contact support.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    showErrorState("Configuration failed")
                    Toast.makeText(this, "Failed to initialize app", Toast.LENGTH_LONG).show()
                }
            }
    }

    // ✅ Get min withdrawal limit in RUPEES based on withdrawal count
    private fun getMinWithdrawalLimitInRupees(withdrawalCount: Int): Long {
        return when (withdrawalCount) {
            in 0..3 -> 1L      // New user - can withdraw 1 rupee
            in 4..49 -> 10L    // Normal user - min 10 rupees
            else -> 50L        // Permanent user - min 50 rupees (50+ withdrawals)
        }
    }

    // ✅ Convert rupees to paisa (for database)
    private fun rupeesToPaisa(rupees: Long): Long {
        return rupees * 100
    }

    // ✅ Convert paisa to rupees (double for decimal support)
    private fun paisaToRupees(paisa: Long): Double {
        return paisa / 100.0
    }

    // ✅ Format coins with decimal support (e.g., 1.5 Coins)
    private fun formatCoins(coinsInPaisa: Long): String {
        val rupees = paisaToRupees(coinsInPaisa)
        val formatted = decimalFormat.format(rupees)
        return if (rupees == 1.0) "$formatted Coin" else "$formatted Coins"
    }

    // ✅ Load user data (WinningCoins in paisa + WithdrawalCount)
    private fun loadUserData() {
        val currentUser = auth.currentUser ?: run {
            userWinningCoinsInPaisa = 0
            userWithdrawalCount = 0
            tvTotalWithdrawalCoins.text = "0 Coins"
            validateInputs()
            return
        }

        db.collection("Users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userWinningCoinsInPaisa = document.getLong("WinningCoins") ?: 0L
                    userWithdrawalCount = document.getLong("WithdrawalCount")?.toInt() ?: 0
                    tvTotalWithdrawalCoins.text = formatCoins(userWinningCoinsInPaisa)
                } else {
                    userWinningCoinsInPaisa = 0
                    userWithdrawalCount = 0
                    tvTotalWithdrawalCoins.text = "0 Coins"
                }
                validateInputs()
            }
            .addOnFailureListener {
                userWinningCoinsInPaisa = 0
                userWithdrawalCount = 0
                tvTotalWithdrawalCoins.text = "0 Coins"
                validateInputs()
            }
    }

    private fun loadSavedBankAddress() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastSuccessful = prefs.getString(KEY_LAST_SUCCESSFUL_ADDRESS, "") ?: ""
        val saved = prefs.getString(KEY_BANK_ADDRESS, "") ?: ""
        val addressToLoad = if (lastSuccessful.isNotEmpty()) lastSuccessful else saved
        if (addressToLoad.isNotEmpty()) {
            etBankAddress.setText(addressToLoad)
            etBankAddress.setSelection(addressToLoad.length)
        }
    }

    private fun saveBankAddressTemporarily() {
        val bankAddress = etBankAddress.text.toString().trim()
        if (bankAddress.isNotEmpty()) {
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_BANK_ADDRESS, bankAddress)
                .apply()
        }
    }

    private fun saveSuccessfulWithdrawalAddress(bankAddress: String) {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_SUCCESSFUL_ADDRESS, bankAddress)
            .putString(KEY_BANK_ADDRESS, bankAddress)
            .apply()
    }

    private fun validateInputs() {
        if (isCooldownActive()) {
            btnProceed.isEnabled = false
            return
        }

        val bankAddress = etBankAddress.text.toString().trim()
        val amountInRupeesText = etAmount.text.toString().trim()
        val isBankValid = bankAddress.isNotEmpty() && bankAddress.length >= 5
        val isAmountValid = amountInRupeesText.isNotEmpty() && amountInRupeesText.toLongOrNull() != null

        if (isAmountValid) {
            val amountInRupees = amountInRupeesText.toLong()
            val minLimitInRupees = getMinWithdrawalLimitInRupees(userWithdrawalCount)
            val maxLimitInRupees = MAX_WITHDRAWAL_IN_RUPEES
            val availableBalanceInRupees = paisaToRupees(userWinningCoinsInPaisa)

            val isAmountInRange = amountInRupees in minLimitInRupees..maxLimitInRupees
            val hasSufficientBalance = amountInRupees <= availableBalanceInRupees

            when {
                amountInRupees < minLimitInRupees -> {
                    tvWarning.text = "Minimum withdrawal is ₹$minLimitInRupees for your account (Withdrawals: $userWithdrawalCount)"
                    tvWarning.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                amountInRupees > maxLimitInRupees -> {
                    tvWarning.text = "Maximum withdrawal is ₹$maxLimitInRupees"
                    tvWarning.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                amountInRupees > availableBalanceInRupees -> {
                    tvWarning.text = "Insufficient balance. Available: ${formatCoins(userWinningCoinsInPaisa)}"
                    tvWarning.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                else -> {
                    tvWarning.text = "Withdraw ₹$amountInRupees - Valid request"
                    tvWarning.setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }
            btnProceed.isEnabled = isBankValid && isAmountInRange && hasSufficientBalance && !isProcessing
        } else {
            val minLimit = getMinWithdrawalLimitInRupees(userWithdrawalCount)
            tvWarning.text = "Enter amount between ₹$minLimit - ₹$MAX_WITHDRAWAL_IN_RUPEES"
            tvWarning.setTextColor(getColor(android.R.color.holo_orange_dark))
            btnProceed.isEnabled = false
        }
    }

    private fun setupInputListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { validateInputs() }
        }

        etAmount.addTextChangedListener(watcher)
        etBankAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateInputs()
                saveBankAddressTemporarily()
            }
        })
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.ivBack).setOnClickListener {
            if (!isProcessing) onBackPressed()
            else Toast.makeText(this, "Please wait, processing withdrawal...", Toast.LENGTH_SHORT).show()
        }

        btnProceed.setOnClickListener {
            if (isCooldownActive()) {
                Toast.makeText(this, "Please wait for cooldown to finish", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isProcessing) processWithdrawal()
        }

        tvGotoRedeem.setOnClickListener {
            startActivity(Intent(this, ActivityRedeemCode::class.java))
        }
    }

    private fun processWithdrawal() {
        if (isProcessing) {
            Toast.makeText(this, "Please wait, processing withdrawal...", Toast.LENGTH_SHORT).show()
            return
        }

        if (isCooldownActive()) {
            Toast.makeText(this, "Please wait for cooldown to finish", Toast.LENGTH_SHORT).show()
            checkAndApplyCooldown()
            return
        }

        if (firebaseFunctionUrl.isEmpty()) {
            Toast.makeText(this, "Withdrawal service unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val bankAddress = etBankAddress.text.toString().trim()
        val amountInRupeesText = etAmount.text.toString().trim()

        if (bankAddress.isEmpty() || bankAddress.length < 5) {
            Toast.makeText(this, "Valid bank address/UPI ID required", Toast.LENGTH_SHORT).show()
            return
        }

        val amountInRupees = amountInRupeesText.toLongOrNull()
        if (amountInRupees == null) {
            Toast.makeText(this, "Invalid amount entered", Toast.LENGTH_SHORT).show()
            return
        }

        val minLimit = getMinWithdrawalLimitInRupees(userWithdrawalCount)
        if (amountInRupees !in minLimit..MAX_WITHDRAWAL_IN_RUPEES) {
            Toast.makeText(this, "Amount must be between ₹$minLimit - ₹$MAX_WITHDRAWAL_IN_RUPEES", Toast.LENGTH_SHORT).show()
            return
        }

        val availableBalanceInRupees = paisaToRupees(userWinningCoinsInPaisa)
        if (amountInRupees > availableBalanceInRupees) {
            Toast.makeText(this, "Insufficient balance. Available: ${formatCoins(userWinningCoinsInPaisa)}", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        btnProceed.isEnabled = false
        etBankAddress.isEnabled = false
        etAmount.isEnabled = false

        setLoadingState(true)
        tvStatus.text = "Processing withdrawal request..."
        tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark))

        val amountInPaisa = rupeesToPaisa(amountInRupees)

        val requestData = JSONObject().apply {
            put("uid", currentUser.uid)
            put("upiId", bankAddress)
            put("bankAddress", bankAddress)
            put("amount", amountInPaisa)
        }

        currentRequest = JsonObjectRequest(
            Request.Method.POST,
            firebaseFunctionUrl,
            requestData,
            { response -> handleApiResponse(response, amountInRupees, bankAddress) },
            { error -> handleApiError(error) }
        ).apply {
            retryPolicy = com.android.volley.DefaultRetryPolicy(
                15000,
                1,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }

        Volley.newRequestQueue(this).add(currentRequest!!)
    }

    private fun handleApiResponse(response: JSONObject, requestedAmountInRupees: Long, bankAddress: String) {
        try {
            val success = response.optBoolean("success", false)
            val message = response.optString("message", "")

            if (success) {
                val transactionId = response.optString("transactionId", "")
                saveSuccessfulWithdrawalAddress(bankAddress)
                startCooldown()
                showSuccessState(requestedAmountInRupees, transactionId)
                Toast.makeText(this, "Withdrawal request submitted successfully!", Toast.LENGTH_LONG).show()
                loadUserData()
            } else {
                startErrorCooldown()
                lockDuringCooldown()
                showErrorState(message)
                Toast.makeText(this, message.ifEmpty { "Withdrawal failed. Please try again." }, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            startErrorCooldown()
            lockDuringCooldown()
            showErrorState("Error processing response: ${e.message}")
            Toast.makeText(this, "Error processing withdrawal", Toast.LENGTH_SHORT).show()
        } finally {
            resetProcessingState()
        }
    }

    private fun handleApiError(error: com.android.volley.VolleyError) {
        val errorMessage = when {
            error.networkResponse != null -> "Network error. Please check connection."
            error is com.android.volley.TimeoutError -> "Request timeout. Please try again."
            else -> "Connection error. Please try again."
        }
        startErrorCooldown()
        lockDuringCooldown()
        showErrorState(errorMessage)
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        resetProcessingState()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COOLDOWN SYSTEM
    // ══════════════════════════════════════════════════════════════════════════

    private fun startCooldown() {
        val endTime = System.currentTimeMillis() + COOLDOWN_DURATION_MS
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_COOLDOWN_END_TIME, endTime)
            .apply()
        beginCountdownUI(endTime)
    }

    private fun startErrorCooldown() {
        val endTime = System.currentTimeMillis() + ERROR_COOLDOWN_DURATION_MS
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ERROR_COOLDOWN_END_TIME, endTime)
            .apply()
        beginCountdownUI(endTime)
    }

    private fun checkAndApplyCooldown() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val successEnd = prefs.getLong(KEY_COOLDOWN_END_TIME, 0L)
        val errorEnd = prefs.getLong(KEY_ERROR_COOLDOWN_END_TIME, 0L)
        val endTime = maxOf(successEnd, errorEnd)
        val remaining = endTime - System.currentTimeMillis()

        if (remaining > 0) {
            lockDuringCooldown()
            beginCountdownUI(endTime)
        } else {
            unlockAfterCooldown()
        }
    }

    private fun beginCountdownUI(endTime: Long) {
        countDownTimer?.cancel()
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0) {
            unlockAfterCooldown()
            return
        }

        tvCooldown.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val prefix = "Next withdrawal after:  "
                val time = String.format("%02d:%02d", minutes, seconds)

                val spannable = android.text.SpannableString("$prefix$time")
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(Color.parseColor("#4CAF50")),
                    0, prefix.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(Color.WHITE),
                    prefix.length, spannable.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    prefix.length, spannable.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                tvCooldown.text = spannable
            }

            override fun onFinish() {
                unlockAfterCooldown()
            }
        }.start()
    }

    private fun lockDuringCooldown() {
        btnProceed.isEnabled = false
        etAmount.isEnabled = false
        etBankAddress.isEnabled = false
    }

    private fun unlockAfterCooldown() {
        tvCooldown.visibility = View.GONE
        countDownTimer?.cancel()
        countDownTimer = null

        if (!isProcessing) {
            etAmount.isEnabled = true
            etBankAddress.isEnabled = true
            validateInputs()
        }
    }

    private fun isCooldownActive(): Boolean {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val successEnd = prefs.getLong(KEY_COOLDOWN_END_TIME, 0L)
        val errorEnd = prefs.getLong(KEY_ERROR_COOLDOWN_END_TIME, 0L)
        return System.currentTimeMillis() < maxOf(successEnd, errorEnd)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private fun setLoadingState(isLoading: Boolean) {
        runOnUiThread {
            if (isLoading) {
                progressBar.visibility = View.VISIBLE
                tvStatus.visibility = View.VISIBLE
                btnProceed.text = "Processing..."
            } else {
                progressBar.visibility = View.GONE
                btnProceed.text = "Proceed"
            }
        }
    }

    private fun showSuccessState(amountInRupees: Long, transactionId: String) {
        runOnUiThread {
            tvStatus.text = "Success! ₹$amountInRupees withdrawn. ID: ${transactionId.take(8)}"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnProceed.text = "Request Submitted"
            btnProceed.isEnabled = false
            etAmount.text.clear()
        }
    }

    private fun showErrorState(errorMessage: String) {
        runOnUiThread {
            tvStatus.text = "Failed: $errorMessage"
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnProceed.text = "Try Again"
        }
    }

    private fun showProgress(message: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = message
            btnProceed.isEnabled = false
        }
    }

    private fun hideProgress() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            btnProceed.isEnabled = true
        }
    }

    private fun resetProcessingState() {
        runOnUiThread {
            isProcessing = false
            setLoadingState(false)
            currentRequest = null

            if (btnProceed.text != "Request Submitted") {
                if (!isCooldownActive()) {
                    etBankAddress.isEnabled = true
                    etAmount.isEnabled = true
                }
                validateInputs()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (firebaseFunctionUrl.isNotEmpty()) {
            loadUserData()
            loadSavedBankAddress()
            checkAndApplyCooldown()
        }
    }

    override fun onBackPressed() {
        if (!isProcessing) super.onBackPressed()
        else Toast.makeText(this, "Please wait, processing withdrawal...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        countDownTimer = null
        currentRequest?.cancel()
        super.onDestroy()
    }
}