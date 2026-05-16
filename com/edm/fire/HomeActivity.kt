package com.edm.fire

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.edm.fire.data.FirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.DecimalFormat
import java.util.Date

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: com.google.firebase.firestore.FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var tvCoins: TextView
    private lateinit var imbNotification: ImageButton
    private lateinit var notificationBadge: TextView

    private var hasRequestedNotificationPermission = false

    private lateinit var repository: FirestoreRepository

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var soundLoaded: Boolean = false
    private val clickVolume = 0.05f

    private var backgroundMusicPlayer: MediaPlayer? = null
    private var notificationPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var originalStreamVolume: Int = -1

    private var notificationCountListener: ListenerRegistration? = null
    private var lastUnreadNotificationCount: Int? = null

    private val MUSIC_START_PERCENT = 0.10f
    private val MUSIC_MAX_PERCENT = 0.20f

    // ✅ Decimal formatter for coin display
    private val decimalFormat = DecimalFormat("#.##")

    // ==================== BAN SYSTEM WITH CACHE VARIABLES ====================
    private var cachedBanStatus: BanCache? = null
    private var isBanCheckRunning = false
    private var banCheckHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    data class BanCache(
        val accountStatus: String,
        val bannedReason: String,
        val bannedPeriod: Date?,
        val cachedAt: Long
    )

    companion object {
        private const val BAN_CHECK_INTERVAL = 30L // 30 seconds
        private const val CACHE_VALIDITY_HOURS = 12L // 12 hours cache
        private const val CACHE_VALIDITY_MS = CACHE_VALIDITY_HOURS * 60 * 60 * 1000
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> resumeBackgroundMusic()
            AudioManager.AUDIOFOCUS_LOSS -> pauseBackgroundMusic()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseBackgroundMusic()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pauseBackgroundMusic()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            showNotificationPermissionDeniedDialog()
        }
        hasRequestedNotificationPermission = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        Log.d("HOME_ACTIVITY", "onCreate - HomeActivity start")

        auth = Firebase.auth
        db = Firebase.firestore
        remoteConfig = FirebaseRemoteConfig.getInstance()

        // Initialize remote config defaults
        val configDefaults = mapOf<String, Any>("autoUnban_function" to "")
        remoteConfig.setDefaultsAsync(configDefaults)
        remoteConfig.fetchAndActivate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val userId = auth.currentUser?.uid
        Log.d("HOME_ACTIVITY", "User logged in: $userId")

        repository = FirestoreRepository(this)

        tvCoins = findViewById(R.id.tv_coins)
        imbNotification = findViewById(R.id.imb_notification)
        notificationBadge = findViewById(R.id.notification_badge)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val ivProfile: ImageView = findViewById(R.id.iv_profile)
        val homeCoin: ImageView = findViewById(R.id.home_coin)

        Glide.with(this)
            .load(R.drawable.applogo)
            .placeholder(R.drawable.applogo)
            .error(R.drawable.applogo)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .circleCrop()
            .into(ivProfile)

        Glide.with(this)
            .load(R.drawable.ic_coin)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(homeCoin)

        loadFreshUserData()
        startNotificationCountListener()

        replaceFragment(HomeFragment())

        val homeBtn: LinearLayout = findViewById(R.id.nav_home)
        val profileBtn: LinearLayout = findViewById(R.id.nav_profile)
        val transactionBtn: LinearLayout = findViewById(R.id.nav_wallet)
        val helpBtn: LinearLayout = findViewById(R.id.nav_help)
        val worldChatBtn: LinearLayout = findViewById(R.id.nav_world_chat)

        initializeClickSound()
        startBackgroundMusic()

        homeBtn.setOnClickListener { playClickSound(); replaceFragment(HomeFragment()) }
        profileBtn.setOnClickListener { playClickSound(); replaceFragment(ProfileFragment()) }
        transactionBtn.setOnClickListener { playClickSound(); replaceFragment(WalletFragment()) }
        helpBtn.setOnClickListener { playClickSound(); replaceFragment(HelpFragment()) }
        worldChatBtn.setOnClickListener { playClickSound(); replaceFragment(WorldChatFragment()) }

        findViewById<LinearLayout>(R.id.coin_container).setOnClickListener {
            startActivity(Intent(this, DepositActivity::class.java))
        }

        ivProfile.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        imbNotification.setOnClickListener {
            playClickSound()
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        updateFcmTokenDirect()

        // ==================== BAN SYSTEM CHECK WITH CACHE ====================
        checkBanStatusWithCache()
        startPeriodicBanCheck()

        Log.d("HOME_ACTIVITY", "onCreate complete")
    }

    override fun onResume() {
        super.onResume()
        Log.d("HOME_ACTIVITY", "onResume - refresh data")

        loadFreshUserData()

        if (notificationCountListener == null) {
            startNotificationCountListener()
        }

        resumeBackgroundMusic()

        if (!hasRequestedNotificationPermission) {
            checkAndRequestNotificationPermission()
        }

        // Use cached ban check on resume (no extra DB call if cache valid)
        checkBanStatusWithCache()
    }

    override fun onPause() {
        super.onPause()
        pauseBackgroundMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("HOME_ACTIVITY", "onDestroy - cleanup")
        notificationCountListener?.remove()
        notificationCountListener = null
        soundPool?.release()
        soundPool = null
        releaseNotificationPlayer()
        releaseBackgroundMusic()
        abandonAudioFocus()
        restoreOriginalVolume()

        // Ban system cleanup
        banCheckHandler.removeCallbacksAndMessages(null)
    }

    // ==================== BAN SYSTEM WITH CACHE METHODS ====================

    private fun startPeriodicBanCheck() {
        banCheckHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing && !isDestroyed) {
                    checkBanStatusWithCache()
                    banCheckHandler.postDelayed(this, BAN_CHECK_INTERVAL * 1000)
                }
            }
        }, BAN_CHECK_INTERVAL * 1000)
    }

    private fun checkBanStatusWithCache() {
        val userId = auth.currentUser?.uid ?: return

        val currentTime = System.currentTimeMillis()

        // Check if cache exists and is still valid (less than 12 hours old)
        if (cachedBanStatus != null &&
            (currentTime - cachedBanStatus!!.cachedAt) < CACHE_VALIDITY_MS) {

            // Use cached data - no Firestore call
            val cacheAgeHours = (currentTime - cachedBanStatus!!.cachedAt) / (1000 * 60 * 60)
            Log.d("BAN_SYSTEM", "Using cached ban data - Age: $cacheAgeHours hours")

            val cached = cachedBanStatus!!
            if (cached.accountStatus == "Banned") {
                handleBanStatus(cached.bannedReason, cached.bannedPeriod, userId)
            }
            return
        }

        // Cache expired or doesn't exist - fetch from Firestore once
        Log.d("BAN_SYSTEM", "Cache expired/empty - fetching from Firestore")
        fetchBanStatusFromFirestore(userId)
    }

    private fun fetchBanStatusFromFirestore(userId: String) {
        if (isBanCheckRunning) return

        isBanCheckRunning = true

        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                isBanCheckRunning = false

                if (document.exists()) {
                    val accountStatus = document.getString("AccountStatus") ?: "Active"
                    val bannedReason = document.getString("BannedReason") ?: "No reason provided"
                    val bannedPeriodTimestamp = document.getTimestamp("BannedPeriod")
                    val bannedPeriod = bannedPeriodTimestamp?.toDate()

                    // Save to cache
                    cachedBanStatus = BanCache(
                        accountStatus = accountStatus,
                        bannedReason = bannedReason,
                        bannedPeriod = bannedPeriod,
                        cachedAt = System.currentTimeMillis()
                    )

                    Log.d("BAN_SYSTEM", "Cache updated - Status: $accountStatus")

                    if (accountStatus == "Banned") {
                        handleBanStatus(bannedReason, bannedPeriod, userId)
                    }
                }
            }
            .addOnFailureListener { e ->
                isBanCheckRunning = false
                Log.e("BAN_SYSTEM", "Fetch failed: ${e.message}")
                // Keep existing cache on failure
            }
    }

    private fun handleBanStatus(reason: String, bannedPeriod: Date?, userId: String) {
        // Agar temporary ban hai aur expiry time ho gaya hai
        if (bannedPeriod != null && bannedPeriod.before(Date())) {
            callAutoUnbanFunction(userId)
        } else {
            showBanDialog(reason, bannedPeriod)
        }
    }

    private fun callAutoUnbanFunction(userId: String) {
        val functionUrl = remoteConfig.getString("autoUnban_function")

        if (functionUrl.isEmpty() || functionUrl == "autoUnban_function") {
            Log.e("BAN_SYSTEM", "Auto unban URL not configured")
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

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BAN_SYSTEM", "Auto unban failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody?.contains("success") == true) {
                        // Clear cache so next check fetches fresh data
                        cachedBanStatus = null
                        runOnUiThread {
                            recreate()
                        }
                    }
                }
            }
        })
    }

    private fun showBanDialog(reason: String, bannedPeriod: Date?) {
        // Agar dialog already visible hai to duplicate mat dikhao
        if (supportFragmentManager.findFragmentByTag("BAN_DIALOG") != null) {
            return
        }

        BanWarningDialog(
            context = this,
            bannedReason = reason,
            bannedPeriod = bannedPeriod,
            onUnbanSuccess = {
                // Clear cache on unban success
                cachedBanStatus = null
                recreate()
            }
        ).show()
    }

    // Force refresh ban status (call this when needed, e.g., after important actions)
    fun forceRefreshBanStatus() {
        cachedBanStatus = null
        checkBanStatusWithCache()
    }

    // ==================== COIN CONVERSION UTILITIES ====================

    private fun paisaToRupees(paisa: Long): Double {
        return paisa / 100.0
    }

    private fun formatCoins(paisa: Long): String {
        val rupees = paisaToRupees(paisa)
        val formatted = decimalFormat.format(rupees)
        return if (rupees == 1.0) "$formatted Coin" else "$formatted Coins"
    }

    private fun loadFreshUserData() {
        lifecycleScope.launch {
            Log.d("HOME_ACTIVITY", "loadFreshUserData - Firestore se coins fetch")
            val userData = repository.fetchUserData(forceRefresh = true)
            userData?.let { data ->
                val totalCoinsInPaisa = data.totalCoins
                Log.d("HOME_ACTIVITY", "Total coins in paisa: $totalCoinsInPaisa")

                tvCoins.text = formatCoins(totalCoinsInPaisa)
                Log.d("HOME_ACTIVITY", "Coins updated: ${formatCoins(totalCoinsInPaisa)}")
            } ?: Log.e("HOME_ACTIVITY", "loadFreshUserData - userData null")
        }
    }

    private fun startNotificationCountListener() {
        val userId = auth.currentUser?.uid ?: return

        notificationCountListener?.remove()
        notificationCountListener = db.collection("Users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HOME_ACTIVITY", "notification listener error: ${error.message}")
                    return@addSnapshotListener
                }

                val count = snapshot?.getLong("unreadNotificationCount")?.toInt() ?: 0
                handleNotificationCountUpdate(count)
            }
    }

    private fun refreshNotificationCountOnce() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val count = document.getLong("unreadNotificationCount")?.toInt() ?: 0
                handleNotificationCountUpdate(count)
            }
            .addOnFailureListener { error ->
                Log.e("HOME_ACTIVITY", "notification count fetch fail: ${error.message}")
            }
    }

    private fun handleNotificationCountUpdate(count: Int) {
        val oldCount = lastUnreadNotificationCount
        updateNotificationBadge(count)
        Log.d("HOME_ACTIVITY", "Notification count: $count")

        if (oldCount != null && count > oldCount) {
            playNotificationAlert()
        }

        lastUnreadNotificationCount = count
    }

    fun refreshUserDataManually() = loadFreshUserData()

    fun refreshNotificationsManually() = refreshNotificationCountOnce()

    fun markAllNotificationsAsRead() {
        val userId = auth.currentUser?.uid ?: return
        Log.d("HOME_ACTIVITY", "markAllNotificationsAsRead start")

        db.collection("Users")
            .document(userId)
            .set(mapOf("unreadNotificationCount" to 0), SetOptions.merge())
            .addOnSuccessListener {
                lastUnreadNotificationCount = 0
                updateNotificationBadge(0)
            }
            .addOnFailureListener { error ->
                Log.e("HOME_ACTIVITY", "markAllNotificationsAsRead fail: ${error.message}")
            }
    }

    private fun updateNotificationBadge(count: Int) {
        if (count > 0) {
            notificationBadge.text = if (count > 99) "99+" else count.toString()
            notificationBadge.visibility = TextView.VISIBLE
            notificationBadge.animate()
                .scaleX(1.3f).scaleY(1.3f).setDuration(200)
                .withEndAction {
                    notificationBadge.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }.start()
        } else {
            notificationBadge.visibility = TextView.GONE
        }
    }

    private fun updateFcmTokenDirect() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("FCM", "updateFcmToken - User not logged in")
            return
        }

        Log.d("FCM", "updateFcmToken - token fetch for user: $userId")

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("FCM", "updateFcmToken - Token fetch fail: ${task.exception?.message}")
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("FCM", "updateFcmToken - Token received")

            db.collection("Users").document(userId).set(
                mapOf(
                    "fcmToken" to token,
                    "lastLogin" to com.google.firebase.Timestamp.now()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                Log.d("FCM", "updateFcmToken - token saved in Firestore")
            }.addOnFailureListener { e ->
                Log.e("FCM", "updateFcmToken - Firestore write error: ${e.message}")
            }
        }
    }

    private fun startBackgroundMusic() {
        try {
            releaseBackgroundMusic()
            if (!requestAudioFocus()) return
            originalStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
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
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    increaseVolumeWithCap()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    decreaseVolumeWithCap()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun increaseVolumeWithCap() {
        try {
            val maxSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxCapStep = (MUSIC_MAX_PERCENT * maxSteps).toInt().coerceAtLeast(1)
            if (currentStep < maxCapStep) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun decreaseVolumeWithCap() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        } catch (_: Exception) {
        }
    }

    private fun restoreOriginalVolume() {
        try {
            if (originalStreamVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalStreamVolume, 0)
                originalStreamVolume = -1
            }
        } catch (_: Exception) {
        }
    }

    private fun pauseBackgroundMusic() {
        try {
            if (backgroundMusicPlayer?.isPlaying == true) backgroundMusicPlayer?.pause()
        } catch (_: Exception) {
        }
    }

    private fun resumeBackgroundMusic() {
        try {
            val player = backgroundMusicPlayer
            if (hasAudioFocus && player != null && !player.isPlaying) {
                enforceVolumeCap()
                player.start()
            }
        } catch (_: Exception) {
        }
    }

    private fun enforceVolumeCap() {
        try {
            val maxSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxCapStep = (MUSIC_MAX_PERCENT * maxSteps).toInt().coerceAtLeast(1)
            if (currentStep > maxCapStep) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxCapStep, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseBackgroundMusic() {
        try {
            backgroundMusicPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            backgroundMusicPlayer = null
        } catch (_: Exception) {
            backgroundMusicPlayer = null
        }
    }

    private fun playNotificationAlert() {
        vibrateForNotification()
        playNotificationSound()
    }

    private fun vibrateForNotification() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e("HOME_ACTIVITY", "notification vibration fail: ${e.message}")
        }
    }

    private fun playNotificationSound() {
        try {
            releaseNotificationPlayer()
            notificationPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                val afd = resources.openRawResourceFd(R.raw.notification)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { player ->
                    player.release()
                    notificationPlayer = null
                }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    notificationPlayer = null
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("HOME_ACTIVITY", "notification sound fail: ${e.message}")
        }
    }

    private fun releaseNotificationPlayer() {
        try {
            notificationPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            notificationPlayer = null
        } catch (_: Exception) {
            notificationPlayer = null
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationPermissionRationaleDialog()
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun showNotificationPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage(
                "Notifications are very important for this app. You'll receive alerts for:\n\n" +
                        "- Tournament starting soon\n" +
                        "- Tournament joining confirmation\n" +
                        "- Tournament results\n" +
                        "- Winnings credited to your account\n" +
                        "- Important account updates\n\n" +
                        "Please grant notification permission to stay updated with all tournament activities."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Not Now") { _, _ -> showNotificationPermissionDeniedDialog() }
            .setCancelable(false)
            .show()
    }

    private fun showNotificationPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Denied")
            .setMessage(
                "You've denied notification permission. You won't receive important alerts about " +
                        "tournaments, winnings, and account updates.\n\n" +
                        "You can enable notifications later in your device settings:\n\n" +
                        "Settings > Apps > EDM Fire > Notifications"
            )
            .setPositiveButton("Enable in Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Maybe Later") { _, _ ->
                Toast.makeText(this, "You can enable notifications later in settings", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun initializeClickSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        } else {
            @Suppress("DEPRECATION")
            soundPool = SoundPool(4, AudioManager.STREAM_MUSIC, 0)
        }
        soundPool?.setOnLoadCompleteListener { _, _, status -> soundLoaded = status == 0 }
        clickSoundId = soundPool?.load(this, R.raw.sci_fi_click, 1) ?: 0
    }

    private fun playClickSound() {
        if (soundLoaded) {
            soundPool?.play(clickSoundId, clickVolume, clickVolume, 1, 0, 1f)
        }
    }
}