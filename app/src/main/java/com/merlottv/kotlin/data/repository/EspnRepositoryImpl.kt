package com.merlottv.kotlin.data.repository

import com.merlottv.kotlin.domain.model.*
import com.merlottv.kotlin.domain.repository.EspnRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EspnRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : EspnRepository {

    private val boundedIo = Dispatchers.IO.limitedParallelism(4)

    // Fast client for ESPN API calls
    private val espnClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // In-memory cache with 5-minute TTL
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val CACHE_TTL = 5 * 60 * 1000L

    private data class CacheEntry(val data: Any, val timestamp: Long)

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? {
        val entry = cache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL) {
            entry.data as? T
        } else {
            cache.remove(key)
            null
        }
    }

    private fun putCache(key: String, data: Any) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    private companion object {
        const val SITE_BASE = "https://site.api.espn.com/apis/site/v2/sports"
    }

    private fun leagueBase(league: SportsLeague) =
        "$SITE_BASE/${league.sport}/${league.league}"

    // ─── Scoreboard / Live Scores ─────────────────────────────────────

    override suspend fun getScoreboard(league: SportsLeague): List<SportScore> {
        val cacheKey = "scoreboard_${league.name}"
        getCached<List<SportScore>>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                val json = fetchJson("${leagueBase(league)}/scoreboard")
                    ?: return@withContext emptyList()
                val events = (json["events"] as? List<*>) ?: emptyList<Any>()
                val scores = events.mapNotNull { parseEvent(it, league) }
                putCache(cacheKey, scores)
                scores
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEvent(raw: Any?, league: SportsLeague): SportScore? {
        val event = raw as? Map<String, Any?> ?: return null
        val competitions = (event["competitions"] as? List<Map<String, Any?>>)
            ?.firstOrNull() ?: return null

        val competitors = (competitions["competitors"] as? List<Map<String, Any?>>)
            ?: return null

        val homeComp = competitors.find { (it["homeAway"] as? String) == "home" }
        val awayComp = competitors.find { (it["homeAway"] as? String) == "away" }

        // Some leagues (UFC) may not have home/away — use index
        val comp1 = homeComp ?: competitors.getOrNull(0)
        val comp2 = awayComp ?: competitors.getOrNull(1)

        val status = competitions["status"] as? Map<String, Any?>
        val statusType = status?.get("type") as? Map<String, Any?>
        val state = statusType?.get("state") as? String ?: "pre"

        // Broadcast info
        val broadcasts = (competitions["broadcasts"] as? List<Map<String, Any?>>)
            ?.flatMap { b ->
                (b["names"] as? List<String>) ?: emptyList()
            } ?: emptyList()

        // Venue
        val venue = competitions["venue"] as? Map<String, Any?>
        val address = venue?.get("address") as? Map<String, Any?>

        return SportScore(
            eventId = event["id"] as? String ?: "",
            league = league,
            name = event["name"] as? String ?: "",
            shortName = event["shortName"] as? String ?: "",
            homeTeam = parseTeamRef(comp1),
            awayTeam = parseTeamRef(comp2),
            homeScore = comp1?.get("score") as? String ?: "0",
            awayScore = comp2?.get("score") as? String ?: "0",
            statusText = statusType?.get("shortDetail") as? String
                ?: statusType?.get("detail") as? String ?: "",
            statusState = state,
            clock = status?.get("displayClock") as? String ?: "",
            period = (status?.get("period") as? Number)?.toInt() ?: 0,
            isLive = state == "in",
            isComplete = statusType?.get("completed") as? Boolean ?: false,
            gameDate = event["date"] as? String ?: "",
            broadcasts = broadcasts,
            venue = venue?.get("fullName") as? String ?: "",
            venueCity = listOfNotNull(
                address?.get("city") as? String,
                address?.get("state") as? String
            ).joinToString(", ")
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTeamRef(comp: Map<String, Any?>?): SportTeamRef {
        if (comp == null) return SportTeamRef()
        val team = comp["team"] as? Map<String, Any?> ?: return SportTeamRef()
        val records = (comp["records"] as? List<Map<String, Any?>>)
            ?.firstOrNull()
        return SportTeamRef(
            id = team["id"] as? String ?: "",
            name = team["name"] as? String ?: "",
            abbreviation = team["abbreviation"] as? String ?: "",
            displayName = team["displayName"] as? String ?: team["name"] as? String ?: "",
            logo = team["logo"] as? String ?: "",
            color = team["color"] as? String ?: "",
            record = records?.get("summary") as? String ?: "",
            isWinner = comp["winner"] as? Boolean ?: false
        )
    }

    // ─── Standings ────────────────────────────────────────────────────

    override suspend fun getStandings(league: SportsLeague): List<SportConference> {
        if (!league.hasStandings) return emptyList()
        val cacheKey = "standings_${league.name}"
        getCached<List<SportConference>>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                // Standings uses /apis/v2/ instead of /apis/site/v2/
                val standingsUrl = "https://site.api.espn.com/apis/v2/sports/${league.sport}/${league.league}/standings"
                val json = fetchJson(standingsUrl)
                    ?: return@withContext emptyList()
                val conferences = parseStandings(json)
                putCache(cacheKey, conferences)
                conferences
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStandings(json: Map<String, Any?>): List<SportConference> {
        // ESPN standings can be nested: children[] → children[] → standings.entries[]
        val children = (json["children"] as? List<Map<String, Any?>>)
            ?: return emptyList()

        return children.mapNotNull { conf ->
            val confName = conf["name"] as? String ?: conf["abbreviation"] as? String ?: return@mapNotNull null
            val confAbbrev = conf["abbreviation"] as? String ?: confName

            // Check if this conference has sub-divisions
            val subChildren = conf["children"] as? List<Map<String, Any?>>

            val divisions = if (subChildren != null && subChildren.isNotEmpty()) {
                subChildren.mapNotNull { div ->
                    val divName = div["name"] as? String ?: return@mapNotNull null
                    val standings = div["standings"] as? Map<String, Any?>
                    val entries = (standings?.get("entries") as? List<Map<String, Any?>>)
                        ?.map { parseStandingsEntry(it) } ?: emptyList()
                    SportDivision(name = divName, entries = entries)
                }
            } else {
                // Flat standings (no divisions)
                val standings = conf["standings"] as? Map<String, Any?>
                val entries = (standings?.get("entries") as? List<Map<String, Any?>>)
                    ?.map { parseStandingsEntry(it) } ?: emptyList()
                if (entries.isNotEmpty()) {
                    listOf(SportDivision(name = confName, entries = entries))
                } else emptyList()
            }

            SportConference(
                id = conf["id"] as? String ?: "",
                name = confName,
                abbreviation = confAbbrev,
                divisions = divisions
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStandingsEntry(entry: Map<String, Any?>): SportStandingsEntry {
        val team = entry["team"] as? Map<String, Any?>
        val stats = (entry["stats"] as? List<Map<String, Any?>>)
            ?.associate { s ->
                val name = s["name"] as? String ?: ""
                val displayValue = s["displayValue"] as? String ?: s["value"]?.toString() ?: "0"
                name to displayValue
            } ?: emptyMap()

        return SportStandingsEntry(
            team = SportTeamRef(
                id = team?.get("id") as? String ?: "",
                name = team?.get("name") as? String ?: "",
                abbreviation = team?.get("abbreviation") as? String ?: "",
                displayName = team?.get("displayName") as? String ?: "",
                logo = (team?.get("logos") as? List<Map<String, Any?>>)
                    ?.firstOrNull()?.get("href") as? String ?: "",
                color = ""
            ),
            wins = stats["wins"]?.toIntOrNull() ?: 0,
            losses = stats["losses"]?.toIntOrNull() ?: 0,
            ties = stats["ties"]?.toIntOrNull() ?: 0,
            winPct = stats["winPercent"] ?: stats["leagueWinPercent"] ?: ".000",
            gamesBack = stats["gamesBehind"] ?: stats["gamesBack"] ?: "-",
            streak = stats["streak"] ?: "",
            pointsFor = stats["pointsFor"]?.toDoubleOrNull()?.toInt() ?: 0,
            pointsAgainst = stats["pointsAgainst"]?.toDoubleOrNull()?.toInt() ?: 0,
            stats = stats
        )
    }

    private fun String.toIntOrNull(): Int? = try { this.toDouble().toInt() } catch (_: Exception) { null }
    private fun String.toDoubleOrNull(): Double? = try { this.toDouble() } catch (_: Exception) { null }

    // ─── Teams ────────────────────────────────────────────────────────

    override suspend fun getTeams(league: SportsLeague): List<SportTeamInfo> {
        val cacheKey = "teams_${league.name}"
        getCached<List<SportTeamInfo>>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                val json = fetchJson("${leagueBase(league)}/teams")
                    ?: return@withContext emptyList()
                val teams = parseTeams(json)
                putCache(cacheKey, teams)
                teams
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTeams(json: Map<String, Any?>): List<SportTeamInfo> {
        // ESPN returns sports[0].leagues[0].teams[{team:{...}}]
        val sports = (json["sports"] as? List<Map<String, Any?>>)
            ?.firstOrNull() ?: return emptyList()
        val leagues = (sports["leagues"] as? List<Map<String, Any?>>)
            ?.firstOrNull() ?: return emptyList()
        val teamsWrapper = (leagues["teams"] as? List<Map<String, Any?>>)
            ?: return emptyList()

        return teamsWrapper.mapNotNull { wrapper ->
            val team = wrapper["team"] as? Map<String, Any?> ?: return@mapNotNull null
            val logos = (team["logos"] as? List<Map<String, Any?>>)
                ?.firstOrNull()
            val venue = team["venue"] as? Map<String, Any?>
            val record = (team["record"] as? Map<String, Any?>)
                ?.let { rec ->
                    val items = rec["items"] as? List<Map<String, Any?>>
                    items?.firstOrNull()?.get("summary") as? String
                }

            SportTeamInfo(
                id = team["id"] as? String ?: "",
                name = team["name"] as? String ?: "",
                location = team["location"] as? String ?: "",
                abbreviation = team["abbreviation"] as? String ?: "",
                displayName = team["displayName"] as? String ?: "",
                logo = logos?.get("href") as? String ?: "",
                color = team["color"] as? String ?: "",
                record = record ?: "",
                standingSummary = team["standingSummary"] as? String ?: "",
                venue = SportVenue(
                    name = venue?.get("fullName") as? String ?: "",
                    city = (venue?.get("address") as? Map<String, Any?>)?.get("city") as? String ?: "",
                    state = (venue?.get("address") as? Map<String, Any?>)?.get("state") as? String ?: "",
                    capacity = (venue?.get("capacity") as? Number)?.toInt() ?: 0
                )
            )
        }
    }

    // ─── Team Roster ──────────────────────────────────────────────────

    override suspend fun getTeamRoster(league: SportsLeague, teamId: String): List<SportPlayer> {
        val cacheKey = "roster_${league.name}_$teamId"
        getCached<List<SportPlayer>>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                val json = fetchJson("${leagueBase(league)}/teams/$teamId/roster")
                    ?: return@withContext emptyList()
                val players = parseRoster(json)
                putCache(cacheKey, players)
                players
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRoster(json: Map<String, Any?>): List<SportPlayer> {
        // ESPN roster: athletes[] or groups of athletes
        val athletes = mutableListOf<SportPlayer>()

        // Try flat athletes list first
        val flatAthletes = json["athletes"] as? List<Map<String, Any?>>
        if (flatAthletes != null) {
            for (group in flatAthletes) {
                // Each group might have "items" list
                val items = group["items"] as? List<Map<String, Any?>>
                if (items != null) {
                    athletes.addAll(items.mapNotNull { parsePlayer(it) })
                } else {
                    // It might be a direct athlete object
                    parsePlayer(group)?.let { athletes.add(it) }
                }
            }
        }

        return athletes
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePlayer(athlete: Map<String, Any?>): SportPlayer? {
        val id = athlete["id"] as? String ?: return null
        val headshot = (athlete["headshot"] as? Map<String, Any?>)?.get("href") as? String
            ?: athlete["headshot"] as? String ?: ""
        val position = (athlete["position"] as? Map<String, Any?>)?.get("abbreviation") as? String
            ?: athlete["position"] as? String ?: ""

        return SportPlayer(
            id = id,
            name = athlete["displayName"] as? String ?: athlete["fullName"] as? String ?: "",
            jersey = athlete["jersey"] as? String ?: "",
            position = position,
            age = (athlete["age"] as? Number)?.toInt() ?: 0,
            height = athlete["displayHeight"] as? String ?: "",
            weight = athlete["displayWeight"] as? String ?: "",
            headshot = headshot,
            experience = (athlete["experience"] as? Map<String, Any?>)
                ?.get("years") as? Int ?: 0
        )
    }

    // ─── Team Schedule ────────────────────────────────────────────────

    override suspend fun getTeamSchedule(league: SportsLeague, teamId: String): List<SportScheduleEvent> {
        val cacheKey = "schedule_${league.name}_$teamId"
        getCached<List<SportScheduleEvent>>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                val json = fetchJson("${leagueBase(league)}/teams/$teamId/schedule")
                    ?: return@withContext emptyList()
                val events = parseSchedule(json)
                putCache(cacheKey, events)
                events
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSchedule(json: Map<String, Any?>): List<SportScheduleEvent> {
        val events = (json["events"] as? List<Map<String, Any?>>)
            ?: return emptyList()

        return events.mapNotNull { event ->
            val competitions = (event["competitions"] as? List<Map<String, Any?>>)
                ?.firstOrNull() ?: return@mapNotNull null
            val competitors = (competitions["competitors"] as? List<Map<String, Any?>>)
                ?: return@mapNotNull null
            val statusType = (competitions["status"] as? Map<String, Any?>)
                ?.get("type") as? Map<String, Any?>
            val isComplete = statusType?.get("completed") as? Boolean ?: false

            // Find the opponent (the team that isn't "us")
            val opponent = competitors.getOrNull(1) ?: competitors.getOrNull(0)
            val homeAway = competitors.getOrNull(0)?.get("homeAway") as? String ?: ""

            val broadcasts = (competitions["broadcasts"] as? List<Map<String, Any?>>)
                ?.flatMap { b -> (b["names"] as? List<String>) ?: emptyList() }
                ?: emptyList()

            val score = if (isComplete) {
                val c0Score = competitors.getOrNull(0)?.get("score") as? Map<String, Any?>
                val c1Score = competitors.getOrNull(1)?.get("score") as? Map<String, Any?>
                val s0 = c0Score?.get("displayValue") as? String
                    ?: competitors.getOrNull(0)?.get("score") as? String ?: ""
                val s1 = c1Score?.get("displayValue") as? String
                    ?: competitors.getOrNull(1)?.get("score") as? String ?: ""
                if (s0.isNotEmpty() && s1.isNotEmpty()) {
                    val winner = competitors.getOrNull(0)?.get("winner") as? Boolean ?: false
                    "${if (winner) "W" else "L"} $s0-$s1"
                } else ""
            } else ""

            SportScheduleEvent(
                eventId = event["id"] as? String ?: "",
                date = event["date"] as? String ?: "",
                opponent = parseTeamRef(opponent),
                homeAway = homeAway,
                score = score,
                isComplete = isComplete,
                broadcasts = broadcasts,
                venue = (competitions["venue"] as? Map<String, Any?>)?.get("fullName") as? String ?: ""
            )
        }
    }

    // ─── Game Summary ─────────────────────────────────────────────────

    override suspend fun getGameSummary(league: SportsLeague, eventId: String): SportGameSummary? {
        val cacheKey = "summary_${league.name}_$eventId"
        getCached<SportGameSummary>(cacheKey)?.let { return it }

        return withContext(boundedIo) {
            try {
                val json = fetchJson("${leagueBase(league)}/summary?event=$eventId")
                    ?: return@withContext null
                val summary = parseSummary(json, eventId)
                if (summary != null) putCache(cacheKey, summary)
                summary
            } catch (_: Exception) {
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSummary(json: Map<String, Any?>, eventId: String): SportGameSummary? {
        // Header info
        val header = json["header"] as? Map<String, Any?>
        val competitions = (header?.get("competitions") as? List<Map<String, Any?>>)
            ?.firstOrNull()
        val competitors = (competitions?.get("competitors") as? List<Map<String, Any?>>)

        val homeComp = competitors?.find { (it["homeAway"] as? String) == "home" }
        val awayComp = competitors?.find { (it["homeAway"] as? String) == "away" }

        val homeTeam = parseTeamRefFromHeader(homeComp)
        val awayTeam = parseTeamRefFromHeader(awayComp)

        // Box score
        val boxScore = parseBoxScore(json)

        // Plays
        val plays = parsePlays(json)

        // Team stats comparison
        val teamStats = parseTeamStats(json)

        val statusType = (competitions?.get("status") as? Map<String, Any?>)
            ?.get("type") as? Map<String, Any?>

        return SportGameSummary(
            eventId = eventId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = homeComp?.get("score") as? String ?: "0",
            awayScore = awayComp?.get("score") as? String ?: "0",
            statusText = statusType?.get("shortDetail") as? String ?: "",
            venue = (json["gameInfo"] as? Map<String, Any?>)
                ?.let { (it["venue"] as? Map<String, Any?>)?.get("fullName") as? String } ?: "",
            boxScore = boxScore,
            plays = plays,
            teamStats = teamStats
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTeamRefFromHeader(comp: Map<String, Any?>?): SportTeamRef {
        if (comp == null) return SportTeamRef()
        val team = comp["team"] as? Map<String, Any?>
        val record = comp["record"] as? String ?: ""
        return SportTeamRef(
            id = comp["id"] as? String ?: team?.get("id") as? String ?: "",
            name = team?.get("name") as? String ?: "",
            abbreviation = team?.get("abbreviation") as? String ?: "",
            displayName = team?.get("displayName") as? String ?: "",
            logo = team?.get("logo") as? String
                ?: (team?.get("logos") as? List<Map<String, Any?>>)?.firstOrNull()?.get("href") as? String ?: "",
            record = record
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBoxScore(json: Map<String, Any?>): List<SportBoxScoreSection> {
        val boxscore = json["boxscore"] as? Map<String, Any?> ?: return emptyList()
        val players = boxscore["players"] as? List<Map<String, Any?>> ?: return emptyList()

        if (players.size < 2) return emptyList()

        // Get stat categories from the first team's stats
        val team1Stats = (players.getOrNull(0)?.get("statistics") as? List<Map<String, Any?>>)
            ?: return emptyList()
        val team2Stats = (players.getOrNull(1)?.get("statistics") as? List<Map<String, Any?>>)
            ?: return emptyList()

        return team1Stats.mapIndexedNotNull { index, statGroup ->
            val title = statGroup["name"] as? String ?: return@mapIndexedNotNull null
            val labels = (statGroup["labels"] as? List<String>) ?: emptyList()
            val headers = listOf("Player") + labels

            val t1Athletes = (statGroup["athletes"] as? List<Map<String, Any?>>)
            val t2Athletes = (team2Stats.getOrNull(index)?.get("athletes") as? List<Map<String, Any?>>)

            val homeRows = t1Athletes?.map { a ->
                val name = (a["athlete"] as? Map<String, Any?>)?.get("displayName") as? String ?: ""
                val stats = (a["stats"] as? List<String>) ?: emptyList()
                listOf(name) + stats
            } ?: emptyList()

            val awayRows = t2Athletes?.map { a ->
                val name = (a["athlete"] as? Map<String, Any?>)?.get("displayName") as? String ?: ""
                val stats = (a["stats"] as? List<String>) ?: emptyList()
                listOf(name) + stats
            } ?: emptyList()

            SportBoxScoreSection(
                title = title,
                headers = headers,
                homeRows = homeRows,
                awayRows = awayRows
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePlays(json: Map<String, Any?>): List<SportPlay> {
        val drives = json["drives"] as? Map<String, Any?>
        val allDrives = drives?.get("previous") as? List<Map<String, Any?>>

        if (allDrives != null) {
            // Football-style: drives with plays inside
            return allDrives.flatMap { drive ->
                val plays = drive["plays"] as? List<Map<String, Any?>> ?: emptyList()
                plays.mapNotNull { play ->
                    val period = (play["period"] as? Map<String, Any?>)?.get("number") as? Number
                    SportPlay(
                        id = play["id"] as? String ?: "",
                        period = period?.toInt() ?: 0,
                        periodText = (play["period"] as? Map<String, Any?>)?.get("type") as? String ?: "Q${period ?: 0}",
                        clock = (play["clock"] as? Map<String, Any?>)?.get("displayValue") as? String ?: "",
                        description = play["text"] as? String ?: "",
                        isScoring = play["scoringPlay"] as? Boolean ?: false
                    )
                }
            }.takeLast(50) // Limit to last 50 plays for performance
        }

        // Non-football: try plays.allPlays or keyEvents
        val playsSection = json["plays"] as? List<Map<String, Any?>>
        if (playsSection != null) {
            return playsSection.takeLast(50).mapNotNull { play ->
                SportPlay(
                    id = play["id"] as? String ?: "",
                    period = (play["period"] as? Number)?.toInt()
                        ?: (play["period"] as? Map<String, Any?>)?.get("number") as? Int ?: 0,
                    clock = play["clock"] as? String
                        ?: (play["clock"] as? Map<String, Any?>)?.get("displayValue") as? String ?: "",
                    description = play["text"] as? String ?: play["description"] as? String ?: "",
                    isScoring = play["scoringPlay"] as? Boolean ?: false
                )
            }
        }

        // Fallback: keyEvents
        val keyEvents = json["keyEvents"] as? List<Map<String, Any?>>
        return keyEvents?.mapNotNull { play ->
            SportPlay(
                id = play["id"] as? String ?: "",
                description = play["text"] as? String ?: "",
                isScoring = play["scoringPlay"] as? Boolean ?: false
            )
        } ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTeamStats(json: Map<String, Any?>): List<SportStatComparison> {
        val boxscore = json["boxscore"] as? Map<String, Any?> ?: return emptyList()
        val teams = boxscore["teams"] as? List<Map<String, Any?>> ?: return emptyList()

        if (teams.size < 2) return emptyList()

        val team1Stats = (teams[0]["statistics"] as? List<Map<String, Any?>>)
            ?: return emptyList()
        val team2Stats = (teams[1]["statistics"] as? List<Map<String, Any?>>)
            ?: return emptyList()

        return team1Stats.mapIndexedNotNull { index, stat ->
            val label = stat["label"] as? String ?: stat["name"] as? String ?: return@mapIndexedNotNull null
            val homeVal = stat["displayValue"] as? String ?: ""
            val awayVal = team2Stats.getOrNull(index)?.get("displayValue") as? String ?: ""
            SportStatComparison(label = label, homeValue = homeVal, awayValue = awayVal)
        }
    }

    // ─── Network Helper ───────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun fetchJson(url: String): Map<String, Any?>? {
        val request = Request.Builder().url(url).build()
        val response = espnClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val adapter = moshi.adapter(Map::class.java)
        return adapter.fromJson(body) as? Map<String, Any?>
    }
}
