package com.edm.fire

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class OtpVerifyActivity : AppCompatActivity() {

    private lateinit var etOtp: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnGoToLogin: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var cardProcess: MaterialCardView
    private lateinit var layoutSuccess: LinearLayout
    private lateinit var scrollContent: androidx.core.widget.NestedScrollView
    private lateinit var tvProcessStep: TextView
    private lateinit var tvProcessDetail: TextView
    private lateinit var tvEmailInfo: TextView
    private lateinit var ivSuccess: ImageView

    private lateinit var email: String
    private var verifyOtpUrl: String = ""
    private var resetPasswordUrl: String = ""

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verify)

        // Views initialize
        etOtp = findViewById(R.id.etOtp)
        etNewPassword = findViewById(R.id.etNewPassword)
        btnVerify = findViewById(R.id.btnVerify)
        btnGoToLogin = findViewById(R.id.btnGoToLogin)
        progressBar = findViewById(R.id.progressBar)
        cardProcess = findViewById(R.id.cardProcess)
        layoutSuccess = findViewById(R.id.layoutSuccess)
        scrollContent = findViewById(R.id.scrollContent)
        tvProcessStep = findViewById(R.id.tvProcessStep)
        tvProcessDetail = findViewById(R.id.tvProcessDetail)
        tvEmailInfo = findViewById(R.id.tvEmailInfo)
        ivSuccess = findViewById(R.id.ivSuccess)

        email = intent.getStringExtra("email") ?: ""
        verifyOtpUrl = intent.getStringExtra("VERIFY_OTP_URL") ?: ""
        resetPasswordUrl = intent.getStringExtra("RESET_PASSWORD_URL") ?: ""

        tvEmailInfo.text = "OTP sent to: $email"

        btnVerify.setOnClickListener {
            verifyOtp()
        }

        btnGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun verifyOtp() {
        val otp = etOtp.text.toString().trim()
        val newPassword = etNewPassword.text.toString().trim()

        if (otp.isEmpty() || newPassword.isEmpty()) {
            Toast.makeText(this, "⚠ Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword.length < 6) {
            Toast.makeText(this, "⚠ Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        showProcess("🔄 Verifying OTP...", "Please wait")
        btnVerify.isEnabled = false

        val url = if (verifyOtpUrl.isNotEmpty()) verifyOtpUrl
        else "https://asia-south1-edm-fire-app.cloudfunctions.net/verifyOtp"

        val jsonBody = JSONObject().apply {
            put("email", email)
            put("otp", otp)
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
                    hideProcess()
                    btnVerify.isEnabled = true
                    Toast.makeText(this@OtpVerifyActivity, "❌ Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(res ?: "{}")
                            if (jsonResponse.optBoolean("success", false)) {
                                val resetToken = jsonResponse.optString("resetToken", "")
                                showProcess("🔒 Resetting Password...", "Updating your account")
                                resetPassword(resetToken, newPassword)
                            } else {
                                val errMsg = jsonResponse.optString("error", "Invalid OTP")
                                hideProcess()
                                btnVerify.isEnabled = true
                                Toast.makeText(this@OtpVerifyActivity, "❌ $errMsg", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            hideProcess()
                            btnVerify.isEnabled = true
                            Toast.makeText(this@OtpVerifyActivity, "❌ Invalid response", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        hideProcess()
                        btnVerify.isEnabled = true
                        Toast.makeText(this@OtpVerifyActivity, "❌ Server error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun resetPassword(token: String, password: String) {
        val url = if (resetPasswordUrl.isNotEmpty()) resetPasswordUrl
        else "https://asia-south1-edm-fire-app.cloudfunctions.net/resetPassword"

        val jsonBody = JSONObject().apply {
            put("resetToken", token)
            put("newPassword", password)
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
                    hideProcess()
                    btnVerify.isEnabled = true
                    Toast.makeText(this@OtpVerifyActivity, "❌ Network error", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    hideProcess()
                    if (response.isSuccessful) {
                        showSuccessOverlay()
                    } else {
                        btnVerify.isEnabled = true
                        Toast.makeText(this@OtpVerifyActivity, "❌ Reset failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun showProcess(step: String, detail: String) {
        cardProcess.visibility = View.VISIBLE
        tvProcessStep.text = step
        tvProcessDetail.text = detail
        cardProcess.alpha = 0f
        cardProcess.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideProcess() {
        cardProcess.animate().alpha(0f).setDuration(200).withEndAction {
            cardProcess.visibility = View.GONE
        }.start()
    }

    private fun showSuccessOverlay() {
        scrollContent.visibility = View.GONE
        cardProcess.visibility = View.GONE

        layoutSuccess.visibility = View.VISIBLE
        layoutSuccess.alpha = 0f
        layoutSuccess.scaleX = 0.85f
        layoutSuccess.scaleY = 0.85f
        layoutSuccess.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        ivSuccess.scaleX = 0f
        ivSuccess.scaleY = 0f
        ivSuccess.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(200)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}