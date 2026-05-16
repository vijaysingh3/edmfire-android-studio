package com.edm.fire

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edm.fire.databinding.ActivityReferBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class ReferActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReferBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var shareLink: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        remoteConfig = FirebaseRemoteConfig.getInstance()

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeRemoteConfig()

        binding.ivBack.setOnClickListener {
            onBackPressed()
        }

        binding.ivCopy.setOnClickListener {
            copyReferralCode()
        }

        binding.ivShare.setOnClickListener {
            shareReferral()
        }
    }

    private fun initializeRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
            fetchTimeoutInSeconds = 30
        }

        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    shareLink = remoteConfig.getString("share_link")
                    fetchReferralCodeFromFirestore()
                } else {
                    shareLink = ""
                    fetchReferralCodeFromFirestore()
                }
            }
    }

    private fun fetchReferralCodeFromFirestore() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("Users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val referralCode = document.getString("MyReferralCode")
                        if (!referralCode.isNullOrEmpty()) {
                            binding.tvReferCode.text = referralCode
                        } else {
                            binding.tvReferCode.text = "EDM12345"
                            Toast.makeText(this, "Referral code not found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        binding.tvReferCode.text = "EDM12345"
                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    binding.tvReferCode.text = "EDM12345"
                    Toast.makeText(this, "Failed to load referral code", Toast.LENGTH_SHORT).show()
                }
        } else {
            binding.tvReferCode.text = "EDM12345"
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyReferralCode() {
        val referralCode = binding.tvReferCode.text.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Referral Code", referralCode)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "Referral code copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun shareReferral() {
        val referralCode = binding.tvReferCode.text.toString()

        val message = if (shareLink.isNotEmpty()) {
            """Join EDM Fire using my referral code: $referralCode

🔥 Earn amazing rewards together!
📱 Download app: $shareLink?referral=$referralCode

Use my referral code: $referralCode
            """.trimIndent()
        } else {
            """Join EDM Fire using my referral code: $referralCode

🔥 Earn amazing rewards together!
📱 Download app and use my referral code: $referralCode

Use my referral code: $referralCode
            """.trimIndent()
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share Referral via")

        try {
            startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }
}