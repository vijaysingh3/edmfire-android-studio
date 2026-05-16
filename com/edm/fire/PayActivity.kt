package com.edm.fire

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class PayActivity : AppCompatActivity() {

    private lateinit var shimmerContainer: ShimmerFrameLayout
    private lateinit var ivQRCode: ImageView
    private lateinit var tvAmount: TextView
    private lateinit var tvBonus: TextView
    private lateinit var tvTotalCoins: TextView
    private lateinit var tvPayAmount: TextView
    private lateinit var tvUPIId: TextView
    private lateinit var upiDetailsLayout: LinearLayout
    private lateinit var btnScreenshot: Button
    private lateinit var btnVerifyPayment: Button
    private lateinit var btnCopyUPI: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var ivBack: ImageView
    private lateinit var scrollView: ScrollView
    private lateinit var demoUtr: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private var amount: Double = 0.0
    private var bonus: Int = 0
    private var totalCoins: Int = 0
    private var upiId: String = ""
    private var currentUpiLink: String = ""
    private var currentTransactionRef: String = ""
    private var screenshotPlayer: MediaPlayer? = null

    private lateinit var bharatPayApiUrl: String
    private lateinit var authToken: String
    private lateinit var merchantId: String
    private lateinit var referer: String
    private lateinit var origin: String
    private lateinit var contentType: String
    private var supabaseFunctionUrl: String = ""
    private var supabaseAnonKey: String = ""

    companion object {
        private const val QR_CODE_SIZE = 800
        private const val UPI_PREFIX = "upi://pay?pa="
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pay)

        initializeViews()
        setupFirebase()
        getIntentData()
        loadRemoteConfig()
        setupClickListeners()
        performAutoScrollAnimation()
    }

    private fun initializeViews() {
        shimmerContainer = findViewById(R.id.shimmerContainer)
        ivQRCode = findViewById(R.id.ivQRCode)
        tvAmount = findViewById(R.id.tvAmount)
        tvBonus = findViewById(R.id.tvBonus)
        tvTotalCoins = findViewById(R.id.tvTotalCoins)
        tvPayAmount = findViewById(R.id.tv_pay_amount)
        tvUPIId = findViewById(R.id.tvUPIId)
        upiDetailsLayout = findViewById(R.id.upiDetailsLayout)
        btnScreenshot = findViewById(R.id.btnScreenshot)
        btnVerifyPayment = findViewById(R.id.btnVerifyPayment)
        btnCopyUPI = findViewById(R.id.btnCopyUPI)
        progressBar = findViewById(R.id.progressBar)
        ivBack = findViewById(R.id.ivBack)
        scrollView = findViewById(R.id.scrollView)
        demoUtr = findViewById(R.id.demo_utr)

        shimmerContainer.visibility = View.VISIBLE
        shimmerContainer.startShimmer()
        ivQRCode.visibility = View.GONE
        upiDetailsLayout.visibility = View.GONE
        progressBar.visibility = View.GONE
        tvPayAmount.visibility = View.GONE

        Glide.with(this).load(R.drawable.utr_demo).into(demoUtr)
    }

    private fun performAutoScrollAnimation() {
        scrollView.post {
            val scrollViewHeight = scrollView.height
            val contentHeight = scrollView.getChildAt(0).height
            val scrollAmount = contentHeight - scrollViewHeight
            scrollView.smoothScrollTo(0, scrollAmount)

            Handler(Looper.getMainLooper()).postDelayed({
                scrollView.smoothScrollTo(0, 0)
            }, 3000)
        }
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        remoteConfig = FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600
            })
            setDefaultsAsync(mapOf(
                "Bharat_pay_api_url" to "",
                "auth_token" to "",
                "merchant_id" to "",
                "referer" to "",
                "origin" to "",
                "content_type" to "",
                "merchant_upi_id" to "",
                "merchant_name" to "",
                "pay_writer_qr_code" to "",
                "supabase_anon_key" to ""
            ))
        }
    }

    private fun getIntentData() {
        amount = intent.getDoubleExtra("EXTRA_AMOUNT", 0.0)
        bonus = intent.getIntExtra("EXTRA_BONUS", 0)
        totalCoins = intent.getIntExtra("EXTRA_TOTAL", 0)
        updatePaymentSummary()
    }

    private fun updatePaymentSummary() {
        tvAmount.text = "₹${amount.toInt()}"
        tvBonus.text = "+ ₹$bonus"
        tvTotalCoins.text = "$totalCoins"
        tvPayAmount.text = "Pay: ₹${amount.toInt()}"
        tvPayAmount.visibility = View.VISIBLE
    }

    private fun loadRemoteConfig() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fetchBharatPayParameters()
            } else {
                showError("Payment config load failed")
            }
        }
    }

    private fun fetchBharatPayParameters() {
        try {
            bharatPayApiUrl = remoteConfig.getString("Bharat_pay_api_url")
            authToken = remoteConfig.getString("auth_token")
            merchantId = remoteConfig.getString("merchant_id")
            referer = remoteConfig.getString("referer")
            origin = remoteConfig.getString("origin")
            contentType = remoteConfig.getString("content_type")
            upiId = remoteConfig.getString("merchant_upi_id")
            val merchantName = remoteConfig.getString("merchant_name")
            supabaseFunctionUrl = remoteConfig.getString("pay_writer_qr_code")
            supabaseAnonKey = remoteConfig.getString("supabase_anon_key")

            if (bharatPayApiUrl.isEmpty() || authToken.isEmpty() || merchantId.isEmpty() || upiId.isEmpty()) {
                showError("Payment config incomplete")
                return
            }
            generateQRCode(amount, merchantName)
        } catch (e: Exception) {
            showError("Config error: ${e.message}")
        }
    }

    private fun generateQRCode(amount: Double, merchantName: String) {
        try {
            val randomRef = (100000..999999).random()
            val transactionNote = "TxnRef$randomRef"
            currentTransactionRef = transactionNote
            currentUpiLink = buildUpiString(amount, merchantName, transactionNote)
            val qrBitmap = createQRBitmap(currentUpiLink)

            saveQrRequestViaSupabase(amount, transactionNote)
            updateUIAfterQRGeneration(qrBitmap)
        } catch (e: Exception) {
            showError("QR Generation failed: ${e.message}")
        }
    }

    private fun saveQrRequestViaSupabase(qrAmount: Double, transactionRef: String) {
        val userId = auth.currentUser?.uid ?: return
        val indianDateTime = getCurrentIndianDateTime()

        Thread {
            try {
                val url = URL(supabaseFunctionUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val requestBody = JSONObject().apply {
                    put("action", "saveQrRequest")
                    put("data", JSONObject().apply {
                        put("amount", qrAmount)
                        put("transactionRef", transactionRef)
                        put("indianDateTime", indianDateTime)
                        put("upiId", upiId)
                        put("userId", userId)
                        put("status", "pending")
                        put("bonus", bonus)
                        put("totalCoins", totalCoins)
                    })
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()

                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                // silent fail — QR already generate ho chuka hai
            }
        }.start()
    }

    private fun getCurrentIndianDateTime(): String {
        val indianTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH)
        sdf.timeZone = indianTimeZone
        return sdf.format(Date())
    }

    private fun buildUpiString(amount: Double, merchantName: String, note: String): String {
        return StringBuilder()
            .append(UPI_PREFIX)
            .append(upiId)
            .append("&pn=").append(Uri.encode(merchantName))
            .append("&am=").append(String.format("%.2f", amount))
            .append("&tn=").append(Uri.encode(note))
            .append("&cu=INR")
            .toString()
    }

    private fun createQRBitmap(upiString: String): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            upiString,
            BarcodeFormat.QR_CODE,
            QR_CODE_SIZE,
            QR_CODE_SIZE
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }

    private fun updateUIAfterQRGeneration(qrBitmap: Bitmap) {
        shimmerContainer.stopShimmer()
        shimmerContainer.visibility = View.GONE
        ivQRCode.setImageBitmap(qrBitmap)
        ivQRCode.visibility = View.VISIBLE
        tvUPIId.text = upiId
        upiDetailsLayout.visibility = View.VISIBLE
        tvPayAmount.visibility = View.VISIBLE
        btnScreenshot.isEnabled = true
        btnVerifyPayment.isEnabled = true
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener { onBackPressed() }
        btnCopyUPI.setOnClickListener { copyToClipboard(upiId, "UPI ID") }
        btnScreenshot.setOnClickListener { captureScreenshot() }
        btnVerifyPayment.setOnClickListener { navigateToVerification() }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun captureScreenshot() {
        try {
            val qrContainer = findViewById<CardView>(R.id.cardQRContainer)
            qrContainer.isDrawingCacheEnabled = true
            val screenshot = Bitmap.createBitmap(qrContainer.drawingCache)
            qrContainer.isDrawingCacheEnabled = false
            playScreenshotSound()
            Toast.makeText(this, "Screenshot captured", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playScreenshotSound() {
        try {
            screenshotPlayer?.release()
            screenshotPlayer = MediaPlayer.create(this, R.raw.camera_shutter)
            screenshotPlayer?.start()
        } catch (e: Exception) {}
    }

    private fun navigateToVerification() {
        progressBar.visibility = View.VISIBLE
        btnVerifyPayment.isEnabled = false
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, PaymentVerification::class.java).apply {
                putExtra("EXTRA_AMOUNT", amount)
                putExtra("EXTRA_BONUS", bonus)
                putExtra("EXTRA_TOTAL", totalCoins)
                putExtra("EXTRA_QR_AMOUNT", amount)
                putExtra("EXTRA_TRANSACTION_REF", currentTransactionRef)
            }
            startActivity(intent)
            finish()
        }, 500)
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        btnVerifyPayment.isEnabled = false
        btnScreenshot.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotPlayer?.release()
        shimmerContainer.stopShimmer()
    }
}