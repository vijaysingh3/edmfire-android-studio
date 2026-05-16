package com.edm.fire

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import java.text.DecimalFormat

class WalletFragment : Fragment() {

    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private lateinit var tvTotalAmount: TextView
    private lateinit var tvTopupCoins: TextView
    private lateinit var tvWinningsCoin: TextView
    private lateinit var tvReferralCoins: TextView

    private val RUPEE_TO_PAISA = 100
    private val decimalFormat = DecimalFormat("#.##")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        android.util.Log.d("WalletFragment", "🚀 WalletFragment onCreateView started")

        val view = inflater.inflate(R.layout.fragment_wallet, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        android.util.Log.d("WalletFragment", "✅ Firebase instances initialized")

        val btnDeposit = view.findViewById<View>(R.id.btn_deposit)
        val btnWithdraw = view.findViewById<View>(R.id.btn_withdraw)
        val tvRefer02 = view.findViewById<TextView>(R.id.tv_refer02)
        val ivReferralIcon = view.findViewById<ImageView>(R.id.iv_referral_icon)

        tvTotalAmount = view.findViewById(R.id.tv_total_amount)
        tvTopupCoins = view.findViewById(R.id.tv_topup_coins)
        tvWinningsCoin = view.findViewById(R.id.tv_winnings_coin)
        tvReferralCoins = view.findViewById(R.id.tv_referral_coins)

        setupRemoteConfig()
        loadImagesWithGlide(view)
        loadUserCoinsData()

        btnDeposit.setOnClickListener {
            android.util.Log.d("WalletFragment", "🔘 Deposit button clicked")
            navigateToDeposit()
        }

        btnWithdraw.setOnClickListener {
            android.util.Log.d("WalletFragment", "🔘 Withdraw button clicked")
            navigateToWithdrawal()
        }

        tvRefer02.setOnClickListener {
            android.util.Log.d("WalletFragment", "🔘 Refer button clicked")
            navigateToRefer()
        }

        ivReferralIcon.setOnClickListener {
            android.util.Log.d("WalletFragment", "🔘 Referral icon clicked")
            navigateToRefer()
        }

        setupViewPager(view)

        android.util.Log.d("WalletFragment", "✅ WalletFragment onCreateView completed")
        return view
    }

    // ============================================================
    // REMOTE CONFIG
    // ============================================================

    private fun setupRemoteConfig() {
        android.util.Log.d("WalletFragment", "🔧 Setting up Remote Config")
        remoteConfig = FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600
            })
            setDefaultsAsync(mapOf(
                "paymentPageUrl" to "",
                "PaymentSwitch" to "DepositActivity"
            ))
        }
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                android.util.Log.d("WalletFragment", "✅ Remote Config fetched successfully")
            } else {
                android.util.Log.w("WalletFragment", "⚠️ Remote Config fetch failed, using fallback URL")
            }
        }
    }

    private fun getPaymentPageUrl(): String {
        return remoteConfig.getString("paymentPageUrl")
    }

    private fun isWebPaymentMode(): Boolean {
        return remoteConfig.getString("PaymentSwitch").equals("Web", ignoreCase = true)
    }

    // ============================================================
    // COIN CONVERSION UTILITIES (PAISA → RUPEES)
    // ============================================================

    /**
     * Convert paisa to rupees (Double for decimal support)
     */
    private fun paisaToRupees(paisa: Long): Double {
        return paisa / 100.0
    }

    /**
     * Format coins for display with proper pluralization
     * Examples:
     * - 100 paisa (1 rupee) → "1 Coin"
     * - 150 paisa (1.5 rupees) → "1.5 Coins"
     * - 10000 paisa (100 rupees) → "100 Coins"
     */
    private fun formatCoins(paisa: Long): String {
        val rupees = paisaToRupees(paisa)
        val formatted = decimalFormat.format(rupees)
        return if (rupees == 1.0) "$formatted Coin" else "$formatted Coins"
    }

    /**
     * Format total amount without "Coins" word (for total display)
     */
    private fun formatTotalAmount(paisa: Long): String {
        val rupees = paisaToRupees(paisa)
        return decimalFormat.format(rupees)
    }

    // ============================================================
    // LOAD DATA FROM FIRESTORE
    // ============================================================

    private fun loadImagesWithGlide(view: View) {
        android.util.Log.d("WalletFragment", "🖼️ Loading images with Glide")

        val pngTotalCoins: ImageView = view.findViewById(R.id.png_total_coins)
        val ivTopup: ImageView = view.findViewById(R.id.iv_topup_icon)
        val ivWinning: ImageView = view.findViewById(R.id.iv_winning_icon)
        val ivReferral: ImageView = view.findViewById(R.id.iv_referral_icon)

        Glide.with(this)
            .load(R.drawable.ic_coin)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(pngTotalCoins)

        Glide.with(this)
            .load(android.R.drawable.stat_sys_download_done)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivTopup)

        Glide.with(this)
            .load(R.drawable.ic_coin)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivWinning)

        Glide.with(this)
            .load(android.R.drawable.ic_menu_share)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .into(ivReferral)

        android.util.Log.d("WalletFragment", "✅ Images loaded")
    }

    private fun loadUserCoinsData() {
        val currentUser = auth.currentUser
        android.util.Log.d("WalletFragment", "📊 Loading user coins data for user: ${currentUser?.uid ?: "null"}")

        currentUser?.let { user ->
            db.collection("Users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    android.util.Log.d("WalletFragment", "✅ Firestore document retrieved")

                    if (document.exists()) {
                        // All values are stored in PAISA in database
                        val referralBonusInPaisa = document.getLong("MyReferralBonus") ?: 0L
                        val winningCoinsInPaisa = document.getLong("WinningCoins") ?: 0L
                        val topUpCoinsInPaisa = document.getLong("TopUpCoins") ?: 0L

                        android.util.Log.d("WalletFragment", "💰 Raw values (paisa):")
                        android.util.Log.d("WalletFragment", "   ReferralBonus: $referralBonusInPaisa paisa")
                        android.util.Log.d("WalletFragment", "   WinningCoins: $winningCoinsInPaisa paisa")
                        android.util.Log.d("WalletFragment", "   TopUpCoins: $topUpCoinsInPaisa paisa")

                        // Convert to rupees for display
                        val referralBonusInRupees = paisaToRupees(referralBonusInPaisa)
                        val winningCoinsInRupees = paisaToRupees(winningCoinsInPaisa)
                        val topUpCoinsInRupees = paisaToRupees(topUpCoinsInPaisa)
                        val totalCoinsInRupees = referralBonusInRupees + winningCoinsInRupees + topUpCoinsInRupees
                        val totalCoinsInPaisa = referralBonusInPaisa + winningCoinsInPaisa + topUpCoinsInPaisa

                        android.util.Log.d("WalletFragment", "💰 Converted values (rupees):")
                        android.util.Log.d("WalletFragment", "   ReferralBonus: ₹$referralBonusInRupees")
                        android.util.Log.d("WalletFragment", "   WinningCoins: ₹$winningCoinsInRupees")
                        android.util.Log.d("WalletFragment", "   TopUpCoins: ₹$topUpCoinsInRupees")
                        android.util.Log.d("WalletFragment", "   Total: ₹$totalCoinsInRupees")

                        // Update UI with formatted strings
                        tvTotalAmount.text = formatTotalAmount(totalCoinsInPaisa)
                        tvTopupCoins.text = formatCoins(topUpCoinsInPaisa)
                        tvWinningsCoin.text = formatCoins(winningCoinsInPaisa)
                        tvReferralCoins.text = formatCoins(referralBonusInPaisa)

                        android.util.Log.d("WalletFragment", "✅ UI updated successfully")
                    } else {
                        android.util.Log.w("WalletFragment", "⚠️ User document does not exist")
                        setDefaultValues()
                    }
                }
                .addOnFailureListener { exception ->
                    android.util.Log.e("WalletFragment", "❌ Failed to load user data: ${exception.message}")
                    setDefaultValues()
                }
        } ?: run {
            android.util.Log.w("WalletFragment", "⚠️ No current user found")
            setDefaultValues()
        }
    }

    private fun setDefaultValues() {
        android.util.Log.d("WalletFragment", "📊 Setting default values (0)")
        tvTotalAmount.text = "0"
        tvTopupCoins.text = "0 Coins"
        tvWinningsCoin.text = "0 Coins"
        tvReferralCoins.text = "0 Coins"
    }

    // ============================================================
    // NAVIGATION FUNCTIONS
    // ============================================================

    private fun navigateToDeposit() {
        android.util.Log.d("WalletFragment", "🚀 Deposit button clicked")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            android.util.Log.w("WalletFragment", "❌ No user logged in")
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        if (isWebPaymentMode()) {
            // Web mode → WebView with paymentPageUrl
            android.util.Log.d("WalletFragment", "🌐 PaymentSwitch = Web → opening WebView")
            currentUser.getIdToken(false).addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val token = task.result!!.token ?: ""
                    val uid = currentUser.uid
                    val paymentUrl = getPaymentPageUrl()

                    android.util.Log.d("WalletFragment", "🔗 Opening payment page URL: $paymentUrl")
                    android.util.Log.d("WalletFragment", "👤 User UID: $uid")

                    val intent = Intent(requireContext(), WebviewActivity::class.java)
                    intent.putExtra("url", paymentUrl)
                    intent.putExtra("token", token)
                    intent.putExtra("uid", uid)
                    intent.putExtra("title", "Add Coins")
                    startActivity(intent)
                } else {
                    android.util.Log.e("WalletFragment", "❌ Failed to get ID token")
                    Toast.makeText(requireContext(), "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // DepositActivity mode (fallback/default)
            android.util.Log.d("WalletFragment", "📱 PaymentSwitch = DepositActivity → opening DepositActivity")
            startActivity(Intent(requireContext(), DepositActivity::class.java))
        }
    }

    private fun navigateToWithdrawal() {
        android.util.Log.d("WalletFragment", "🚀 Navigating to Withdrawal")
        val intent = Intent(requireContext(), WithdrawalActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToRefer() {
        android.util.Log.d("WalletFragment", "🚀 Navigating to Refer")
        val intent = Intent(requireContext(), ReferActivity::class.java)
        startActivity(intent)
    }

    // ============================================================
    // VIEWPAGER SETUP
    // ============================================================

    private fun setupViewPager(view: View) {
        android.util.Log.d("WalletFragment", "📄 Setting up ViewPager")

        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager = view.findViewById(R.id.view_pager)

        val adapter = WalletPagerAdapter(requireActivity())
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "All Transactions"
            }
        }.attach()

        android.util.Log.d("WalletFragment", "✅ ViewPager setup completed")
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onResume() {
        super.onResume()
        android.util.Log.d("WalletFragment", "🔄 onResume - Reloading user data")
        loadUserCoinsData()
    }

    companion object {
        @JvmStatic
        fun newInstance() = WalletFragment()
    }
}