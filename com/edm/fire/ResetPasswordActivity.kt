package com.edm.fire

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var etEmail: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnOtpReset: MaterialButton
    private lateinit var btnBackToLogin: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvCountdown: TextView
    private lateinit var tvResetInfo: TextView
    private lateinit var cardStatus: MaterialCardView

    private var countDownTimer: CountDownTimer? = null
    private var isCountdownActive = false
    private val COUNTDOWN_TIME = 30000L

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var sendOtpEmailUrl: String = ""
    private var verifyOtpUrl: String = ""
    private var resetPasswordUrl: String = ""

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reset_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupClickListeners()
        setupToolbar()
        setupRemoteConfig()
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        btnOtpReset = findViewById(R.id.btnOtpReset)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)
        progressBar = findViewById(R.id.progressBar)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvResetInfo = findViewById(R.id.tvResetInfo)
        cardStatus = findViewById(R.id.cardStatus)

        cardStatus.visibility = View.GONE
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                sendOtpEmailUrl = remoteConfig.getString("SEND_OTP_EMAIL_URL")
                verifyOtpUrl = remoteConfig.getString("VERIFY_OTP_URL")
                resetPasswordUrl = remoteConfig.getString("RESET_PASSWORD_URL")
            } else {
                sendOtpEmailUrl = "https://asia-south1-edm-fire-app.cloudfunctions.net/sendOtpEmail"
                verifyOtpUrl = "https://asia-south1-edm-fire-app.cloudfunctions.net/verifyOtp"
                resetPasswordUrl = "https://asia-south1-edm-fire-app.cloudfunctions.net/resetPassword"
            }
        }
    }

    private fun setupClickListeners() {
        btnOtpReset.setOnClickListener {
            if (!isCountdownActive) {
                sendOtpReset()
            } else {
                Toast.makeText(this, "⏱ Please wait for timer", Toast.LENGTH_SHORT).show()
            }
        }

        btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun sendOtpReset() {
        val email = etEmail.text.toString().trim()

        if (TextUtils.isEmpty(email)) {
            etEmail.error = "⚠ Enter email"
            etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "⚠ Invalid email format"
            etEmail.requestFocus()
            return
        }

        showStatus("📩 Sending OTP...", true)
        btnOtpReset.isEnabled = false

        val url = if (sendOtpEmailUrl.isNotEmpty()) sendOtpEmailUrl
        else "https://asia-south1-edm-fire-app.cloudfunctions.net/sendOtpEmail"

        val jsonBody = JSONObject().apply {
            put("email", email)
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnOtpReset.isEnabled = true
                    showStatus("❌ Network error: ${e.message}", false)
                    Toast.makeText(this@ResetPasswordActivity, "❌ Network error", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                runOnUiThread {
                    btnOtpReset.isEnabled = true
                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(res ?: "{}")
                            if (jsonResponse.optBoolean("success", false)) {
                                hideStatus()
                                Toast.makeText(this@ResetPasswordActivity, "✅ OTP sent! Check email", Toast.LENGTH_SHORT).show()
                                startCountdownTimer()

                                val intent = Intent(this@ResetPasswordActivity, OtpVerifyActivity::class.java)
                                intent.putExtra("email", email)
                                intent.putExtra("VERIFY_OTP_URL", verifyOtpUrl)
                                intent.putExtra("RESET_PASSWORD_URL", resetPasswordUrl)
                                startActivity(intent)
                            } else {
                                // yaha successful request par backend error check ho raha hai
                                val errorMsg = jsonResponse.optString("error", jsonResponse.optString("message", "Failed to send OTP"))
                                val displayMsg = getReadableErrorMessage(errorMsg)
                                showStatus("❌ $displayMsg", false)
                                Toast.makeText(this@ResetPasswordActivity, "❌ $displayMsg", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            showStatus("❌ Invalid response", false)
                            Toast.makeText(this@ResetPasswordActivity, "❌ Invalid response", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // yaha 4xx ya 5xx server errors handle honge (jaise email not found hone par backend 404/400 de sakta hai)
                        try {
                            val jsonResponse = JSONObject(res ?: "{}")
                            val errorMsg = jsonResponse.optString("error", jsonResponse.optString("message", "Server error: ${response.code}"))
                            val displayMsg = getReadableErrorMessage(errorMsg)

                            showStatus("❌ $displayMsg", false)
                            Toast.makeText(this@ResetPasswordActivity, "❌ $displayMsg", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            showStatus("❌ Server error: ${response.code}", false)
                            Toast.makeText(this@ResetPasswordActivity, "❌ Server error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    // Helper function taaki user ko samajh aane wala clear error message mile
    private fun getReadableErrorMessage(rawError: String): String {
        val lowerError = rawError.lowercase()
        return when {
            lowerError.contains("user-not-found") || lowerError.contains("user not found") ||
                    lowerError.contains("no user") || lowerError.contains("not registered") -> {
                "This email is not registered with us!"
            }
            lowerError.contains("invalid-email") -> {
                "Invalid email format provided!"
            }
            else -> rawError // default to original error
        }
    }

    private fun startCountdownTimer() {
        isCountdownActive = true
        btnOtpReset.isEnabled = false
        tvCountdown.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(COUNTDOWN_TIME, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvCountdown.text = "⏱ Resend in: $seconds sec"
            }

            override fun onFinish() {
                isCountdownActive = false
                btnOtpReset.isEnabled = true
                tvCountdown.visibility = View.GONE
                showStatus("✉ You can send again now.", false)
            }
        }.start()
    }

    private fun showStatus(message: String, showProgress: Boolean) {
        cardStatus.visibility = View.VISIBLE
        tvResetInfo.text = message

        cardStatus.alpha = 0f
        cardStatus.animate().alpha(1f).setDuration(300).start()

        if (showProgress) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }
    }

    private fun hideStatus() {
        cardStatus.animate().alpha(0f).setDuration(200).withEndAction {
            cardStatus.visibility = View.GONE
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}