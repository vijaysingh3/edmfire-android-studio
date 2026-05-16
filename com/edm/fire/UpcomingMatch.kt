// Data Class for Upcoming Match
data class UpcomingMatch(
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
    val status: String,
// Purane fields ke saath ye 3 bhi add karo:
    val perKill: Int = 0,
    val slotNumbers: Int = 0,
    val joinedCount: Int = 0,
    val roomId: String = "Coming Soon....",
    val roomPassword: String = "Coming Soon...."
)
