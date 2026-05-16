package com.edm.fire

data class LeaderboardModel(
    val userId: String = "",
    val matchPlayed: Int = 0,
    val winningCoins: Int = 0,
    val winningCount: Int = 0,
    var userName: String = "",
    var rank: Int = 0,
    val inGameName: String = "",
    val inGameLevel: Int = 0,
    val inGameUID: Long = 0L
)