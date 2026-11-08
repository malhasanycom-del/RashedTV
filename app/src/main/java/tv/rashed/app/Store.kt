package tv.rashed.app

import android.content.Context
import org.json.JSONArray

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rashed", Context.MODE_PRIVATE)

    fun playlists(): List<Playlist> = try {
        JSONArray(sp.getString("pl", "[]")).objs().map { Playlist.fromJson(it) }
    } catch (e: Exception) { emptyList() }

    fun savePlaylists(l: List<Playlist>) {
        sp.edit().putString("pl", JSONArray(l.map { it.toJson() }).toString()).apply()
    }

    private fun read(k: String): List<Channel> = try {
        JSONArray(sp.getString(k, "[]")).objs().map { Channel.fromJson(it) }
    } catch (e: Exception) { emptyList() }

    private fun write(k: String, l: List<Channel>) {
        sp.edit().putString(k, JSONArray(l.map { it.toJson() }).toString()).apply()
    }

    fun favs(pid: String) = read("fav_$pid")
    fun saveFavs(pid: String, l: List<Channel>) = write("fav_$pid", l)
    fun recents(pid: String) = read("rec_$pid")
    fun saveRecents(pid: String, l: List<Channel>) = write("rec_$pid", l)

    fun pos(key: String): Long = sp.getLong("pos_$key", 0L)
    fun savePos(key: String, v: Long) { sp.edit().putLong("pos_$key", v).apply() }

    fun hidden(pid: String): Set<String> = try {
        val a = JSONArray(sp.getString("hid_$pid", "[]"))
        (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (e: Exception) { emptySet() }

    fun saveHidden(pid: String, s: Set<String>) {
        sp.edit().putString("hid_$pid", JSONArray(s.toList()).toString()).apply()
    }

    fun settings() = Settings(
        hideClock = sp.getBoolean("hideClock", false),
        clock24 = sp.getBoolean("clock24", false),
        hideLive = sp.getBoolean("hideLive", false),
        hideMovies = sp.getBoolean("hideMovies", false),
        hideSeries = sp.getBoolean("hideSeries", false),
        sortLive = sp.getBoolean("sortLive", false),
        sortVod = sp.getBoolean("sortVod", false),
        sortSeries = sp.getBoolean("sortSeries", false),
        liveTs = sp.getBoolean("liveTs", false),
        external = sp.getBoolean("external", false),
        parental = sp.getBoolean("parental", false)
    )

    fun saveSettings(s: Settings) {
        sp.edit()
            .putBoolean("hideClock", s.hideClock).putBoolean("clock24", s.clock24)
            .putBoolean("hideLive", s.hideLive).putBoolean("hideMovies", s.hideMovies)
            .putBoolean("hideSeries", s.hideSeries).putBoolean("sortLive", s.sortLive)
            .putBoolean("sortVod", s.sortVod).putBoolean("sortSeries", s.sortSeries)
            .putBoolean("liveTs", s.liveTs).putBoolean("external", s.external)
            .putBoolean("parental", s.parental).apply()
    }
}
