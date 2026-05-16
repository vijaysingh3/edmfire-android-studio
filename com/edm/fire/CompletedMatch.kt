package com.edm.fire

data class CompletedMatch(
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
    val perKill: Int = 0,
    val slotNumbers: Int = 0,
    val joinedCount: Int = 0,
    val userSlotNumber: Int,
    val joinTime: Long,
    val status: String,
    val videoUrl: String = ""
)