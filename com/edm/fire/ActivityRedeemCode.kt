package com.edm.fire

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ActivityRedeemCode : AppCompatActivity() {

    // ✅ NEW: Back Button
    private lateinit var ivBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_redeem_code)

        // ✅ NEW: Initialize and setup back button
        initializeViews()
        setupClickListeners()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // ✅ NEW: Initialize Views
    private fun initializeViews() {
        ivBack = findViewById(R.id.ivBack)
    }

    // ✅ NEW: Setup Click Listeners
    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            onBackPressed()
        }
    }
}