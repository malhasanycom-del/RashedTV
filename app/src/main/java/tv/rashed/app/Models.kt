package tv.rashed.app

import org.json.JSONObject

enum class PType { M3U, XTREAM, STALKER }
enum class Page { PLAYLISTS, HOME, LIVE, MOVIES, SERIES, SERIES_DETAIL, SEARCH, SETTINGS }

data class Playlist(
    val id: String,
    val name: String,
    val type: PType,
    val url: String,
    val user: String = "",
    val pass: String = "",
    val mac: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type.name).put("url", url)
        .put("user", user).put("pass", pass).put("mac", mac)

    companion object {
        fun fromJson(o: JSONObject) = Playlist(
            o.s("id"), o.s("name"), PType.valueOf(o.s("type")), o.s("url"),
            o.s("user"), o.s("pass"), o.s("mac")
        )
    }
}

data class Channel(
    val id: String,
    val name: String,
    val logo: String,
    val group: String,
    val url: String,
    val cmd: String = "",
    val kind: String = "live",
    val rating: String = "",
    val plot: String = "",
    val genre: String = "",
    val cast: String = "",
    val year: String = "",
    val backdrop: String = "",
    val added: Long = 0L
) {
    val key: String get() = "$kind:$id"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("logo", logo).put("group", group)
        .put("url", url).put("cmd", cmd).put("kind", kind).put("rating", rating)
        .put("plot", plot).put("genre", genre).put("cast", cast).put("year", year)
        .put("backdrop", backdrop).put("added", added)

    companion object {
        fun fromJson(o: JSONObject) = Channel(
            o.s("id"), o.s("name"), o.s("logo"), o.s("group"), o.s("url"), o.s("cmd"),
            o.s("kind").ifEmpty { "live" }, o.s("rating"), o.s("plot"), o.s("genre"),
            o.s("cast"), o.s("year"), o.s("backdrop"), o.optLong("added", 0L)
        )
    }
}

data class Info(
    val plot: String = "",
    val genre: String = "",
    val cast: String = "",
    val rating: String = "",
    val year: String = "",
    val duration: String = "",
    val backdrop: String = ""
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val num: Int,
    val url: String,
    val plot: String,
    val duration: String,
    val logo: String
)

data class Content(val live: List<Channel>, val movies: List<Channel>, val series: List<Channel>)

data class Settings(
    val hideClock: Boolean = false,
    val clock24: Boolean = false,
    val hideLive: Boolean = false,
    val hideMovies: Boolean = false,
    val hideSeries: Boolean = false,
    val sortLive: Boolean = false,
    val sortVod: Boolean = false,
    val sortSeries: Boolean = false,
    val liveTs: Boolean = false,
    val external: Boolean = false,
    val parental: Boolean = false
)
