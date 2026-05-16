package com.edm.fire

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edm.fire.databinding.ActivityFreeTournamentBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class FreeTournamentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFreeTournamentBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private val TAG = "FREE_DEBUG_ACTIVITY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Step 1: Activity onCreate started")

        binding = ActivityFreeTournamentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initRemoteConfigAndSetup()

        binding.ivBack.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            onBackPressed()
        }
    }

    private fun initRemoteConfigAndSetup() {
        Log.d(TAG, "Step 2: Initializing RemoteConfig")
        remoteConfig = FirebaseRemoteConfig.getInstance()

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                val databaseUrl = if (task.isSuccessful) {
                    val url = remoteConfig.getString("FirebaseDatabase_url")
                    Log.d(TAG, "Step 3: RemoteConfig Success. Fetched URL: $url")
                    url
                } else {
                    Log.e(TAG, "Step 3: RemoteConfig Failed. Task exception: ${task.exception}")
                    ""
                }

                if (databaseUrl.isEmpty()) {
                    Log.w(TAG, "Warning: Database URL is empty. Fragments might fail to load data.")
                }

                setupViewPager(databaseUrl)
            }
    }

    private fun setupViewPager(databaseUrl: String) {
        Log.d(TAG, "Step 4: Setting up ViewPager with URL: $databaseUrl")
        val adapter = ViewPagerAdapter(this, databaseUrl)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
        Log.d(TAG, "Step 5: TabLayoutMediator attached")
    }
}