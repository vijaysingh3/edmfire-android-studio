package com.edm.fire

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edm.fire.databinding.ActivityBattleRoyalBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class BattleRoyalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBattleRoyalBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBattleRoyalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        initRemoteConfigAndSetup()

        binding.ivBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun initRemoteConfigAndSetup() {
        remoteConfig = FirebaseRemoteConfig.getInstance()

        // 🔥 DEV MODE: Cache disabled — har bar fresh read
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
        val adapter = BattleRoyalViewPagerAdapter(this, databaseUrl)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }
}