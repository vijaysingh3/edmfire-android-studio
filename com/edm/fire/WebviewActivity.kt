package com.edm.fire

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * WebviewActivity — Opens web pages INSIDE the app.
 *
 * Existing behavior preserved:
 *   - AppCompatActivity + XML layout (R.layout.activity_webview)
 *   - Same IDs: R.id.webView, R.id.btn_back, R.id.tv_title
 *   - Intent extras: "url", "title" (same as before)
 *
 * New features added:
 *   - Firebase token support: intent.putExtra("token", firebaseIdToken)
 *   - UPI deep links open in external UPI app (user stays in app otherwise)
 *   - WebView back navigation on hardware back button
 *   - Proper WebView lifecycle (pause/resume/destroy)
 *   - Better WebView settings (DOM storage, viewport, cache)
 *   - JS Bridge "AndroidBridge" for web ↔ app communication
 *   - Loading progress bar (auto hide on page load)
 *   - Custom error page (no URL/token leak on no internet)
 */
class WebviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var progressBar: ProgressBar

    // error page me retry ke liye
    private var lastLoadedUrl: String = ""

    // internet disconnected error code (API level agnostic)
    private val ERROR_NET_DISCONNECTED = -2

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // ===== IDs bind =====
        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btn_back)
        tvTitle = findViewById(R.id.tv_title)
        progressBar = findViewById(R.id.progressBar)

        // ===== Intent extras =====
        val url = intent.getStringExtra("url") ?: "https://www.edmfire.in"
        val title = intent.getStringExtra("title") ?: "Edm Fire"
        val token = intent.getStringExtra("token") ?: ""

        tvTitle.text = title

        // Back Button
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        // ===== WebView Setup =====
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            userAgentString = userAgentString.replace("wv", "")
        }

        // ===== WebViewClient =====
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val urlStr = request.url.toString()

                // UPI deep links — external UPI app me open
                if (urlStr.startsWith("upi://")) {
                    try {
                        val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                        upiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(upiIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this@WebviewActivity,
                            "No UPI app found", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView?, urlStr: String?, favicon: Bitmap?) {
                super.onPageStarted(view, urlStr, favicon)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            override fun onPageFinished(view: WebView?, urlStr: String?) {
                super.onPageFinished(view, urlStr)
                progressBar.visibility = View.GONE

                // token localStorage me inject (SPA ke liye)
                if (token.isNotEmpty()) {
                    view?.evaluateJavascript(
                        "try { localStorage.setItem('auth_token', '$token'); } catch(e) {}",
                        null
                    )
                }
            }

            // ===== Custom error page — no URL/token leak =====
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)

                // sirf main page errors handle karo (sub-resources skip)
                if (request?.isForMainFrame == true) {
                    showErrorPage(error.errorCode)
                }
            }

            // ===== OLD API support (API < 23) =====
            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showErrorPage(errorCode)
            }
        }

        // ===== WebChromeClient — progress % tracking =====
        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = false
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // ===== JavaScript Bridge =====
        webView.addJavascriptInterface(WebViewBridge(), "AndroidBridge")

        // ===== Load URL with token =====
        val finalUrl = if (token.isNotEmpty() && !url.contains("?")) {
            "$url?token=$token"
        } else if (token.isNotEmpty()) {
            "$url&token=$token"
        } else {
            url
        }

        lastLoadedUrl = finalUrl
        webView.loadUrl(finalUrl)
    }

    // ===== Custom Error Page — clean gaming style, no token exposed =====
    private fun showErrorPage(errorCode: Int) {
        val titleText = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> "Server Not Found"
            WebViewClient.ERROR_CONNECT -> "Connection Failed"
            WebViewClient.ERROR_TIMEOUT -> "Request Timed Out"
            ERROR_NET_DISCONNECTED -> "No Internet Connection"
            else -> "Something Went Wrong"
        }

        val descText = when (errorCode) {
            ERROR_NET_DISCONNECTED ->
                "Please check your internet connection and try again."
            else ->
                "Unable to load the page. Please try again later."
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                        background: linear-gradient(135deg, #0a0015, #1a0030, #000d1a);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: #fff;
                    }
                    .container {
                        text-align: center;
                        padding: 2rem;
                        max-width: 320px;
                    }
                    .icon-circle {
                        width: 80px; height: 80px;
                        border-radius: 50%;
                        background: rgba(255, 60, 60, 0.15);
                        border: 2px solid rgba(255, 60, 60, 0.3);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin: 0 auto 1.2rem;
                        font-size: 2rem;
                    }
                    .title {
                        font-size: 1.1rem;
                        font-weight: 700;
                        margin-bottom: 0.5rem;
                        color: #ff6b6b;
                    }
                    .desc {
                        font-size: 0.8rem;
                        color: rgba(255,255,255,0.5);
                        line-height: 1.5;
                        margin-bottom: 1.5rem;
                    }
                    .retry-btn {
                        display: inline-block;
                        padding: 10px 28px;
                        background: linear-gradient(135deg, #00ff88, #00c8ff);
                        color: #000;
                        font-weight: 700;
                        font-size: 0.85rem;
                        border: none;
                        border-radius: 12px;
                        cursor: pointer;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .retry-btn:active { transform: scale(0.96); }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon-circle">⚠️</div>
                    <p class="title">$titleText</p>
                    <p class="desc">$descText</p>
                    <button class="retry-btn" onclick="window.AndroidBridge.retryLoad()">RETRY</button>
                </div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // ===== Hardware Back Button =====
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ===== WebView Lifecycle =====
    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // ===== JavaScript Interface =====
    inner class WebViewBridge {

        @JavascriptInterface
        fun goBack() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun onPaymentComplete(coins: String) {
            runOnUiThread {
                Toast.makeText(this@WebviewActivity,
                    "Coins added: $coins", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun getAuthToken(): String {
            return intent.getStringExtra("token") ?: ""
        }

        // ===== Retry button ke liye — page reload =====
        @JavascriptInterface
        fun retryLoad() {
            runOnUiThread {
                if (lastLoadedUrl.isNotEmpty()) {
                    webView.loadUrl(lastLoadedUrl)
                }
            }
        }
    }
}