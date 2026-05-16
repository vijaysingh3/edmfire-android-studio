package com.edm.fire

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.edm.fire.databinding.ActivityLoginBinding
import com.google.android.gms.common.AccountPicker
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnPickGmail: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvSignUp: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var ivLogo: ImageView
    private lateinit var cardProcess: MaterialCardView
    private lateinit var tvProcessStep: TextView
    private lateinit var tvProcessDetail: TextView
    private lateinit var layoutSuccess: LinearLayout
    private lateinit var tvWelcomeUser: TextView
    private lateinit var scrollContent: androidx.core.widget.NestedScrollView

    private var swiftWorkerUrl: String = ""
    private var supabaseAnonKey: String = ""

    // ✅ AccountPicker launcher — SignUpActivity jaisa proper Gmail picker
    private val accountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                // email field mein fill karo, editable rehne do
                etEmail.setText(accountName)
                etEmail.setSelection(accountName.length)
                etEmail.isEnabled = true
                Toast.makeText(this, "✅ Gmail selected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No email selected", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Email selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // purana Gmail picker launcher — ab use nahi hoga, AccountPicker replace karega
    private val gmailPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                etEmail.setText(accountName)
                etEmail.setSelection(accountName.length)
                Toast.makeText(this, "✅ Gmail selected!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth
        db = Firebase.firestore
        initializeRemoteConfig()

        initializeViews()
        loadLogoWithGlide()

        binding.ivBack.setOnClickListener {
            onBackPressed()
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        btnLogin.setOnClickListener {
            if (validateInputs()) {
                loginUser()
            }
        }

        // ✅ Gmail picker button — AccountPicker use karega
        btnPickGmail.setOnClickListener {
            openGmailPicker()
        }
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.setDefaultsAsync(
            mapOf(
                "new_version" to "",
                "swift_worker" to "",
                "supa_anonkey" to ""
            )
        )

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    swiftWorkerUrl = remoteConfig.getString("swift_worker")
                    supabaseAnonKey = remoteConfig.getString("supa_anonkey")
                }
            }
    }

    private fun initializeViews() {
        etEmail = binding.etEmail
        etPassword = binding.etPassword
        btnLogin = binding.btnLogin
        btnPickGmail = binding.btnPickGmail
        progressBar = binding.progressBar
        tvSignUp = binding.tvSignUp
        tvForgotPassword = binding.tvForgotPassword
        ivLogo = binding.ivLogo
        cardProcess = binding.cardProcess
        tvProcessStep = binding.tvProcessStep
        tvProcessDetail = binding.tvProcessDetail
        layoutSuccess = binding.layoutSuccess
        tvWelcomeUser = binding.tvWelcomeUser
        scrollContent = binding.scrollContent
    }

    // ✅ AccountPicker — SignUpActivity jaisa proper device Gmail picker
    private fun openGmailPicker() {
        try {
            Toast.makeText(this, "Opening account selector...", Toast.LENGTH_SHORT).show()
            val intent = AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .build()
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            // agar AccountPicker fail ho to fallback AlertDialog dikhao
            showAvailableEmailsFallback()
        }
    }

    // ✅ Fallback — device accounts AlertDialog mein dikhao
    private fun showAvailableEmailsFallback() {
        try {
            val accountManager = getSystemService(ACCOUNT_SERVICE) as AccountManager
            val accounts: List<Account> = accountManager.getAccountsByType("com.google").toList()

            if (accounts.isEmpty()) {
                Toast.makeText(this, "⚠ No Gmail accounts found on device", Toast.LENGTH_SHORT).show()
                return
            }

            val emailList = accounts.map { it.name }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("📬 Select Gmail Account")
                .setItems(emailList) { _, which ->
                    val selectedEmail = emailList[which]
                    etEmail.setText(selectedEmail)
                    etEmail.setSelection(selectedEmail.length)
                    etEmail.isEnabled = true
                    Toast.makeText(this, "✅ Gmail selected!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "⚠ Could not load accounts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadLogoWithGlide() {
        Glide.with(this)
            .load(R.mipmap.ic_launcher)
            .placeholder(R.drawable.ic_upcoming)
            .error(R.mipmap.ic_launcher)
            .transition(DrawableTransitionOptions.withCrossFade(500))
            .into(ivLogo)
    }

    private fun validateInputs(): Boolean {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty()) {
            etEmail.error = "⚠ Email is required"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "⚠ Enter valid email"
            return false
        }

        if (password.isEmpty()) {
            etPassword.error = "⚠ Password is required"
            return false
        }

        return true
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Process show — user ko pata chale login ho raha hai
        showProcess("🔄  Signing In...", "Verifying your credentials")
        btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Step update — version check ho raha hai
                    showProcess("🔍  Checking Version...", "Verifying app compatibility")

                    updateFcmTokenSilently()

                    val versionName = try {
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        packageInfo.versionName ?: "1.0"
                    } catch (e: Exception) {
                        "1.0"
                    }

                    checkVersionAndProceed(versionName)
                } else {
                    hideProcess()
                    btnLogin.isEnabled = true

                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "❌ No account found with this email"
                        task.exception?.message?.contains("password is invalid") == true ->
                            "❌ Wrong Password"
                        task.exception?.message?.contains("network error") == true ->
                            "❌ Network error. Check your connection"
                        task.exception?.message?.contains("incorrect, malformed or has expired") == true ->
                            "❌ Wrong Password or Email"
                        else -> "❌ Login failed. Please try again"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkVersionAndProceed(localVersion: String) {
        val docRef = db.collection("AppVersion").document("oxOggxjiTFhG8MZqNXhd")
        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val remoteVersion = document.getString("AppVersion") ?: ""
                    if (remoteVersion.isNotEmpty() && remoteVersion == localVersion) {
                        showSuccessAndNavigate()
                    } else {
                        hideProcess()
                        btnLogin.isEnabled = true
                        showUpdateRequiredDialog(remoteVersion)
                    }
                } else {
                    showSuccessAndNavigate()
                }
            }
            .addOnFailureListener {
                showSuccessAndNavigate()
            }
    }

    // ✅ Success overlay — ic_completed ke saath animate karke dikhao
    private fun showSuccessAndNavigate() {
        val userName = auth.currentUser?.email
            ?.substringBefore("@")
            ?.replaceFirstChar { it.uppercase() }
            ?: "Player"

        tvWelcomeUser.text = "Welcome back, $userName! 🎮"

        // Scroll aur process hide karo
        scrollContent.visibility = View.GONE
        cardProcess.visibility = View.GONE

        // Success overlay show karo
        layoutSuccess.visibility = View.VISIBLE
        layoutSuccess.alpha = 0f
        layoutSuccess.scaleX = 0.88f
        layoutSuccess.scaleY = 0.88f

        layoutSuccess.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // ic_completed icon bounce animation
        val ivSuccess = findViewById<ImageView>(R.id.ivSuccess)
        ivSuccess.scaleX = 0f
        ivSuccess.scaleY = 0f
        ivSuccess.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(200)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // 1 second baad auto navigate to HomeActivity
                ivSuccess.postDelayed({
                    navigateToHome()
                }, 1000)
            }
            .start()
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun getSafeDownloadLink(): String {
        val raw = remoteConfig.getString("new_version").trim()
        if (raw.isEmpty()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    private fun showUpdateRequiredDialog(remoteVersion: String) {
        val message = if (remoteVersion.isNotEmpty()) {
            "Please Download Latest App or apk. Latest version: $remoteVersion"
        } else {
            "Please Download Latest App or apk."
        }
        val downloadLink = getSafeDownloadLink()
        AlertDialog.Builder(this)
            .setTitle("Update Required")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ ->
                if (downloadLink.isNotBlank()) {
                    showProcess("📥  Opening Download...", "Please wait")
                    btnLogin.isEnabled = false
                    tvSignUp.isEnabled = false
                    tvForgotPassword.isEnabled = false
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadLink))
                        startActivity(intent)
                    } catch (e: Exception) {
                        hideProcess()
                        btnLogin.isEnabled = true
                        tvSignUp.isEnabled = true
                        tvForgotPassword.isEnabled = true
                        Toast.makeText(this, "Unable to open download link", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Download link not available", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun fetchAppVersionFromFirestore() {
        val docRef = db.collection("AppVersion").document("oxOggxjiTFhG8MZqNXhd")
        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val version = document.getString("AppVersion") ?: "Unknown"
                    Toast.makeText(this, "Firestore Version: $version", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to fetch Firestore version", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFcmTokenSilently() {
        val userId = auth.currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener

            val token = task.result

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (swiftWorkerUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()) {
                        updateFcmTokenViaSupabase(userId, token)
                    } else {
                        updateFcmTokenDirect(userId, token)
                    }
                } catch (e: Exception) {
                    updateFcmTokenDirect(userId, token)
                }
            }
        }
    }

    private suspend fun updateFcmTokenViaSupabase(userId: String, fcmToken: String) {
        try {
            val requestJson = JSONObject().apply {
                put("action", "update_fcm_token")
                put("userId", userId)
                put("fcmToken", fcmToken)
            }

            val response = makeSupabaseRequest(requestJson)
            if (!response.getBoolean("success")) {
                updateFcmTokenDirect(userId, fcmToken)
            }
        } catch (e: Exception) {
            updateFcmTokenDirect(userId, fcmToken)
        }
    }

    private suspend fun makeSupabaseRequest(requestJson: JSONObject): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = java.net.URL(swiftWorkerUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            val outputStream = connection.outputStream
            outputStream.write(requestJson.toString().toByteArray())
            outputStream.flush()
            outputStream.close()

            val inputStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            JSONObject(responseText)
        }
    }

    private fun updateFcmTokenDirect(userId: String, token: String) {
        val tokenMap = mapOf(
            "fcmToken" to token,
            "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now(),
            "lastLogin" to com.google.firebase.Timestamp.now()
        )

        db.collection("Users").document(userId)
            .set(tokenMap, SetOptions.merge())
    }

    // ✅ Process card show — step aur detail ke saath
    private fun showProcess(step: String, detail: String) {
        cardProcess.visibility = View.VISIBLE
        tvProcessStep.text = step
        tvProcessDetail.text = detail

        cardProcess.alpha = 0f
        cardProcess.animate().alpha(1f).setDuration(300).start()
    }

    // ✅ Process card hide
    private fun hideProcess() {
        cardProcess.animate().alpha(0f).setDuration(200).withEndAction {
            cardProcess.visibility = View.GONE
        }.start()
    }

    // ✅ Already logged in → fast smooth auto login → direct HomeActivity
    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            showProcess("⚡  Auto Login...", "Welcome back!")
            binding.root.postDelayed({
                navigateToHome()
            }, 600)
        }
    }

    override fun onResume() {
        super.onResume()
        hideProcess()
        btnLogin.isEnabled = true
        tvSignUp.isEnabled = true
        tvForgotPassword.isEnabled = true
    }
}