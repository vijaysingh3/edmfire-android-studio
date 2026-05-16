package com.edm.fire

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BanWarningDialog(
    private val context: Context,
    private val bannedReason: String,
    private val bannedPeriod: Date?,
    private val onUnbanSuccess: () -> Unit
) {

    private lateinit var dialog: Dialog
    private var countDownTimer: CountDownTimer? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()

    fun show() {
        dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_ban_warning)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val tvBanReason: TextView = dialog.findViewById(R.id.tvBanReason)
        val tvBanDuration: TextView = dialog.findViewById(R.id.tvBanDuration)
        val tvCountdownLabel: TextView = dialog.findViewById(R.id.tvCountdownLabel)
        val tvCountdown: TextView = dialog.findViewById(R.id.tvCountdown)
        val tvWarning: TextView = dialog.findViewById(R.id.tvWarning)
        val btnHelp: Button = dialog.findViewById(R.id.btnHelp)
        val btnExit: Button = dialog.findViewById(R.id.btnExit)

        // set reason
        tvBanReason.text = bannedReason

        // check if temporary or permanent
        val isPermanent = bannedPeriod == null

        if (isPermanent) {
            tvBanDuration.text = "Permanent Ban"
            tvCountdownLabel.visibility = View.GONE
            tvCountdown.visibility = View.GONE
            tvWarning.text = "⚠️ Permanent ban. Contact support for help."
        } else {
            // format and show ban expiry date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale("en", "IN"))
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            tvBanDuration.text = "Until: ${dateFormat.format(bannedPeriod)}"

            // start countdown timer
            tvCountdownLabel.visibility = View.VISIBLE
            tvCountdown.visibility = View.VISIBLE
            startCountdown(bannedPeriod, tvCountdown)
        }

        btnHelp.setOnClickListener {
            dialog.dismiss()
            countDownTimer?.cancel()
            val intent = Intent(context, HelpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        btnExit.setOnClickListener {
            dialog.dismiss()
            countDownTimer?.cancel()
            (context as? android.app.Activity)?.finishAffinity()
        }

        dialog.show()

        // listen for unban from Firestore
        listenForUnban()
    }

    private fun startCountdown(expiryDate: Date, tvCountdown: TextView) {
        countDownTimer?.cancel()

        val currentTime = Date()
        var remainingMillis = expiryDate.time - currentTime.time

        if (remainingMillis <= 0) {
            tvCountdown.text = "Expired"
            callAutoUnban()
            return
        }

        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvCountdown.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
                tvCountdown.text = "Unbanning..."
                callAutoUnban()
            }
        }.start()
    }

    private fun callAutoUnban() {
        val userId = auth.currentUser?.uid ?: return

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val functionUrl = remoteConfig.getString("autoUnban_function")

        if (functionUrl.isEmpty() || functionUrl == "autoUnban_function") {
            return
        }

        val json = JSONObject()
        json.put("userId", userId)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(functionUrl)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Firestore listener will handle dialog dismissal
                }
            }
        })
    }

    private fun listenForUnban() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val currentStatus = snapshot.getString("AccountStatus")

                if (currentStatus == "Active") {
                    countDownTimer?.cancel()
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                    onUnbanSuccess()
                }
            }
    }
}