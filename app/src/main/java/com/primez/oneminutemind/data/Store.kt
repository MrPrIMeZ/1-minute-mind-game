package com.primez.oneminutemind.data

import android.content.Context
import com.primez.oneminutemind.game.GameId
import org.json.JSONArray
import org.json.JSONObject

/** Saves progress on the phone as one small JSON string. Nothing leaves the device. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun load(): Progress = try {
        prefs.getString(KEY, null)?.let { fromJson(JSONObject(it)) } ?: Progress()
    } catch (e: Exception) {
        Progress()
    }

    fun save(p: Progress) {
        prefs.edit().putString(KEY, toJson(p).toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun toJson(p: Progress) = JSONObject().apply {
        put("v", 1)
        put("xp", p.xp)
        put("gamesPlayed", p.gamesPlayed)
        put("streak", p.streak)
        put("bestStreak", p.bestStreak)
        put("lastDailyDay", p.lastDailyDay)
        put("dailyScores", JSONObject().apply { p.dailyScores.forEach { (k, v) -> put(k.toString(), v) } })
        put("bestScores", JSONObject().apply { p.bestScores.forEach { (k, v) -> put(k.name, v) } })
        put("playsPerGame", JSONObject().apply { p.playsPerGame.forEach { (k, v) -> put(k.name, v) } })
        put("recentRatings", JSONObject().apply {
            p.recentRatings.forEach { (k, v) -> put(k.name, JSONArray(v)) }
        })
        put("achievements", JSONArray(p.achievements.toList()))
        put("bestCombo", p.bestCombo)
        put("bestMemory", p.bestMemory)
        put("totalCorrect", p.totalCorrect)
        put("onboarded", p.onboarded)
        put("settings", JSONObject().apply {
            put("sound", p.settings.sound)
            put("haptics", p.settings.haptics)
            put("reminder", p.settings.reminder)
            put("reminderHour", p.settings.reminderHour)
            put("reminderMinute", p.settings.reminderMinute)
        })
    }

    private fun fromJson(o: JSONObject): Progress {
        fun intMap(key: String): Map<String, Int> {
            val m = o.optJSONObject(key) ?: return emptyMap()
            return m.keys().asSequence().associateWith { m.optInt(it) }
        }
        fun gameMap(key: String): Map<GameId, Int> =
            intMap(key).mapNotNull { (k, v) -> GameId.fromName(k)?.let { it to v } }.toMap()

        val ratings = o.optJSONObject("recentRatings")?.let { m ->
            m.keys().asSequence().mapNotNull { k ->
                val g = GameId.fromName(k) ?: return@mapNotNull null
                val arr = m.optJSONArray(k) ?: JSONArray()
                g to List(arr.length()) { arr.optInt(it) }
            }.toMap()
        } ?: emptyMap()
        val ach = o.optJSONArray("achievements")?.let { a -> List(a.length()) { a.optString(it) }.toSet() } ?: emptySet()
        val s = o.optJSONObject("settings") ?: JSONObject()
        val d = Settings()
        return Progress(
            xp = o.optInt("xp"),
            gamesPlayed = o.optInt("gamesPlayed"),
            streak = o.optInt("streak"),
            bestStreak = o.optInt("bestStreak"),
            lastDailyDay = o.optInt("lastDailyDay"),
            dailyScores = intMap("dailyScores").mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
            bestScores = gameMap("bestScores"),
            playsPerGame = gameMap("playsPerGame"),
            recentRatings = ratings,
            achievements = ach,
            bestCombo = o.optInt("bestCombo"),
            bestMemory = o.optInt("bestMemory"),
            totalCorrect = o.optInt("totalCorrect"),
            onboarded = o.optBoolean("onboarded"),
            settings = Settings(
                sound = s.optBoolean("sound", d.sound),
                haptics = s.optBoolean("haptics", d.haptics),
                reminder = s.optBoolean("reminder", d.reminder),
                reminderHour = s.optInt("reminderHour", d.reminderHour),
                reminderMinute = s.optInt("reminderMinute", d.reminderMinute),
            ),
        )
    }

    private companion object {
        const val KEY = "progress_json"
    }
}
