package com.edm.fire

import android.annotation.SuppressLint
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class HelpActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var auth: FirebaseAuth
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val results = data?.dataString?.let { arrayOf(Uri.parse(it)) }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = false
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            // UI phone pe fit karne ke liye text zoom fix
            textZoom = 100
        }

        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false

        // ===== MAIN FIX: WebView me bottom padding = navigation bar height =====
        // Android kuch phones pe navigation bar ko window inset me count nahi karta
        // isliye manually bottom padding add karna padta hai
        // isse WebView ka content nav bar ke peeche nahi jayega
        // aur send button hamesha dikhega
        val navBarHeight = getNavigationBarHeight()
        Log.d("HelpDebug", "navBarHeight padding added: $navBarHeight px")
        webView.setPadding(0, 0, 0, navBarHeight)

        // FrameLayout wrapper with fitsSystemWindows
        // ye status bar ke liye automatic padding dega
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = true
        }
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(webView)
        setContentView(container)

        auth = FirebaseAuth.getInstance()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Height inject NAHI karna - ye hi problem cause kar raha tha!
                // CSS me height: 100% use kar rahe hai + WebView padding handle karega
                injectAuthToken()
                injectFcmToken()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@HelpActivity.filePathCallback = filePathCallback

                val chooserIntent = fileChooserParams?.createIntent()
                if (chooserIntent != null) {
                    try {
                        fileChooserLauncher.launch(chooserIntent)
                        return true
                    } catch (e: Exception) {
                        this@HelpActivity.filePathCallback = null
                        return false
                    }
                }

                this@HelpActivity.filePathCallback = null
                return false
            }
        }

        webView.loadUrl("https://edmfire-web.vercel.app/user/")
    }

    // Navigation bar height calculate karna
    // ye Android system resource se accurate value nikalta hai
    private fun getNavigationBarHeight(): Int {
        val resources: Resources = resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private fun injectAuthToken() {
        val user = auth.currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val token = tokenResult.token ?: return@addOnSuccessListener
            val js = "if(typeof receiveAuthToken==='function'){receiveAuthToken('$token');}"
            webView.evaluateJavascript(js, null)
        }
    }

    private fun injectFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val js = "if(typeof receiveFcmToken==='function'){receiveFcmToken('$token');}"
            webView.evaluateJavascript(js, null)
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
