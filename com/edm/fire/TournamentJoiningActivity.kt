package com.edm.fire

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class TournamentJoiningActivity : AppCompatActivity() {

    private lateinit var tvTournamentInfo: TextView
    private lateinit var tvSelectedSlot: TextView
    private lateinit var tvJoiningFeeHighlight: TextView // 🔥 New variable for big fee text
    private lateinit var tvBalanceInfo: TextView
    private lateinit var btnProceedPayment: Button
    private lateinit var btnCancel: Button
    private lateinit var progressOverlay: CardView
    private lateinit var tvProgressText: TextView

    private var tournamentId: String = ""
    private var tournamentType: String = ""
    private var selectedSlotNumber: Int = 0
    private var joiningFee: Int = 0  // in PAISA
    private var currentUserId: String = ""

    // Tournament details
    private var tournamentTitle: String = ""
    private var tournamentDateTime: String = ""
    private var tournamentPricePool: Int = 0  // in PAISA

    // Balance cache (in PAISA)
    private var cachedTopUp: Int = 0
    private var cachedWinning: Int = 0
    private var cachedRefBonus: Int = 0

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var tournamentFunctionUrl: String = ""
    private var supabaseAdminAnonKey: String = ""
    private var baseDatabaseUrl: String = ""

    private val client = OkHttpClient()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Custom dialog reference
    private var confirmationDialog: Dialog? = null

    // ✅ Bank Method: Paisa → Coins with DECIMAL support
    private fun paisaToCoins(paisa: Int): Double {
        return paisa / 100.0
    }

    // ✅ Format coins with proper decimal display
    private fun formatCoins(coins: Double): String {
        return if (coins % 1 == 0.0) {
            coins.toInt().toString()
        } else {
            val formatted = String.format(Locale.US, "%.2f", coins)
            when {
                formatted.endsWith(".00") -> formatted.dropLast(3)
                formatted.endsWith("0") -> formatted.dropLast(1)
                else -> formatted
            }
        }
    }

    // ✅ Format balance display
    private fun formatBalance(paisa: Int): String {
        val coins = paisaToCoins(paisa)
        return formatCoins(coins)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tournament_joining)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = auth.currentUser?.uid ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        initializeRemoteConfig()
    }

    private fun initViews() {
        tvTournamentInfo = findViewById(R.id.tvTournamentInfo)
        tvSelectedSlot = findViewById(R.id.tvSelectedSlot)
        tvJoiningFeeHighlight = findViewById(R.id.tvJoiningFeeHighlight) // 🔥 Initialize new text
        tvBalanceInfo = findViewById(R.id.tvBalanceInfo)
        btnProceedPayment = findViewById(R.id.btnProceedPayment)
        btnCancel = findViewById(R.id.btnCancel)
        progressOverlay = findViewById(R.id.progressOverlay)
        tvProgressText = findViewById(R.id.tvProgressText)
    }

    private fun showProgress(message: String = "Processing...") {
        runOnUiThread {
            progressOverlay.visibility = View.VISIBLE
            tvProgressText.text = message
            btnProceedPayment.isEnabled = false
            btnCancel.isEnabled = false
        }
    }

    private fun hideProgress() {
        runOnUiThread {
            progressOverlay.visibility = View.GONE
            btnProceedPayment.isEnabled = true
            btnCancel.isEnabled = true
        }
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
            fetchTimeoutInSeconds = 30
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        showProgress("Initializing...")

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener {
                baseDatabaseUrl = remoteConfig.getString("FirebaseDatabase_url")
                tournamentFunctionUrl = remoteConfig.getString("hyper_tournament_joining")
                supabaseAdminAnonKey = remoteConfig.getString("Supabase_admin_anon_key")

                getIntentData()
                loadTournamentDetails()
                setupButtons()
            }
    }

    private fun getIntentData() {
        tournamentType = intent.getStringExtra("TOURNAMENT_TYPE") ?: ""
        tournamentId = intent.getStringExtra("TOURNAMENT_ID") ?: ""
        selectedSlotNumber = intent.getIntExtra("SELECTED_SLOT_NUMBER", 0)

        if (tournamentType.isEmpty() || tournamentId.isEmpty() || selectedSlotNumber == 0) {
            Toast.makeText(this, "Invalid tournament data", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 🔥 REST API - Load tournament details from TournamentMeta node
    private fun loadTournamentDetails() {
        showProgress("Loading tournament...")

        val url = "$baseDatabaseUrl/Tournaments/TournamentMeta/$tournamentType/$tournamentId.json"

        UniversalReader.readJson(
            url = url,
            onResult = { jsonString ->
                if (jsonString.isNotEmpty() && jsonString != "null" && jsonString != "{}") {
                    try {
                        val obj = JsonParser.parseString(jsonString).asJsonObject

                        joiningFee = if (obj.has("JoiningFee") && !obj.get("JoiningFee").isJsonNull) {
                            obj.get("JoiningFee").asInt
                        } else 0

                        tournamentTitle = if (obj.has("Title") && !obj.get("Title").isJsonNull) {
                            obj.get("Title").asString
                        } else "Tournament"

                        tournamentDateTime = if (obj.has("DateTime") && !obj.get("DateTime").isJsonNull) {
                            obj.get("DateTime").asString
                        } else ""

                        tournamentPricePool = if (obj.has("PrizePool") && !obj.get("PrizePool").isJsonNull) {
                            obj.get("PrizePool").asInt
                        } else 0

                        // ✅ Convert PAISA to Coins for display
                        val joiningFeeCoins = formatBalance(joiningFee)
                        val prizePoolCoins = formatBalance(tournamentPricePool)

                        runOnUiThread {
                            tvTournamentInfo.text = String.format(
                                Locale.getDefault(),
                                getString(R.string.tournament_info_details_0),
                                tournamentTitle, tournamentType, tournamentDateTime,
                                joiningFeeCoins, prizePoolCoins
                            )
                            tvSelectedSlot.text = "Selected Slot: $selectedSlotNumber"

                            // 🔥 Set single big highlighted text for fee
                            tvJoiningFeeHighlight.text = "Entry Fee: $joiningFeeCoins Coins"

                            loadUserBalance()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            hideProgress()
                            Toast.makeText(this, "Error parsing tournament data", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        hideProgress()
                        Toast.makeText(this, "Tournament not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    hideProgress()
                    Toast.makeText(this, "Network error: $error", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        )
    }

    private fun loadUserBalance() {
        firestore.collection("Users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                hideProgress()

                if (document.exists()) {
                    cachedTopUp = document.getLong("TopUpCoins")?.toInt() ?: 0
                    cachedWinning = document.getLong("WinningCoins")?.toInt() ?: 0
                    cachedRefBonus = document.getLong("MyReferralBonus")?.toInt() ?: 0
                    val totalCoinsInPaisa = cachedTopUp + cachedWinning

                    // ✅ Convert PAISA to Coins for display
                    val topUpCoins = formatBalance(cachedTopUp)
                    val winningCoins = formatBalance(cachedWinning)
                    val refBonusCoins = formatBalance(cachedRefBonus)
                    val totalCoins = formatBalance(totalCoinsInPaisa)

                    tvBalanceInfo.text = String.format(
                        Locale.getDefault(),
                        getString(R.string.user_balance_details_1),
                        topUpCoins, winningCoins, refBonusCoins, totalCoins
                    )
                    btnProceedPayment.isEnabled = true
                }
            }
            .addOnFailureListener {
                hideProgress()
                Toast.makeText(this, "Failed to load balance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupButtons() {
        btnProceedPayment.setOnClickListener {
            validateAndJoin()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    // 🔥 REST API - Remove user selection from RealtimeSelection node
    private fun removeUserSelection() {
        val url = "$baseDatabaseUrl/RealtimeSelection/$tournamentType/$tournamentId/$selectedSlotNumber.json"

        val request = Request.Builder()
            .url(url)
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TournamentJoining", "Failed to remove selection: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun validateAndJoin() {
        if (supabaseAdminAnonKey.isEmpty()) {
            Toast.makeText(this, "Configuration error. Please restart.", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress("Validating...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val validation = validateTournament()

                withContext(Dispatchers.Main) {
                    if (validation.success) {
                        cachedTopUp = validation.topUp
                        cachedWinning = validation.winning
                        cachedRefBonus = validation.refBonus
                        showCustomPaymentDialog()
                    } else {
                        handleError(validation.error, validation.message, validation.shortage)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    Toast.makeText(this@TournamentJoiningActivity, "Network error. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun validateTournament(): ValidationResult {
        val requestJson = JSONObject().apply {
            put("action", "validate_tournament_join")
            put("userId", currentUserId)
            put("tournamentType", tournamentType)
            put("tournamentId", tournamentId)
            put("slotNumber", selectedSlotNumber)
            put("joiningFee", joiningFee)
            put("appVersion", getAppVersion())
            put("skipVersionCheck", false)
        }

        val response = makeRequest(requestJson)
        val isSuccess = response.optBoolean("success", false)

        return if (isSuccess) {
            ValidationResult(
                success = true,
                topUp = response.optInt("topUp", cachedTopUp),
                winning = response.optInt("winning", cachedWinning),
                refBonus = response.optInt("refBonus", cachedRefBonus)
            )
        } else {
            ValidationResult(
                success = false,
                error = response.optString("error"),
                message = response.optString("message"),
                shortage = response.optInt("shortage", 0)
            )
        }
    }

    private suspend fun processPayment(): PaymentResult {
        val requestJson = JSONObject().apply {
            put("action", "process_tournament_payment")
            put("userId", currentUserId)
            put("tournamentType", tournamentType)
            put("tournamentId", tournamentId)
            put("slotNumber", selectedSlotNumber)
            put("joiningFee", joiningFee)
            put("appVersion", getAppVersion())
            put("skipVersionCheck", false)
        }

        val response = makeRequest(requestJson)
        val isSuccess = response.optBoolean("success", false)

        return if (isSuccess) {
            PaymentResult(
                success = true,
                refUsed = response.optInt("refUsed", 0),
                finalCost = response.optInt("finalCost", joiningFee)
            )
        } else {
            PaymentResult(
                success = false,
                error = response.optString("error"),
                message = response.optString("message")
            )
        }
    }

    private suspend fun makeRequest(json: JSONObject): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = java.net.URL(tournamentFunctionUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $supabaseAdminAnonKey")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            connection.outputStream.use { it.write(json.toString().toByteArray()) }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            JSONObject(responseText)
        }
    }

    private fun showCustomPaymentDialog() {
        confirmationDialog?.dismiss()

        confirmationDialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_payment_confirmation, null)
            setContentView(dialogView)
        }

        val totalCoinsInPaisa = cachedTopUp + cachedWinning

        // ✅ Convert PAISA to Coins for display
        val joiningFeeCoins = formatBalance(joiningFee)
        val topUpCoins = formatBalance(cachedTopUp)
        val winningCoins = formatBalance(cachedWinning)
        val refBonusCoins = formatBalance(cachedRefBonus)
        val totalCoins = formatBalance(totalCoinsInPaisa)

        confirmationDialog?.findViewById<TextView>(R.id.tvTournamentType)?.text = tournamentType
        confirmationDialog?.findViewById<TextView>(R.id.tvTournamentId)?.text = tournamentId
        confirmationDialog?.findViewById<TextView>(R.id.tvSlotNumber)?.text = selectedSlotNumber.toString()
        confirmationDialog?.findViewById<TextView>(R.id.tvJoiningFee)?.text = "$joiningFeeCoins Coins"
        confirmationDialog?.findViewById<TextView>(R.id.tvTopUpCoins)?.text = "$topUpCoins Coins"
        confirmationDialog?.findViewById<TextView>(R.id.tvWinningCoins)?.text = "$winningCoins Coins"
        confirmationDialog?.findViewById<TextView>(R.id.tvReferralBonus)?.text = "$refBonusCoins Coins"
        confirmationDialog?.findViewById<TextView>(R.id.tvTotalCoins)?.text = "$totalCoins Coins"

        confirmationDialog?.findViewById<Button>(R.id.btnConfirmPay)?.setOnClickListener {
            confirmationDialog?.dismiss()
            executePayment()
        }

        confirmationDialog?.findViewById<Button>(R.id.btnCancelPay)?.setOnClickListener {
            confirmationDialog?.dismiss()
            hideProgress()
        }

        confirmationDialog?.show()
    }

    private fun executePayment() {
        showProgress("Processing payment...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = processPayment()

                withContext(Dispatchers.Main) {
                    hideProgress()

                    if (result.success) {
                        val finalCostCoins = formatBalance(result.finalCost)
                        Toast.makeText(
                            this@TournamentJoiningActivity,
                            "✅ Joined! Fee: $finalCostCoins Coins",
                            Toast.LENGTH_LONG
                        ).show()
                        showSuccessAndExit(result.refUsed, result.finalCost)
                    } else {
                        handleError(result.error, result.message, 0)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    Toast.makeText(this@TournamentJoiningActivity, "Payment failed. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleError(errorType: String, message: String, shortage: Int) {
        hideProgress()

        when {
            errorType.contains("VERSION") -> {
                showUpdateDialog()
            }
            errorType == "KYC_REQUIRED" -> {
                showKYCIncompletePopup()
            }
            errorType == "LOW_BALANCE" -> {
                val need = if (shortage > 0) shortage else joiningFee - (cachedTopUp + cachedWinning)
                val needCoins = formatBalance(need)
                showLowBalanceAlert(needCoins)
            }
            errorType == "Slot already taken" || errorType.contains("occupied") -> {
                Toast.makeText(this, "Slot already taken! Please select another slot.", Toast.LENGTH_LONG).show()
                finish()
            }
            errorType == "Already joined" -> {
                Toast.makeText(this, "You have already joined this tournament!", Toast.LENGTH_LONG).show()
                finish()
            }
            errorType == "Tournament not found" -> {
                Toast.makeText(this, "Tournament no longer exists.", Toast.LENGTH_LONG).show()
                finish()
            }
            else -> {
                Toast.makeText(this, if (message.isNotEmpty()) message else "Failed. Try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("📱 Update Required")
            .setMessage("Please update your app from edmfire.in to continue.")
            .setPositiveButton("Download") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://edmfire.in")))
                finish()
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showKYCIncompletePopup() {
        AlertDialog.Builder(this)
            .setTitle("📋 KYC Required")
            .setMessage("Complete KYC verification to join tournaments.")
            .setPositiveButton("Verify Now") { _, _ ->
                startActivity(Intent(this, ActivityGameIdVerification::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLowBalanceAlert(shortageCoins: String) {
        val message = String.format(
            Locale.getDefault(),
            getString(R.string.low_balance_message_3),
            shortageCoins
        )
        AlertDialog.Builder(this)
            .setTitle("⚠️ Low Balance")
            .setMessage(message)
            .setPositiveButton("Add Coins") { _, _ ->
                startActivity(Intent(this, DepositActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSuccessAndExit(refUsed: Int, finalCost: Int) {
        val joiningFeeCoins = formatBalance(joiningFee)
        val refUsedCoins = formatBalance(refUsed)
        val finalCostCoins = formatBalance(finalCost)

        val message = String.format(
            Locale.getDefault(),
            getString(R.string.tournament_join_success_message_5),
            tournamentTitle, tournamentType, selectedSlotNumber,
            joiningFeeCoins, refUsedCoins, finalCostCoins
        )

        AlertDialog.Builder(this)
            .setTitle("🎉 Success!")
            .setMessage(message)
            .setPositiveButton("View Tournament") { _, _ ->
                startActivity(Intent(this, UpcomingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        confirmationDialog?.dismiss()
        removeUserSelection()
        setResult(RESULT_CANCELED)
    }

    // Data classes
    data class ValidationResult(
        val success: Boolean,
        val topUp: Int = 0,
        val winning: Int = 0,
        val refBonus: Int = 0,
        val error: String = "",
        val message: String = "",
        val shortage: Int = 0
    )

    data class PaymentResult(
        val success: Boolean,
        val refUsed: Int = 0,
        val finalCost: Int = 0,
        val error: String = "",
        val message: String = ""
    )
}