package com.primez.oneminutemind.online

import com.primez.oneminutemind.BuildConfig
import com.primez.oneminutemind.data.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The 28 states and 8 union territories. Must match the list in supabase/setup.sql. */
object IndianStates {
    val all = listOf(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat",
        "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
        "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan",
        "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
        "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
        "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry",
    )
}

data class BoardRow(
    val rank: Int, val name: String, val state: String, val score: Long, val isMe: Boolean, val isPremium: Boolean,
)

enum class Period(val api: String, val label: String) { TODAY("today", "Today"), ALL("all", "All time") }

/** A problem the player should see, in plain words. */
class ScoreboardException(message: String) : Exception(message)

/** Talks to the Supabase database functions in supabase/setup.sql. */
object Scoreboard {
    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** Names allowed by the server: 3–16 letters, numbers, spaces, _ . - */
    fun nameProblem(name: String): String? {
        val n = name.trim()
        return when {
            n.length < 3 -> "Use at least 3 characters"
            n.length > 16 -> "Use 16 characters or fewer"
            !Regex("^[A-Za-z0-9_. -]+$").matches(n) -> "Use only letters, numbers, spaces, _ . -"
            else -> null
        }
    }

    suspend fun register(name: String, state: String): Player {
        val o = JSONObject(rpc("register_player", JSONObject().put("p_name", name.trim()).put("p_state", state)))
        return Player(o.getString("id"), o.getString("secret"), o.getString("name"), o.getString("state"))
    }

    suspend fun update(p: Player, name: String, state: String) {
        rpc("update_player", JSONObject().put("p_id", p.id).put("p_secret", p.secret)
            .put("p_name", name.trim()).put("p_state", state))
    }

    suspend fun submit(p: Player, day: Int, score: Int) {
        rpc("submit_daily", JSONObject().put("p_id", p.id).put("p_secret", p.secret)
            .put("p_day", day).put("p_score", score))
    }

    /** Shows the 👑 crown next to the player's name on the scoreboard. */
    suspend fun setPremium(p: Player, on: Boolean) {
        rpc("set_premium", JSONObject().put("p_id", p.id).put("p_secret", p.secret).put("p_on", on))
    }

    suspend fun leaderboard(period: Period, state: String?, day: Int, playerId: String?): List<BoardRow> {
        val body = JSONObject().put("p_period", period.api).put("p_day", day)
            .put("p_state", state ?: JSONObject.NULL).put("p_player", playerId ?: JSONObject.NULL)
        val arr = JSONArray(rpc("leaderboard", body))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            BoardRow(
                rank = o.optInt("rank"),
                name = o.optString("player_name"),
                state = o.optString("player_state"),
                score = o.optLong("score"),
                isMe = o.optBoolean("is_me", false),
                isPremium = o.optBoolean("is_premium", false),
            )
        }
    }

    private suspend fun rpc(fn: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        if (!configured) throw ScoreboardException("The scoreboard isn't switched on yet.")
        val conn = try {
            (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            throw ScoreboardException("No internet connection.")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ScoreboardException(friendly(text))
            text
        } catch (e: ScoreboardException) {
            throw e
        } catch (e: IOException) {
            throw ScoreboardException("No internet connection. Try again.")
        } finally {
            conn.disconnect()
        }
    }

    private fun friendly(errorBody: String): String {
        val msg = try { JSONObject(errorBody).optString("message") } catch (_: Exception) { "" }
        return when (msg) {
            "name_taken" -> "That username is taken. Try another one."
            "bad_name" -> "Use 3–16 letters, numbers, spaces, _ . -"
            "bad_state" -> "Please pick your state."
            "unknown_player" -> "Your scoreboard profile wasn't found."
            "bad_day", "bad_score" -> "That score couldn't be saved."
            else -> "Something went wrong. Try again."
        }
    }
}
