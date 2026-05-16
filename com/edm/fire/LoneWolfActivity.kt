package com.edm.fire

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edm.fire.databinding.ActivityLoneWolfBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class LoneWolfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoneWolfBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoneWolfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initRemoteConfigAndSetup()

        binding.ivBack.setOnClickListener {
            onBackPressed()
        }
    }

    // ⚡ RemoteConfig se database URL fetch karo — cache disabled (dev mode)
    // Pehle: remoteConfig.getString() directly — cached/stale value
    // Ab: fetchAndActivate() — har baar fresh read
    private fun initRemoteConfigAndSetup() {
        remoteConfig = FirebaseRemoteConfig.getInstance()

        // 🔥 DEV MODE: Cache disabled — har baar fresh read
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        Log.d("REMOTE_CONFIG", "DEV MODE — Cache disabled, fresh fetch always")

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                val databaseUrl = if (task.isSuccessful) {
                    remoteConfig.getString("FirebaseDatabase_url")
                } else {
                    ""
                }

                Log.d("REMOTE_CONFIG", "Fetched URL: \"$databaseUrl\"")
                setupViewPager(databaseUrl)
            }
    }

    private fun setupViewPager(databaseUrl: String) {
        val adapter = LoneWolfViewPagerAdapter(this, databaseUrl)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }
}
