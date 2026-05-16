package com.edm.fire

import android.util.Log
import okhttp3.*
import java.io.IOException

class UniversalReader {

    companion object {
        private const val TAG = "UNIVERSAL_READER"

        private val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        /**
         * Kisi bhi Firebase URL se JSON data read karega
         */
        fun readJson(url: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
            Log.d(TAG, "📡 Reading: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ Network error: ${e.message}")
                    onError("Network error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Success! Response length: ${body.length}")
                        onResult(body)
                    } else {
                        Log.e(TAG, "❌ HTTP ${response.code}: ${response.message}")
                        onError("HTTP ${response.code}: ${response.message}")
                    }
                }
            })
        }
    }
}