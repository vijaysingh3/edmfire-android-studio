package com.edm.fire

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

class SSEClient {

    companion object {
        private const val TAG = "SSEClient"
    }

    private var eventSource: EventSource? = null
    private var factory: EventSource.Factory? = null
    private var client: OkHttpClient? = null
    private var eventListener: EventListener? = null
    private var isConnected = false

    interface EventListener {
        fun onDataReceived(data: String)
        fun onConnectionError(error: String)
        fun onConnectionClosed()
    }

    fun connect(url: String, listener: EventListener) {
        disconnect()

        this.eventListener = listener

        client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(24, java.util.concurrent.TimeUnit.HOURS)
            .build()

        factory = EventSources.createFactory(client!!)

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .build()

        eventSource = factory?.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                isConnected = true
                Log.d(TAG, "SSE connected successfully")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isNotEmpty()) {
                    Log.d(TAG, "SSE Event received: $data")
                    Handler(Looper.getMainLooper()).post {
                        listener.onDataReceived(data)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                isConnected = false
                Log.d(TAG, "SSE connection closed")
                Handler(Looper.getMainLooper()).post {
                    listener.onConnectionClosed()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                isConnected = false
                Log.e(TAG, "SSE connection failed: ${t?.message}")
                Handler(Looper.getMainLooper()).post {
                    listener.onConnectionError(t?.message ?: "Connection failed")
                }
            }
        })
    }

    fun disconnect() {
        isConnected = false
        eventSource?.cancel()
        eventSource = null
        client?.dispatcher?.cancelAll()
        client = null
        factory = null
        Log.d(TAG, "SSE disconnected")
    }

    fun isConnected(): Boolean = isConnected
}