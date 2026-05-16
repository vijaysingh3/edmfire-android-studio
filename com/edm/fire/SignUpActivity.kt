package com.edm.fire

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.edm.fire.databinding.ActivitySignUpBinding
import com.google.android.gms.common.AccountPicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Suppress("DEPRECATION")
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    private lateinit var btnGoogleSignIn: com.google.android.gms.common.SignInButton
    private lateinit var gmailSelectingProgressBar: ProgressBar
    private lateinit var tvSelectedEmail: TextView
    private lateinit var etPassword: TextInputEditText
    private lateinit var etReferCode: TextInputEditText
    private lateinit var cbTerms: CheckBox
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSignUp: Button
    private lateinit var tvLogin: TextView
    private lateinit var tilReferCode: TextInputLayout
    private lateinit var llReferOptions: View
    private lateinit var tvReferPrompt: TextView
    private lateinit var llReferSection: View
    private lateinit var btnYesRefer: Button
    private lateinit var btnNoRefer: Button
    private lateinit var tvApplyRefer: TextView
    private lateinit var tvAppliedSuccess: TextView
    private lateinit var tvPromoSuccess: TextView
    private lateinit var tvPromoInvalid: TextView
    private lateinit var ivLogo: ImageView
    private lateinit var fabAIAgent: FloatingActionButton
    private lateinit var tvGotoPrivacy: TextView

    private var isReferCodeApplied = false
    private var appliedReferCode = ""
    private var referrerUserId: String? = null
    private var referrerEmail: String? = null
    private var selectedEmail: String? = null

    private var supabaseUrl: String = ""
    private var supabaseAnonKey: String = ""
    private var privacyPolicyUrl: String = ""
    private var termsUrl: String = ""

    private lateinit var prefs: android.content.SharedPreferences

    private var backgroundMusicPlayer: MediaPlayer? = null
    private var originalStreamVolume: Int = -1
    private val MUSIC_START_PERCENT = 0.10f
    private val MUSIC_MAX_PERCENT = 0.25f

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (backgroundMusicPlayer?.isPlaying == false) {
                    backgroundMusicPlayer?.start()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pauseBackgroundMusic()
        }
    }

    private val accountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hideEmailSelectionLoading()
        if (result.resultCode == RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                selectedEmail = accountName
                updateSelectedEmailUI(selectedEmail!!)
                showColoredToast(getString(R.string.email_selected_successfully_2, selectedEmail!!), true)
            } else {
                showColoredToast(getString(R.string.no_email_selected_4), false)
            }
        } else {
            showColoredToast(getString(R.string.email_selection_cancelled_3), false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth  = FirebaseAuth.getInstance()
        db    = FirebaseFirestore.getInstance()
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        startBackgroundMusic()

        initializeRemoteConfig { success ->
            if (success) {
                checkUserLoginStatus()
                initializeViews()
                loadLogoWithGlide()
                setupClickListeners()
            } else {
                showConfigErrorAndClose()
            }
        }
    }

    private fun startBackgroundMusic() {
        try {
            releaseBackgroundMusic()
            if (!requestAudioFocus()) return

            originalStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxSteps  = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val startStep = (MUSIC_START_PERCENT * maxSteps).toInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, startStep, 0)

            backgroundMusicPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val afd = resources.openRawResourceFd(R.raw.free_fire_advance)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(1.0f, 1.0f)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            backgroundMusicPlayer = null
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP   -> { increaseVolumeWithCap(); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { decreaseVolumeWithCap(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun increaseVolumeWithCap() {
        try {
            val maxSteps   = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxCapStep  = (MUSIC_MAX_PERCENT * maxSteps).toInt().coerceAtLeast(1)
            if (currentStep < maxCapStep) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            }
        } catch (e: Exception) { }
    }

    private fun decreaseVolumeWithCap() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        } catch (e: Exception) { }
    }

    private fun restoreOriginalVolume() {
        try {
            if (originalStreamVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalStreamVolume, 0)
                originalStreamVolume = -1
            }
        } catch (e: Exception) { }
    }

    private fun pauseBackgroundMusic() {
        try {
            if (backgroundMusicPlayer?.isPlaying == true) backgroundMusicPlayer?.pause()
        } catch (e: Exception) { }
    }

    private fun resumeBackgroundMusic() {
        try {
            if (hasAudioFocus && backgroundMusicPlayer != null && !backgroundMusicPlayer!!.isPlaying) {
                backgroundMusicPlayer?.start()
            }
        } catch (e: Exception) { }
    }

    private fun releaseBackgroundMusic() {
        try {
            backgroundMusicPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            backgroundMusicPlayer = null
        } catch (e: Exception) {
            backgroundMusicPlayer = null
        }
    }

    override fun onResume()  { super.onResume();  resumeBackgroundMusic() }
    override fun onPause()   { super.onPause();   pauseBackgroundMusic()  }
    override fun onDestroy() {
        super.onDestroy()
        releaseBackgroundMusic()
        abandonAudioFocus()
        restoreOriginalVolume()
    }

    private fun initializeViews() {
        btnGoogleSignIn          = binding.btnGoogleSignIn
        gmailSelectingProgressBar = binding.gmailSelectingProgressbar
        gmailSelectingProgressBar.visibility = View.GONE
        tvSelectedEmail  = binding.tvSelectedEmail
        etPassword       = binding.etPassword
        etReferCode      = binding.etReferCode
        cbTerms          = binding.cbTerms
        progressBar      = binding.progressBar
        btnSignUp        = binding.btnSignUp
        tvLogin          = binding.tvLogin
        tilReferCode     = binding.tilReferCode
        llReferOptions   = binding.llReferOptions
        tvReferPrompt    = binding.tvReferPrompt
        llReferSection   = binding.llReferSection
        btnYesRefer      = binding.btnYesRefer
        btnNoRefer       = binding.btnNoRefer
        tvApplyRefer     = binding.tvApplyRefer
        tvAppliedSuccess = binding.tvAppliedSuccess
        tvPromoSuccess   = binding.tvPromoSuccess
        tvPromoInvalid   = binding.tvPromoInvalid
        ivLogo           = binding.ivLogo
        fabAIAgent       = binding.fabAIAgent
        tvGotoPrivacy    = binding.tvGotoPrivacy

        tvGotoPrivacy.paintFlags = tvGotoPrivacy.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        btnSignUp.isEnabled = false
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener    { finish() }
        btnYesRefer.setOnClickListener       { showReferSection() }
        btnNoRefer.setOnClickListener        { hideReferSection() }
        tvApplyRefer.setOnClickListener      { applyReferCode() }
        tvLogin.setOnClickListener           { redirectToLogin() }
        tvGotoPrivacy.setOnClickListener     { openTermsAndConditions() }

        btnGoogleSignIn.setOnClickListener {
            showEmailSelectionLoading()
            openAccountPicker()
        }

        btnSignUp.setOnClickListener {
            if (validateInputs()) signUpWithSupabase()
        }

        fabAIAgent.setOnClickListener { openAIChatActivity() }
    }

    // ✅ Unregistered user — AI ko sahi context bhejo
    private fun openAIChatActivity() {
        try {
            val intent = Intent(this, AichatActivity::class.java).apply {
                putExtra("USER_ID",         "unregistered_${System.currentTimeMillis()}")
                putExtra("USER_SCREEN",     "signup")
                putExtra("USER_KYC_STATUS", "unregistered")
                putExtra("USER_LEVEL",      0)
                putExtra("USER_TOTAL_COINS", 0)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showColoredToast("Unable to open AI Assistant. Please try again.", false)
        }
    }

    private fun initializeRemoteConfig(onComplete: (Boolean) -> Unit) {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                supabaseUrl     = remoteConfig.getString(getString(R.string.remote_config_supabase_url_45))
                supabaseAnonKey = remoteConfig.getString(getString(R.string.remote_config_supabase_key_46))
                privacyPolicyUrl = remoteConfig.getString(getString(R.string.remote_config_privacy_policy_48))

                onComplete(supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty())
            } else {
                onComplete(false)
            }
        }
    }

    private fun openTermsAndConditions() {
        tvGotoPrivacy.isEnabled = false
        tvGotoPrivacy.alpha     = 0.6f

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            tvGotoPrivacy.isEnabled = true
            tvGotoPrivacy.alpha     = 1.0f

            if (task.isSuccessful) {
                termsUrl = remoteConfig.getString("terms_link")
                if (termsUrl.isNotEmpty()) {
                    val intent = Intent(this, WebviewActivity::class.java).apply {
                        putExtra(getString(R.string.extra_url_44), termsUrl)
                        putExtra("title", "Terms & Conditions")
                    }
                    startActivity(intent)
                } else {
                    showColoredToast("Terms link unavailable.", false)
                }
            } else {
                showColoredToast("Failed to load Terms.", false)
            }
        }
    }

    private fun showConfigErrorAndClose() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.configuration_error_33))
            .setMessage(getString(R.string.configuration_error_message_34))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.retry_32)) { _, _ ->
                initializeRemoteConfig { success ->
                    if (success) recreate() else showConfigErrorAndClose()
                }
            }
            .setNegativeButton(getString(R.string.exit_31)) { _, _ -> finish() }
            .show()
    }

    private fun openAccountPicker() {
        try {
            showColoredToast(getString(R.string.opening_account_selector_6), true)
            val intent = AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .build()
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            hideEmailSelectionLoading()
            showColoredToast(getString(R.string.error_opening_account_picker_5), false)
            showAvailableEmails()
        }
    }

    private fun showAvailableEmails() {
        hideEmailSelectionLoading()
        val accounts = getAccountsFromDevice()
        if (accounts.isEmpty()) {
            showColoredToast(getString(R.string.no_google_accounts_found_7), false)
            return
        }
        val emails = accounts.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_your_email_8))
            .setItems(emails) { _, which ->
                selectedEmail = emails[which]
                updateSelectedEmailUI(selectedEmail!!)
                showColoredToast(getString(R.string.email_selected_successfully_2, selectedEmail!!), true)
            }
            .setNegativeButton(getString(R.string.cancel_30), null)
            .show()
    }

    private fun getAccountsFromDevice(): List<Account> {
        return try {
            val accountManager = getSystemService(ACCOUNT_SERVICE) as AccountManager
            accountManager.getAccountsByType("com.google").toList()
        } catch (_: SecurityException) {
            showColoredToast(getString(R.string.permission_denied_accounts_9), false)
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateSelectedEmailUI(email: String) {
        tvSelectedEmail.text       = getString(R.string.selected_email_1, email)
        tvSelectedEmail.visibility = View.VISIBLE
        btnSignUp.isEnabled        = true
    }

    private fun applyReferCode() {
        val referCode = etReferCode.text.toString().trim().uppercase()
        if (referCode.isEmpty()) {
            showColoredToast(getString(R.string.enter_promo_code_14), false)
            return
        }
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestJson = JSONObject().apply {
                    put("action", "verify_referral_code")
                    put("referralCode", referCode)
                }
                val response = makeSupabaseRequest(requestJson)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.getBoolean("success")) {
                        isReferCodeApplied = true
                        appliedReferCode   = referCode
                        referrerUserId     = response.optString("referrer_user_id")
                        referrerEmail      = response.optString("referrer_email")
                        showPromoCodeSuccess()
                        showColoredToast(getString(R.string.promo_applied_successfully_16), true)
                    } else {
                        showPromoCodeInvalid()
                        showColoredToast(
                            response.optString("error", getString(R.string.invalid_promo_code_17)), false
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showPromoCodeInvalid()
                    showColoredToast(getFriendlyErrorMessage(e), false)
                }
            }
        }
    }

    private fun signUpWithSupabase() {
        val androidId = getAndroidId()
        val email     = selectedEmail ?: ""
        val password  = etPassword.text.toString().trim()
        val referCode = if (isReferCodeApplied) appliedReferCode else null

        progressBar.visibility = View.VISIBLE
        btnSignUp.isEnabled    = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestJson = JSONObject().apply {
                    put("action", "create_user_with_bonus")
                    put("email", email)
                    put("password", password)
                    put("deviceId", androidId)
                    if (!referCode.isNullOrEmpty()) put("referralCode", referCode)
                }
                val response = makeSupabaseRequest(requestJson)
                withContext(Dispatchers.Main) {
                    if (response.getBoolean("success")) {
                        val customToken = response.optString("customToken")
                        if (customToken.isNotEmpty()) {
                            signInWithCustomToken(customToken)
                        } else {
                            saveLoginState()
                            redirectToLogin()
                        }
                        showColoredToast(getString(R.string.account_created_successfully_19), true)
                    } else {
                        val errorMsg = response.optString("error", "Signup failed")
                        if (errorMsg.contains("Device already registered", ignoreCase = true)) {
                            progressBar.visibility = View.GONE
                            btnSignUp.isEnabled    = true
                            if (!isFinishing && !isDestroyed) {
                                showDeviceAlreadyRegisteredDialog(extractEmailFromError(errorMsg))
                            }
                        } else {
                            showColoredToast(errorMsg, false)
                            progressBar.visibility = View.GONE
                            btnSignUp.isEnabled    = true
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnSignUp.isEnabled    = true
                    showColoredToast(getString(R.string.signup_failed_generic_20, getFriendlyErrorMessage(e)), false)
                }
            }
        }
    }

    private fun extractEmailFromError(errorMsg: String): String {
        val match = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}").find(errorMsg)
        return match?.value?.trim() ?: errorMsg.substringAfter("email: ", "").substringBefore(",").trim()
    }

    private suspend fun makeSupabaseRequest(requestJson: JSONObject): JSONObject {
        return withContext(Dispatchers.IO) {
            val url        = java.net.URL(supabaseUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            try {
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
                    setRequestProperty("Accept", "application/json")
                    doOutput      = true
                    connectTimeout = 30000
                    readTimeout    = 30000
                }
                connection.outputStream.use { it.write(requestJson.toString().toByteArray()) }

                val responseCode  = connection.responseCode
                val inputStream   = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText  = inputStream.bufferedReader().use { it.readText() }

                try { JSONObject(responseText) }
                catch (je: JSONException) {
                    JSONObject().put("success", false).put("error", "Server returned invalid data format.")
                }
            } catch (e: SocketTimeoutException) {
                JSONObject().put("success", false).put("error", "Request Timeout.")
            } catch (e: UnknownHostException) {
                JSONObject().put("success", false).put("error", "No Internet Connection.")
            } catch (e: Exception) {
                JSONObject().put("success", false).put("error", "Network Error: ${e.localizedMessage}")
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun getFriendlyErrorMessage(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "Request Timeout."
            is UnknownHostException   -> "No Internet Connection."
            is JSONException          -> "Data parsing error."
            else                      -> e.message ?: "An unknown error occurred."
        }
    }

    private fun signInWithCustomToken(customToken: String) {
        auth.signInWithCustomToken(customToken).addOnCompleteListener { task ->
            progressBar.visibility = View.GONE
            btnSignUp.isEnabled    = true
            if (task.isSuccessful) {
                saveLoginState()
                redirectToLogin()
            } else {
                showColoredToast(
                    getString(R.string.authentication_failed_21, task.exception?.message ?: "Unknown error"), false
                )
            }
        }
    }

    private fun showDeviceAlreadyRegisteredDialog(registeredEmail: String) {
        if (isFinishing || isDestroyed) return

        val dialogView       = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        val tvStoredEmail    = dialogView.findViewById<TextView>(R.id.tvStoredEmail)     ?: return
        val etPasswordDialog = dialogView.findViewById<TextInputEditText>(R.id.etPassword) ?: return
        val cbShowPassword   = dialogView.findViewById<CheckBox>(R.id.cbShowPassword)    ?: return
        val tvForgotPassword = dialogView.findViewById<TextView>(R.id.tvForgotPassword)  ?: return
        val dialogProgressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)   ?: return
        val btnLogin         = dialogView.findViewById<Button>(R.id.btnLogin)            ?: return

        tvStoredEmail.text = getString(R.string.registered_gmail_label_37, registeredEmail)

        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            etPasswordDialog.transformationMethod =
                if (isChecked) null else PasswordTransformationMethod()
            etPasswordDialog.setSelection(etPasswordDialog.text?.length ?: 0)
        }

        tvForgotPassword.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra(getString(R.string.extra_email_43), registeredEmail)
            })
        }

        btnLogin.setOnClickListener {
            val password = etPasswordDialog.text.toString().trim()
            if (password.isEmpty()) { etPasswordDialog.error = getString(R.string.password_required_10); return@setOnClickListener }

            dialogProgressBar.visibility = View.VISIBLE
            btnLogin.isEnabled           = false

            auth.signInWithEmailAndPassword(registeredEmail, password).addOnCompleteListener { task ->
                dialogProgressBar.visibility = View.GONE
                btnLogin.isEnabled           = true
                if (task.isSuccessful) {
                    showColoredToast(getString(R.string.login_successful_22), true)
                    saveLoginState()
                    redirectToLogin()
                    dialog.dismiss()
                } else {
                    val msg = when {
                        task.exception?.message?.contains("no user record") == true          -> "No account found with this email"
                        task.exception?.message?.contains("password is invalid") == true     -> "Wrong Password"
                        task.exception?.message?.contains("network error") == true           -> "Network error. Check your connection"
                        task.exception?.message?.contains("incorrect, malformed") == true    -> "Wrong Password or Email"
                        else -> "Login failed. Please try again"
                    }
                    showColoredToast(msg, false)
                }
            }
        }
        if (!isFinishing && !isDestroyed) dialog.show()
    }

    private fun showColoredToast(message: String, isPositive: Boolean) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkUserLoginStatus() {
        if (isNetworkAvailable()) {
            auth.currentUser?.let { redirectToLogin(); return }
        } else {
            if (prefs.getBoolean("isLoggedIn", false)) {
                showNoInternetAlert { redirectToLogin() }
            } else {
                showNoInternetAlert { }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm      = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun showNoInternetAlert(onContinue: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.no_internet_connection_27))
            .setMessage(getString(R.string.no_internet_message_28))
            .setPositiveButton(getString(R.string.continue_29)) { d, _ -> d.dismiss(); onContinue() }
            .setNegativeButton(getString(R.string.cancel_30)) { d, _ -> d.dismiss(); finish() }
            .setCancelable(false)
            .show()
    }

    private fun loadLogoWithGlide() {
        Glide.with(this)
            .load(R.mipmap.ic_launcher)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivLogo)
    }

    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showReferSection() {
        llReferSection.visibility = View.VISIBLE
        llReferOptions.visibility = View.GONE
        tvReferPrompt.visibility  = View.GONE
    }

    private fun hideReferSection() {
        llReferSection.visibility = View.GONE
        llReferOptions.visibility = View.GONE
        tvReferPrompt.visibility  = View.GONE
        etReferCode.setText("")
        resetReferCodeUI()
    }

    private fun showPromoCodeSuccess() {
        tvApplyRefer.visibility    = View.GONE
        tvAppliedSuccess.visibility = View.VISIBLE
        tvPromoSuccess.visibility  = View.VISIBLE
        tvPromoInvalid.visibility  = View.GONE
        etReferCode.isEnabled      = false
    }

    private fun showPromoCodeInvalid() {
        tvPromoSuccess.visibility = View.GONE
        tvPromoInvalid.visibility = View.VISIBLE
    }

    private fun resetReferCodeUI() {
        isReferCodeApplied      = false
        appliedReferCode        = ""
        referrerUserId          = null
        referrerEmail           = null
        tvApplyRefer.visibility    = View.VISIBLE
        tvAppliedSuccess.visibility = View.GONE
        tvPromoSuccess.visibility  = View.GONE
        tvPromoInvalid.visibility  = View.GONE
        etReferCode.isEnabled      = true
    }

    private fun validateInputs(): Boolean {
        if (selectedEmail.isNullOrEmpty()) {
            showColoredToast(getString(R.string.select_email_first_13), false)
            return false
        }
        val password = etPassword.text.toString().trim()
        if (password.isEmpty()) { etPassword.error = getString(R.string.password_required_10); return false }
        if (password.length < 4) { etPassword.error = getString(R.string.password_min_length_11); return false }
        if (!cbTerms.isChecked) { showColoredToast(getString(R.string.accept_terms_12), false); return false }
        return true
    }

    private fun saveLoginState() {
        prefs.edit {
            putBoolean("isLoggedIn", true)
            putString("user_email", selectedEmail ?: "")
        }
    }

    private fun showEmailSelectionLoading() {
        gmailSelectingProgressBar.visibility = View.VISIBLE
        btnGoogleSignIn.isEnabled            = false
    }

    private fun hideEmailSelectionLoading() {
        gmailSelectingProgressBar.visibility = View.GONE
        btnGoogleSignIn.isEnabled            = true
    }
}