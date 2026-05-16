package com.edm.fire


import com.google.firebase.firestore.PropertyName

data class NotificationModel(
    var id: String? = null,

    @PropertyName("title")
    val title: String? = null,

    @PropertyName("message")
    val message: String? = null,

    @PropertyName("timestamp")
    val timestamp: String? = null,

    @PropertyName("type")
    val type: String? = null,

    @PropertyName("read")
    var read: Boolean = false,

    // Tournament related fields hai
    @PropertyName("tournamentType")
    val tournamentType: String? = null,

    @PropertyName("tournamentId")
    val tournamentId: String? = null,

    @PropertyName("slotNumber")
    val slotNumber: String? = null,

    @PropertyName("joiningFee")
    val joiningFee: String? = null,

    @PropertyName("referralBonusUsed")
    val referralBonusUsed: String? = null,

    @PropertyName("finalCost")
    val finalCost: String? = null,

    @PropertyName("transactionId")
    val transactionId: String? = null,

    @PropertyName("totalPlayed")
    val totalPlayed: String? = null,

    // Profile verification fields
    @PropertyName("userName")
    val userName: String? = null,

    @PropertyName("level")
    val level: Int? = null,

    // Tournament winnings fields
    @PropertyName("winnings")
    val winnings: String? = null,

    // Technical fields
    @PropertyName("fcmMessageId")
    val fcmMessageId: String? = null,

    @PropertyName("sentVia")
    val sentVia: String? = null
)
