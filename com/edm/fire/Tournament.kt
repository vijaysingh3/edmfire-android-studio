package com.edm.fire

data class Tournament(
    val tournamentId: String = "",
    val Title: String = "",
    val Map: String = "",
    val Mode: String = "",
    val PerKill: Int = 0,
    val PricePool: Int = 0,
    val Type: String = "",
    val DateTime: String = "",
    val Description: String = "",
    val JoiningFee: Int = 0,
    var JoinedPlayersCount: Int = 0,
    val BannerUrl: String = "",
    val Status: String = "",
    val RoomID: String = "",
    val RoomPassword: String = "",
    val SlotNumbers: Int = 0,
    val JoinedPlayers: Map<String, Player> = emptyMap()
)

data class Player(
    val InGameName: String = "****",
    val userId: String = "****",
    val InGameUID: Long = 0,
    val InGameLevel: Int = 0,
    val PositionSeat: Int = 0,
    val Kills: Int = 0,
    val Deaths: Int = 0,
    val Assists: Int = 0,
    val Damage: Int = 0,
    val CoinsEarned: Int = 0,
    val Rank: Int = 0,
    val JoinTime: Long = 0
)