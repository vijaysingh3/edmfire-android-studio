package com.edm.fire

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class MainActivity : AppCompatActivity() {

    // yeh constants hain, kabhi change nahi honge
    private val PREFS_NAME = "AppVersionPrefs"
    private val KEY_LAST_CHECK_TIME = "last_check_time"
    private val KEY_VERIFIED_VERSION = "verified_version"

    // ✅ cache interval 30 min se 5 hours kar diya
    private val INTERVAL_5_HOURS: Long = 5 * 60 * 60 * 1000L

    private val splashDelay: Long = 2000L

    // ✅ retry system ke liye constants — 3 silent retries, 2 second gap
    private val MAX_RETRY_COUNT = 3
    private val RETRY_DELAY_MS = 2000L
    private var retryCount = 0

    // yeh baad mein initialize honge
    private lateinit var db: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var appLogo: android.widget.ImageView
    private lateinit var versionTextView: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: android.widget.ProgressBar

    // local app version — var hai kyunki assign hoti hai baad mein
    private var localVersionName: String = ""

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        db = FirebaseFirestore.getInstance()
        remoteConfig = FirebaseRemoteConfig.getInstance()

        appLogo = findViewById(R.id.appLogo)
        versionTextView = findViewById(R.id.tv_app_version)
        tvStatus = findViewById(R.id.tv_status)
        progressBar = findViewById(R.id.progressBar)

        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Initializing..."

        Glide.with(this)
            .load(R.drawable.applogo)
            .placeholder(R.drawable.applogo)
            .error(R.drawable.applogo)
            .transition(DrawableTransitionOptions.withCrossFade(500))
            .into(appLogo)

        // local version read karo gradle se
        localVersionName = getAppVersion()

        if (localVersionName.isEmpty()) {
            progressBar.visibility = View.GONE
            tvStatus.text = "Version read error."
            showBlockedDialog("App version detect nahi ho saka. Reinstall karo.")
            return
        }

        versionTextView.text = "Version $localVersionName"

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeRemoteConfig()

        handler.postDelayed({ checkVersionLogic() }, splashDelay)
    }

    // gradle se actual version name read karta hai
    private fun getAppVersion(): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager
                    .getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    .versionName ?: ""
            } else {
                @Suppress("DEPRECATION")
                packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // network available hai ya nahi check karta hai
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun initializeRemoteConfig() {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("new_version" to ""))
        remoteConfig.fetchAndActivate()
    }

    // ✅ STRICT VERSION CHECK LOGIC
    // Rule 1: Cache check karo pehle
    // Rule 2: Cache valid hai aur version match karta hai → direct navigate
    // Rule 3: Cache nahi / expired → server se verify karo
    // Rule 4: Network nahi → entry block, no internet dialog
    // Rule 5: Server fail → pehle silent retry (3 baar), tabhi dialog
    // Rule 6: Version mismatch → update dialog
    private fun checkVersionLogic() {
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val cachedVersion = prefs.getString(KEY_VERIFIED_VERSION, null)
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastCheckTime

        // ✅ cache valid tab hai jab 5 hours se kam time hua ho aur version match kare
        val cacheValid = lastCheckTime != 0L &&
                timeDiff < INTERVAL_5_HOURS &&
                !cachedVersion.isNullOrEmpty() &&
                cachedVersion == localVersionName

        if (cacheValid) {
            // ✅ Cache mein verified version hai → fast navigate
            tvStatus.text = "Version verified (cache)."
            progressBar.visibility = View.GONE
            navigateToNext()
        } else {
            // cache nahi hai ya expired → server verify zaroori hai
            // pehle network check karo
            if (!isNetworkAvailable()) {
                progressBar.visibility = View.GONE
                tvStatus.text = "No internet connection."
                // ✅ Network nahi hai → entry block, dialog dikhao
                showNoInternetDialog()
            } else {
                // ✅ retry count reset karo fresh check ke liye
                retryCount = 0
                verifyWithServer()
            }
        }
    }

    // ✅ IMPROVED: pehle 3 silent retries karo — tabhi dialog dikhao
    private fun verifyWithServer() {
        tvStatus.text = if (retryCount == 0) "Verifying version..." else "Retrying... (${retryCount}/$MAX_RETRY_COUNT)"
        progressBar.visibility = View.VISIBLE

        db.collection("AppVersion")
            .document("oxOggxjiTFhG8MZqNXhd")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // database se remote version read karo
                    val remoteVersion = document.getString("AppVersion") ?: ""

                    if (remoteVersion.isNotEmpty() && remoteVersion == localVersionName) {
                        // ✅ Version match → cache save karo aur navigate karo
                        retryCount = 0
                        saveVerifiedCache(localVersionName)
                        tvStatus.text = "Version up to date."
                        progressBar.visibility = View.GONE
                        navigateToNext()
                    } else {
                        // ✅ Version mismatch → update dialog dikhao (remote version show hoga)
                        retryCount = 0
                        progressBar.visibility = View.GONE
                        tvStatus.text = "Update required."
                        showUpdateRequiredDialog(remoteVersion)
                    }
                } else {
                    // document exist nahi karta → entry block
                    retryCount = 0
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Configuration error."
                    showBlockedDialog("Server configuration error. Please try later.")
                }
            }
            .addOnFailureListener {
                // ✅ SMART RETRY: pehle 3 baar silently retry karo, user ko turant error mat dikhao
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++
                    tvStatus.text = "Connecting... (${retryCount}/$MAX_RETRY_COUNT)"
                    handler.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            // ✅ network dobara check karo retry se pehle
                            if (isNetworkAvailable()) {
                                verifyWithServer()
                            } else {
                                retryCount = 0
                                progressBar.visibility = View.GONE
                                tvStatus.text = "No internet connection."
                                showNoInternetDialog()
                            }
                        }
                    }, RETRY_DELAY_MS)
                } else {
                    // ✅ 3 retries ke baad bhi fail → ab dialog dikhao
                    retryCount = 0
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Server unreachable."
                    showServerErrorDialog()
                }
            }
    }

    // verified version aur time cache mein save karo
    private fun saveVerifiedCache(version: String) {
        prefs.edit().apply {
            putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            putString(KEY_VERIFIED_VERSION, version)
            apply()
        }
    }

    private fun navigateToNext() {
        startActivity(Intent(this, SignUpActivity::class.java))
        finish()
    }

    private fun getSafeDownloadLink(): String {
        val raw = remoteConfig.getString("new_version").trim()
        if (raw.isEmpty()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    // ✅ UPDATE REQUIRED DIALOG — database wala remoteVersion badge mein show hoga
    private fun showUpdateRequiredDialog(remoteVersion: String) {
        val downloadLink = getSafeDownloadLink()
        val dialog = AlertDialog.Builder(this, 0).create()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2E"))
                cornerRadius = 28f
            }
        }

        val accentBar = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#6C63FF"), Color.parseColor("#FF6584"))
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6
            )
        }

        val iconWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        val iconTv = TextView(this).apply {
            text = "🚀"
            textSize = 42f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#16213E"))
                setStroke(2, Color.parseColor("#6C63FF"))
                cornerRadius = 60f
            }
            layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        iconWrapper.addView(iconTv)

        val tvTitle = TextView(this).apply {
            text = "Update Available!"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 0)
        }

        val badgeWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        // ✅ database se aaya remoteVersion yahan show hoga — local version nahi
        val badge = TextView(this).apply {
            text = "  v$remoteVersion  "
            textSize = 15f
            setTextColor(Color.parseColor("#A0FFD6"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F3460"))
                setStroke(1, Color.parseColor("#6C63FF"))
                cornerRadius = 30f
            }
            setPadding(24, 10, 24, 10)
        }
        badgeWrapper.addView(badge)

        val tvSub = TextView(this).apply {
            text = "Naya version available hai.\nBehtar experience ke liye update karo."
            textSize = 14f
            setTextColor(Color.parseColor("#B0B8D0"))
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1f)
            setPadding(40, 16, 40, 8)
        }

        val divider = View(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#2A2A4A")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(32, 20, 32, 0) }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 20, 24, 28)
        }

        val btnExit = Button(this).apply {
            text = "Exit"
            textSize = 14f
            setTextColor(Color.parseColor("#8888AA"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(1, Color.parseColor("#3A3A5A"))
                cornerRadius = 40f
            }
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, 112, 1f).apply {
                setMargins(0, 0, 12, 0)
            }
            setOnClickListener { dialog.dismiss(); finish() }
        }

        val btnDownload = Button(this).apply {
            text = "Update Now"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#6C63FF"), Color.parseColor("#FF6584"))
            ).apply { cornerRadius = 40f }
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, 112, 2f)
            setOnClickListener {
                if (downloadLink.isNotBlank()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadLink)))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Link unavailable", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Download link not set", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
                finish()
            }
        }

        btnRow.addView(btnExit)
        btnRow.addView(btnDownload)

        root.addView(accentBar)
        root.addView(iconWrapper)
        root.addView(tvTitle)
        root.addView(badgeWrapper)
        root.addView(tvSub)
        root.addView(divider)
        root.addView(btnRow)

        dialog.setView(root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    // ✅ NO INTERNET DIALOG — network nahi hone par dikhega
    private fun showNoInternetDialog() {
        val dialog = AlertDialog.Builder(this, 0).create()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2E"))
                cornerRadius = 28f
            }
        }

        val accentBar = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E53"))
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6
            )
        }

        val iconWrapper = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }
        val iconTv = TextView(this).apply {
            text = "📡"
            textSize = 42f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#16213E"))
                setStroke(2, Color.parseColor("#FF6B6B"))
                cornerRadius = 60f
            }
            layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        iconWrapper.addView(iconTv)

        val tvTitle = TextView(this).apply {
            text = "No Internet"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 0)
        }

        val tvMsg = TextView(this).apply {
            text = "Version verify karna zaroori hai.\nInternet connect karke retry karo."
            textSize = 14f
            setTextColor(Color.parseColor("#B0B8D0"))
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1f)
            setPadding(40, 12, 40, 8)
        }

        val divider = View(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#2A2A4A")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(32, 20, 32, 0) }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 20, 24, 28)
        }

        val btnExit = Button(this).apply {
            text = "Exit"
            textSize = 14f
            setTextColor(Color.parseColor("#8888AA"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(1, Color.parseColor("#3A3A5A"))
                cornerRadius = 40f
            }
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, 112, 1f).apply {
                setMargins(0, 0, 12, 0)
            }
            setOnClickListener { dialog.dismiss(); finish() }
        }

        val btnRetry = Button(this).apply {
            text = "Retry"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF8E53"))
            ).apply { cornerRadius = 40f }
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, 112, 2f)
            setOnClickListener {
                dialog.dismiss()
                // retry par pehle network check karo
                if (!isNetworkAvailable()) {
                    showNoInternetDialog()
                } else {
                    // ✅ manual retry par bhi retry count reset karo
                    retryCount = 0
                    progressBar.visibility = View.VISIBLE
                    tvStatus.text = "Retrying..."
                    verifyWithServer()
                }
            }
        }

        btnRow.addView(btnExit)
        btnRow.addView(btnRetry)

        root.addView(accentBar)
        root.addView(iconWrapper)
        root.addView(tvTitle)
        root.addView(tvMsg)
        root.addView(divider)
        root.addView(btnRow)

        dialog.setView(root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    // ✅ SERVER ERROR DIALOG — sirf tab dikhega jab 3 retries ke baad bhi fail ho
    private fun showServerErrorDialog() {
        val dialog = AlertDialog.Builder(this, 0).create()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2E"))
                cornerRadius = 28f
            }
        }

        val accentBar = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF4444"), Color.parseColor("#CC0000"))
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6
            )
        }

        val iconWrapper = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }
        val iconTv = TextView(this).apply {
            text = "⚠️"
            textSize = 42f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#16213E"))
                setStroke(2, Color.parseColor("#FF4444"))
                cornerRadius = 60f
            }
            layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        iconWrapper.addView(iconTv)

        val tvTitle = TextView(this).apply {
            text = "Please Check Internet🛜 "
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 0)
        }

        val tvMsg = TextView(this).apply {
            text = "Server se connect nahi ho saka.\nplease connect with internet🌐."
            textSize = 14f
            setTextColor(Color.parseColor("#B0B8D0"))
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1f)
            setPadding(40, 12, 40, 8)
        }

        val divider = View(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#2A2A4A")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(32, 20, 32, 0) }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 20, 24, 28)
        }

        val btnExit = Button(this).apply {
            text = "Exit"
            textSize = 14f
            setTextColor(Color.parseColor("#8888AA"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(1, Color.parseColor("#3A3A5A"))
                cornerRadius = 40f
            }
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, 112, 1f).apply {
                setMargins(0, 0, 12, 0)
            }
            setOnClickListener { dialog.dismiss(); finish() }
        }

        val btnRetry = Button(this).apply {
            text = "Retry"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF4444"), Color.parseColor("#CC0000"))
            ).apply { cornerRadius = 40f }
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, 112, 2f)
            setOnClickListener {
                dialog.dismiss()
                // retry par pehle network check karo phir server
                if (!isNetworkAvailable()) {
                    showNoInternetDialog()
                } else {
                    // ✅ manual retry par bhi retry count reset karo
                    retryCount = 0
                    progressBar.visibility = View.VISIBLE
                    tvStatus.text = "Retrying..."
                    verifyWithServer()
                }
            }
        }

        btnRow.addView(btnExit)
        btnRow.addView(btnRetry)

        root.addView(accentBar)
        root.addView(iconWrapper)
        root.addView(tvTitle)
        root.addView(tvMsg)
        root.addView(divider)
        root.addView(btnRow)

        dialog.setView(root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showBlockedDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Access Blocked")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}