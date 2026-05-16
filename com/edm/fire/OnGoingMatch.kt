package com.edm.fire

data class OnGoingMatch(
    val tournamentType: String,
    val tournamentId: String,
    val title: String,
    val dateTime: String,
    val entryFee: Int,
    val pricePool: Int,
    val bannerUrl: String,
    val map: String,
    val mode: String,
    val type: String,
    val userSlotNumber: Int,
    val joinTime: Long,
    val perKill: Int = 0,
    val slotNumbers: Int = 0,
    val joinedCount: Int = 0,
    val status: String,
    val roomId: String = "Coming Soon....",
    val roomPassword: String = "Coming Soon....",
    val videoUrl: String = ""
)