package com.edm.fire

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NotificationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: MaterialToolbar
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var selectionBar: View
    private lateinit var cbSelectAll: CheckBox
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var btnCancelSelect: MaterialButton

    private val notifications = mutableListOf<NotificationModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notification)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            Toast.makeText(this, "Please login to view notifications", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        toolbar = findViewById(R.id.materialToolbar)
        progressBar = findViewById(R.id.progressBar_Announce)
        selectionBar = findViewById(R.id.selectionBar)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        btnCancelSelect = findViewById(R.id.btnCancelSelect)

        progressBar.visibility = View.VISIBLE

        toolbar.setNavigationOnClickListener {
            if (adapter.isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select -> {
                    enterSelectionMode()
                    true
                }
                R.id.action_clear_all -> {
                    showClearAllConfirmation()
                    true
                }
                else -> false
            }
        }

        recyclerView = findViewById(R.id.recyclerAnnouncements)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NotificationAdapter(
            list = notifications,
            onItemClick = { notification -> markAsRead(notification) },
            onSelectionChanged = { count -> updateSelectionCount(count) }
        )
        recyclerView.adapter = adapter

        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                adapter.selectAll()
            } else {
                adapter.deselectAll()
            }
        }

        btnDeleteSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "Koi bhi select nahi hai", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDeleteConfirmation(selectedIds)
        }

        btnCancelSelect.setOnClickListener { exitSelectionMode() }

        initializeRemoteConfig()
    }

    private fun enterSelectionMode() {
        adapter.enterSelectionMode()
        selectionBar.visibility = View.VISIBLE
        tvSelectedCount.text = "0 selected"
        cbSelectAll.isChecked = false
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        selectionBar.visibility = View.GONE
        cbSelectAll.isChecked = false
    }

    private fun updateSelectionCount(count: Int) {
        tvSelectedCount.text = "$count selected"
        cbSelectAll.isChecked = notifications.isNotEmpty() && count == notifications.size
    }

    private fun showDeleteConfirmation(selectedIds: Set<String>) {
        AlertDialog.Builder(this)
            .setTitle("Delete Notifications")
            .setMessage("${selectedIds.size} notification(s) delete karna chahte ho?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedNotifications(selectedIds)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearAllConfirmation() {
        if (notifications.isEmpty()) {
            Toast.makeText(this, "Koi notification nahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Clear All")
            .setMessage("Saari notifications delete karna chahte ho?")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllNotifications()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedNotifications(selectedIds: Set<String>) {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        val unreadDeletedCount = notifications.count { notification ->
            val notificationId = notification.id
            notificationId != null && selectedIds.contains(notificationId) && !notification.read
        }

        val batch = db.batch()
        selectedIds.forEach { notifId ->
            val ref = db.collection("Users")
                .document(userId)
                .collection("Notifications")
                .document(notifId)
            batch.delete(ref)
        }

        if (unreadDeletedCount > 0) {
            val userRef = db.collection("Users").document(userId)
            batch.set(
                userRef,
                mapOf("unreadNotificationCount" to FieldValue.increment(-unreadDeletedCount.toLong())),
                SetOptions.merge()
            )
        }

        batch.commit()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                exitSelectionMode()
                Toast.makeText(
                    this,
                    "${selectedIds.size} notification(s) delete ho gayi",
                    Toast.LENGTH_SHORT
                ).show()
                notifications.removeAll { it.id != null && selectedIds.contains(it.id) }
                adapter.notifyDataSetChanged()
                checkEmptyState()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Delete fail: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearAllNotifications() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        val batch = db.batch()
        notifications.forEach { notif ->
            notif.id?.let { notificationId ->
                val ref = db.collection("Users")
                    .document(userId)
                    .collection("Notifications")
                    .document(notificationId)
                batch.delete(ref)
            }
        }

        val userRef = db.collection("Users").document(userId)
        batch.set(userRef, mapOf("unreadNotificationCount" to 0), SetOptions.merge())

        batch.commit()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                notifications.clear()
                adapter.notifyDataSetChanged()
                exitSelectionMode()
                checkEmptyState()
                Toast.makeText(this, "Saari notifications clear ho gayi", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Clear fail: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkEmptyState() {
        val emptyState = findViewById<View>(R.id.emptyState)
        if (notifications.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun initializeRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    loadNotificationModels()
                } else {
                    showError("Failed to load notifications. Please try again.")
                }
            }
    }

    private fun loadNotificationModels() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            showError("User not found")
            return
        }

        db.collection("Users")
            .document(userId)
            .collection("Notifications")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    showError("Failed to load notifications: ${error.message}")
                    return@addSnapshotListener
                }

                notifications.clear()
                snapshot?.documents?.forEach { document ->
                    try {
                        val notification = document.toObject(NotificationModel::class.java)
                        notification?.let {
                            it.id = document.id
                            notifications.add(it)
                        }
                    } catch (_: Exception) {
                    }
                }

                sortNotificationsForUi()
                adapter.notifyDataSetChanged()
                checkEmptyState()
            }
    }

    private fun markAsRead(notification: NotificationModel) {
        val userId = auth.currentUser?.uid ?: return
        val notificationId = notification.id ?: return

        if (!notification.read) {
            val userRef = db.collection("Users").document(userId)
            val notificationRef = userRef.collection("Notifications").document(notificationId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(notificationRef)
                val isAlreadyRead = snapshot.getBoolean("read") ?: false
                if (!isAlreadyRead) {
                    transaction.update(notificationRef, "read", true)
                    transaction.update(userRef, "unreadNotificationCount", FieldValue.increment(-1))
                }
                isAlreadyRead
            }.addOnSuccessListener {
                val index = notifications.indexOfFirst { it.id == notificationId }
                if (index != -1) {
                    notifications[index].read = true
                    sortNotificationsForUi()
                    adapter.notifyDataSetChanged()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to mark as read", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        notifications.clear()
        notifications.add(
            NotificationModel(
                type = "error",
                title = "Error",
                message = message,
                timestamp = "",
                read = true
            )
        )
        adapter.notifyDataSetChanged()
        checkEmptyState()
    }

    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    // yaha IST aur old UTC dono timestamp safely parse ho rahe hain
    private fun parseNotificationTime(timestamp: String?): Long? {
        if (timestamp.isNullOrBlank()) return null

        val formats = listOf(
            SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale("en", "IN")).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        )

        formats.forEach { format ->
            try {
                val parsedDate = format.parse(timestamp)
                if (parsedDate != null) {
                    return parsedDate.time
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    // yaha unread sabse upar aur har group me newest se oldest sort ho raha hai
    private fun sortNotificationsForUi() {
        notifications.sortWith(
            compareBy<NotificationModel> { it.read }
                .thenByDescending { parseNotificationTime(it.timestamp) ?: Long.MIN_VALUE }
        )
    }
}