package com.edm.fire

import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edm.fire.databinding.ActivityKycBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class KycActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKycBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKycBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.etInGameUID.filters += InputFilter.LengthFilter(11)

        binding.btnSubmitKyc.setOnClickListener {
            performKyc()
        }
    }

    private fun performKyc() {
        val inGameName = binding.etInGameName.text.toString().trim()
        val inGameUID = binding.etInGameUID.text.toString().trim()
        val gameLevel = binding.etGameLevel.text.toString().trim()

        if (inGameName.isEmpty()) {
            showError("कृपया अपना In-Game Name दर्ज करें")
            binding.etInGameName.requestFocus()
            return
        }

        if (inGameName.length < 3) {
            showError("In-Game Name कम से कम 3 characters का होना चाहिए")
            binding.etInGameName.requestFocus()
            return
        }

        if (inGameUID.isEmpty()) {
            showError("कृपया अपना In-Game UID दर्ज करें")
            binding.etInGameUID.requestFocus()
            return
        }

        // Free Fire UID की लंबाई 7 से 11 अंकों तक हो सकती है।
        if (inGameUID.length < 7 || inGameUID.length > 11) {
            showError("UID 7 से 11 अंकों का होना चाहिए")
            binding.etInGameUID.requestFocus()
            return
        }

        if (gameLevel.isEmpty()) {
            showError("कृपया अपना Game Level दर्ज करें")
            binding.etGameLevel.requestFocus()
            return
        }

        val inGameUIDLong = inGameUID.toLongOrNull()
        val gameLevelInt = gameLevel.toIntOrNull()

        if (inGameUIDLong == null || inGameUIDLong <= 0) {
            showError("कृपया एक वैध UID दर्ज करें")
            binding.etInGameUID.requestFocus()
            return
        }

        if (gameLevelInt == null || gameLevelInt <= 0 || gameLevelInt > 100) {
            showError("Level 1 और 100 के बीच का एक positive number होना चाहिए")
            binding.etGameLevel.requestFocus()
            return
        }

        saveKycToFirestore(inGameName, inGameUIDLong, gameLevelInt)
    }

    private fun saveKycToFirestore(inGameName: String, inGameUID: Long, gameLevel: Int) {
        // UI को अपडेट करने के लिए एक हेल्पर फंक्शन का उपयोग करें
        showProgress(true)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("यूज़र लॉग इन नहीं है।")
            showProgress(false)
            return
        }

        val userDocRef = db.collection("Users").document(currentUser.uid)

        val kycData = hashMapOf<String, Any>(
            "UserName" to inGameName,
            "InGameUID" to inGameUID,
            "Level" to gameLevel,
            "KYCStatus" to "Game Kyc Verified",
            "LastUpdated" to FieldValue.serverTimestamp(),
            "KYCTimestamp" to System.currentTimeMillis()
        )


        userDocRef.set(kycData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("KycActivity", "KYC सफलतापूर्वक अपडेट/सेव हो गई।")
                showSuccess("KYC सफलतापूर्वक पूरी हो गई!")

                android.os.Handler().postDelayed({
                    finish()
                }, 1500)
            }
            .addOnFailureListener { e ->
                showError("KYC सेव करने में त्रुटि: ${e.message}")
                Log.e("KycActivity", "Firestore ऑपरेशन में त्रुटि", e)
            }
            .addOnCompleteListener {
                showProgress(false)
            }
    }


    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSubmitKyc.isEnabled = !show
        binding.btnSubmitKyc.text = if (show) "Processing..." else "KYC पूरी करें"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
