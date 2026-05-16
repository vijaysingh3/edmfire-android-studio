package com.edm.fire

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Switch
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var notificationSwitch: SwitchCompat
    private lateinit var vibrationSwitch: SwitchCompat
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initializeViews()


        setupToolbar()


        loadSettings()


        setupClickListeners()
    }

    private fun initializeViews() {
        notificationSwitch = findViewById(R.id.switch_notifications)
        vibrationSwitch = findViewById(R.id.switch_vibration)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar2)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadSettings() {
        // Load notification setting
        val notificationEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        notificationSwitch.isChecked = notificationEnabled

        // Load vibration setting
        val vibrationEnabled = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        vibrationSwitch.isChecked = vibrationEnabled

        Log.d("SettingsActivity", "Loaded settings - Notification: $notificationEnabled, Vibration: $vibrationEnabled")
    }

    private fun setupClickListeners() {
        // Notification switch listener
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
            if (isChecked) {
                enableNotifications()
            } else {
                disableNotifications()
            }
        }

        // Vibration switch listener
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationSetting(isChecked)
            if (isChecked) {
                enableVibration()
                // Test vibration
                testVibration()
            } else {
                disableVibration()
            }
        }
    }

    private fun saveNotificationSetting(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_NOTIFICATION_ENABLED, enabled)
            .apply()
        Log.d("SettingsActivity", "Notification setting saved: $enabled")
    }

    private fun saveVibrationSetting(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_VIBRATION_ENABLED, enabled)
            .apply()
        Log.d("SettingsActivity", "Vibration setting saved: $enabled")
    }

    private fun enableNotifications() {
        // Implementation to enable notifications
        // This would typically involve checking and requesting notification permissions
        Log.d("SettingsActivity", "Notifications enabled")
    }

    private fun disableNotifications() {
        // Implementation to disable notifications
        Log.d("SettingsActivity", "Notifications disabled")
    }

    private fun enableVibration() {
        // Implementation to enable vibration
        Log.d("SettingsActivity", "Vibration enabled")
    }

    private fun disableVibration() {
        // Implementation to disable vibration
        Log.d("SettingsActivity", "Vibration disabled")
    }

    private fun testVibration() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                // Use different vibration methods based on API level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // For API level 26 and above
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {

                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
                Log.d("SettingsActivity", "Test vibration performed")
            } else {
                Log.d("SettingsActivity", "Device does not support vibration")
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error performing test vibration: ${e.message}")
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}