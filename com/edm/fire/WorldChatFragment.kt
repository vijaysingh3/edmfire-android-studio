package com.edm.fire

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class WorldChatFragment : Fragment() {

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val CHAT_URL_KEY = "chat_url"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_world_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.worldChatWebView)
        progressBar = view.findViewById(R.id.worldChatProgressBar)

        setupWebView()
        loadChatUrlFromRemoteConfig()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString().orEmpty()

                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    view?.loadUrl(url)
                    true
                } else {
                    true
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun loadChatUrlFromRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
        )

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (!isAdded) return@addOnCompleteListener

            if (task.isSuccessful) {
                val chatUrl = remoteConfig.getString(CHAT_URL_KEY).trim()

                if (chatUrl.startsWith("http://") || chatUrl.startsWith("https://")) {
                    webView.loadUrl(chatUrl)
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "chat_url valid nahi hai",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Remote config load fail hua",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }

        super.onPause()
    }

    override fun onDestroyView() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }

        super.onDestroyView()
    }
}
