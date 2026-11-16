package com.ominix.vidiio.data.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A league the Live TV page lists, with its ESPN scoreboard path and the PenguPlay catalogue to search. */
enum class League(val label: String, val espnPath: String, val penguCatalog: String) {
    NFL("NFL", "football/nfl", "pp-live-american_football"),
    COLLEGE_FOOTBALL("College Football", "football/college-football", "pp-live-american_football"),
    NBA("NBA", "basketball/nba", "pp-live-basketball"),
    WNBA("WNBA", "basketball/wnba", "pp-live-basketball"),
    COLLEGE_BASKETBALL("College Basketball", "basketball/mens-college-basketball", "pp-live-basketball"),
    NHL("NHL", "hockey/nhl", "pp-live-hockey"),
    MLB("MLB", "baseball/mlb", "pp-live-baseball"),
    MLS("MLS", "soccer/usa.1", "pp-live-football"),
    PREMIER_LEAGUE("Premier League", "soccer/eng.1", "pp-live-football"),
    CHAMPIONS_LEAGUE("Champions League", "soccer/uefa.champions", "pp-live-football"),
    LA_LIGA("La Liga", "soccer/esp.1", "pp-live-football"),
    SERIE_A("Serie A", "soccer/ita.1", "pp-live-football"),
    UFC_MMA("UFC & MMA", "mma/ufc", "pp-live-mma"),
    F1_RACING("Formula 1", "racing/f1", "pp-live-motorsport"),
}

enum class GameState { LIVE, UPCOMING, FINAL }

data class Team(val name: String, val shortName: String, val logo: String?, val score: String?)

/** One game from a league's scoreboard. */
data class Game(
    val id: String,
    val league: League,
    val home: Team,
    val away: Team,
    val state: GameState,
    /** "8:12 - 4th", "Final", "11/15 - 1:00 PM EST". */
    val detail: String,
    /** Networks or services airing it, e.g. ["ESPN"]. */
    val networks: List<String>,
    val startIso: String,
)

/**
 * Today's games per league, from ESPN's public scoreboard feed: what is live, what's next, what
 * finished, and which network airs each. It lists games only; it carries no streams.
 */
class SportsScores(private val client: OkHttpClient) {

    /** Every league with its games, live first. A league whose feed fails comes back empty. */
    suspend fun today(): List<Pair<League, List<Game>>> = coroutineScope {
        League.entries.map { league ->
            async { league to runCatching { scoreboard(league) }.getOrDefault(emptyList()) }
        }.awaitAll()
    }

    private suspend fun scoreboard(league: League): List<Game> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/scoreboard")
            .build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Scoreboard ${league.label} HTTP ${response.code}")
            JSONObject(response.body.string())
        }
        val events = json.optJSONArray("events") ?: return@withContext emptyList()
        (0 until events.length()).mapNotNull { parse(events.getJSONObject(it), league) }
            .sortedWith(compareBy({ it.state.ordinal }, { it.startIso }))
    }

    private fun parse(event: JSONObject, league: League): Game? = runCatching {
        val competition = event.getJSONArray("competitions").getJSONObject(0)
        val competitors = competition.getJSONArray("competitors")
        val teams = (0 until competitors.length()).map { competitors.getJSONObject(it) }
        fun team(side: String): Team {
            val c = teams.firstOrNull { it.optString("homeAway") == side } ?: teams.first()
            val t = c.getJSONObject("team")
            return Team(
                name = t.optString("displayName"),
                shortName = t.optString("abbreviation").ifBlank { t.optString("shortDisplayName") },
                logo = t.optString("logo").takeIf { it.isNotBlank() },
                score = c.optString("score").takeIf { it.isNotBlank() },
            )
        }
        val status = event.getJSONObject("status").getJSONObject("type")
        val state = when (status.optString("state")) {
            "in" -> GameState.LIVE
            "post" -> GameState.FINAL
            else -> GameState.UPCOMING
        }
        val broadcasts = competition.optJSONArray("broadcasts")
        val networks = (0 until (broadcasts?.length() ?: 0)).flatMap { i ->
            val names = broadcasts!!.getJSONObject(i).optJSONArray("names")
            (0 until (names?.length() ?: 0)).map { names!!.getString(it) }
        }.distinct()
        Game(
            id = event.getString("id"),
            league = league,
            home = team("home"),
            away = team("away"),
            state = state,
            detail = status.optString("shortDetail").ifBlank { status.optString("description") },
            networks = networks,
            startIso = event.optString("date"),
        )
    }.getOrNull()
}
