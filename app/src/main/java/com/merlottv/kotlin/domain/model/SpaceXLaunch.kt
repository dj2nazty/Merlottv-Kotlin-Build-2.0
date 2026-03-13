package com.merlottv.kotlin.domain.model

data class SpaceXLaunch(
    val id: String,
    val name: String,
    val netUtc: String,
    val netEpochMs: Long,
    val windowStart: String?,
    val windowEnd: String?,
    val status: LaunchStatus,
    val missionName: String?,
    val missionType: String?,
    val missionDescription: String?,
    val rocketName: String,
    val padName: String?,
    val padLocation: String?,
    val imageUrl: String?,
    val webcastLive: Boolean,
    val videoUrls: List<String>,
    val orbit: String?
)

enum class LaunchStatus(val display: String, val isTerminal: Boolean) {
    Go("GO", false),
    TBD("TBD", false),
    TBC("TBC", false),
    Success("Success", true),
    Failure("Failure", true),
    InFlight("In Flight", false),
    Hold("Hold", false),
    PartialFailure("Partial Failure", true),
    Unknown("Unknown", false);

    companion object {
        fun fromId(id: Int): LaunchStatus = when (id) {
            1 -> Go
            2 -> TBD
            3 -> Success
            4 -> Failure
            5 -> Hold
            6 -> InFlight
            7 -> PartialFailure
            8 -> TBC
            else -> Unknown
        }
    }
}
