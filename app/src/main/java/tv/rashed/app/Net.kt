package tv.rashed.app

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

object Net {
    const val UA_STB = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val request = Request.Builder().url(url).header("User-Agent", "RashedTV/1.0 Android").apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP " + response.code)
            return response.body?.string().orEmpty()
        }
    }
}

object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)""")
    private val seriesWords = listOf("series", "tv show", "مسلسل", "مسلسلات", "دراما")
    private val movieWords = listOf("movie", "movies", "film", "films", "فيلم", "أفلام", "افلام")

    fun parse(text: String): List<Channel> {
        val result = ArrayList<Channel>()
        var title = ""; var logo = ""; var group = ""; var tvgId = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.startsWith("#EXTINF", true) -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"].orEmpty()
                    group = attrs["group-title"].orEmpty()
                    tvgId = attrs["tvg-id"].orEmpty()
                    title = line.substringAfterLast(',').trim().ifEmpty { attrs["tvg-name"].orEmpty() }
                }
                line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim()
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val name = title.ifEmpty { line.substringAfterLast('/').substringBefore('?') }
                    val textToClassify = (group + " " + line).lowercase()
                    val kind = when {
                        seriesWords.any { textToClassify.contains(it) } || line.contains("/series/", true) -> "series"
                        movieWords.any { textToClassify.contains(it) } || line.contains("/movie/", true) -> "movie"
                        else -> "live"
                    }
                    result.add(Channel(
                        id = (tvgId.ifEmpty { line }).hashCode().toString(), name = name,
                        logo = logo, group = group, url = line, kind = kind
                    ))
                    title = ""; logo = ""; group = ""; tvgId = ""
                }
            }
        }
        return result.distinctBy { it.key + it.url }
    }
}

object Xtream {
    private fun base(value: String): String {
        var url = value.trim().trimEnd('/')
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "http://" + url
        return url
    }
    private fun api(p: Playlist, action: String = ""): String {
        val root = base(p.url) + "/player_api.php?username=" + enc(p.user) + "&password=" + enc(p.pass)
        return if (action.isBlank()) root else root + "&action=" + action
    }
    private fun categories(p: Playlist, action: String): Map<String, String> = try {
        JSONArray(Net.get(api(p, action))).objs().associate { it.s("category_id") to it.s("category_name") }
    } catch (_: Exception) { emptyMap() }

    fun load(p: Playlist): Content {
        val auth = JSONObject(Net.get(api(p))).optJSONObject("user_info")
            ?: throw IllegalStateException("تعذّر التحقق من بيانات Xtream")
        if (auth.optInt("auth", 1) == 0) throw IllegalStateException("اسم المستخدم أو كلمة المرور غير صحيحة")
        val root = base(p.url)
        val liveCats = categories(p, "get_live_categories")
        val vodCats = categories(p, "get_vod_categories")
        val seriesCats = categories(p, "get_series_categories")
        val live = JSONArray(Net.get(api(p, "get_live_streams"))).objs().map { o ->
            val id = o.s("stream_id")
            Channel(id, o.s("name"), o.s("stream_icon"), liveCats[o.s("category_id")].orEmpty(),
                root + "/live/" + enc(p.user) + "/" + enc(p.pass) + "/" + id + ".ts")
        }
        val movies = JSONArray(Net.get(api(p, "get_vod_streams"))).objs().map { o ->
            val id = o.s("stream_id"); val ext = o.s("container_extension").ifBlank { "mp4" }
            Channel(id, o.s("name"), o.s("stream_icon"), vodCats[o.s("category_id")].orEmpty(),
                root + "/movie/" + enc(p.user) + "/" + enc(p.pass) + "/" + id + "." + ext,
                kind = "movie", rating = o.s("rating"),
                year = Regex("""\b(19|20)\d{2}\b""").find(o.s("name"))?.value.orEmpty(),
                added = o.s("added").toLongOrNull() ?: 0L)
        }
        val series = JSONArray(Net.get(api(p, "get_series"))).objs().map { o ->
            val id = o.s("series_id")
            Channel(id, o.s("name"), o.s("cover"), seriesCats[o.s("category_id")].orEmpty(),
                "", kind = "series", rating = o.s("rating"),
                year = Regex("""\b(19|20)\d{2}\b""").find(o.s("name"))?.value.orEmpty(),
                plot = o.s("plot"), backdrop = o.optJSONArray("backdrop_path")?.optString(0).orEmpty())
        }
        return Content(live, movies, series)
    }

    fun vodInfo(p: Playlist, id: String): Info = try {
        val info = JSONObject(Net.get(api(p, "get_vod_info") + "&vod_id=" + enc(id))).optJSONObject("info")
            ?: return Info()
        Info(info.s("plot").ifBlank { info.s("description") }, info.s("genre"),
            info.s("cast").ifBlank { info.s("actors") }, info.s("rating"),
            info.s("releasedate").take(4), info.s("duration"),
            info.optJSONArray("backdrop_path")?.optString(0).orEmpty())
    } catch (_: Exception) { Info() }

    fun series(p: Playlist, id: String): Pair<Info, List<Episode>> {
        val root = base(p.url)
        val json = JSONObject(Net.get(api(p, "get_series_info") + "&series_id=" + enc(id)))
        val i = json.optJSONObject("info")
        val info = if (i == null) Info() else Info(i.s("plot"), i.s("genre"), i.s("cast"),
            i.s("rating"), i.s("releaseDate").ifBlank { i.s("release_date") }.take(4),
            backdrop = i.optJSONArray("backdrop_path")?.optString(0).orEmpty())
        val episodes = ArrayList<Episode>()
        fun add(o: JSONObject, season: Int) {
            val eid = o.s("id"); val ext = o.s("container_extension").ifBlank { "mp4" }
            val details = o.optJSONObject("info")
            episodes.add(Episode(eid, o.s("title").ifBlank { "حلقة " + (episodes.size + 1) },
                o.optInt("season", season), o.optInt("episode_num", episodes.size + 1),
                root + "/series/" + enc(p.user) + "/" + enc(p.pass) + "/" + eid + "." + ext,
                details?.s("plot").orEmpty(), details?.s("duration").orEmpty(),
                details?.s("movie_image").orEmpty()))
        }
        when (val raw = json.opt("episodes")) {
            is JSONObject -> raw.keys().asSequence().forEach { key ->
                raw.optJSONArray(key)?.objs()?.forEach { add(it, key.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (index in 0 until raw.length()) {
                when (val season = raw.opt(index)) {
                    is JSONArray -> season.objs().forEach { add(it, index + 1) }
                    is JSONObject -> add(season, 1)
                }
            }
        }
        return info to episodes.sortedWith(compareBy<Episode> { it.season }.thenBy { it.num })
    }

    fun epg(p: Playlist, streamId: String): List<String> = try {
        val array = JSONObject(Net.get(api(p, "get_short_epg") + "&stream_id=" + enc(streamId) + "&limit=6"))
            .optJSONArray("epg_listings") ?: return emptyList()
        array.objs().map {
            val title = try { String(Base64.decode(it.s("title"), Base64.DEFAULT)) } catch (_: Exception) { it.s("title") }
            it.s("start").substringAfter(' ').take(5) + "   " + title
        }
    } catch (_: Exception) { emptyList() }
}

class Stalker(portal: String, macAddress: String) {
    private val mac = macAddress.trim().uppercase()
    private val origin: String
    private val endpoints: List<String>
    private var loadUrl = ""
    private var token = ""

    init {
        var value = portal.trim().trimEnd('/')
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) value = "http://" + value
        val uri = java.net.URI(value)
        origin = uri.scheme + "://" + uri.authority
        val clean = value.removeSuffix("/c")
        endpoints = if (clean.endsWith(".php")) listOf(clean) else listOf(
            clean + "/server/load.php", clean + "/portal.php", clean + "/stalker_portal/server/load.php")
    }
    private fun call(query: String): JSONObject {
        val headers = mutableMapOf(
            "Cookie" to "mac=" + mac + "; stb_lang=en; timezone=GMT",
            "User-Agent" to Net.UA_STB,
            "X-User-Agent" to "Model: MAG250; Link: WiFi",
            "Referer" to origin + "/c/", "Accept" to "*/*")
        if (token.isNotBlank()) headers["Authorization"] = "Bearer " + token
        val raw = Net.get(loadUrl + "?" + query + "&JsHttpRequest=1-xml", headers).trim()
        val start = raw.indexOf('{')
        if (start < 0) throw IOException("استجابة بوابة Stalker غير مفهومة")
        return JSONObject(raw.substring(start))
    }
    fun connect() {
        var last: Exception? = null
        endpoints.forEach { endpoint ->
            try {
                loadUrl = endpoint; token = ""
                token = call("type=stb&action=handshake&token=&prehash=0").optJSONObject("js")?.s("token").orEmpty()
                if (token.isBlank()) throw IOException("البوابة لم ترسل رمز الاتصال")
                return
            } catch (e: Exception) { last = e }
        }
        throw IOException("فشل اتصال Stalker: " + (last?.message ?: "تحقق من الرابط والماك"))
    }
    fun channels(): List<Channel> {
        if (token.isBlank()) connect()
        val genres = try {
            call("type=itv&action=get_genres").optJSONArray("js")?.objs()
                ?.associate { it.s("id") to it.s("title") }.orEmpty()
        } catch (_: Exception) { emptyMap() }
        val data = try {
            when (val js = call("type=itv&action=get_all_channels").opt("js")) {
                is JSONArray -> js.objs()
                is JSONObject -> js.optJSONArray("data")?.objs().orEmpty()
                else -> emptyList()
            }
        } catch (_: Exception) { emptyList() }
        val rows = if (data.isNotEmpty()) data else {
            val all = ArrayList<JSONObject>()
            for (page in 1..100) {
                val js = call("type=itv&action=get_ordered_list&genre=*&force_ch_link_check=&fav=0&sortby=number&hd=0&p=" + page)
                    .optJSONObject("js") ?: break
                val batch = js.optJSONArray("data")?.objs().orEmpty()
                if (batch.isEmpty()) break
                all.addAll(batch)
                if (all.size >= js.optInt("total_items", Int.MAX_VALUE)) break
            }
            all
        }
        if (rows.isEmpty()) throw IOException("لم يتم العثور على قنوات لهذا الماك")
        return rows.map { row ->
            val path = row.s("logo")
            val logo = when {
                path.startsWith("http") -> path
                path.isBlank() -> ""
                else -> origin + "/stalker_portal/misc/logos/320/" + path
            }
            Channel(row.s("id"), row.s("name"), logo, genres[row.s("tv_genre_id")].orEmpty(),
                "", row.s("cmd"), kind = "live")
        }
    }
    fun resolve(command: String): String {
        if (token.isBlank()) connect()
        val response = call("type=itv&action=create_link&cmd=" + enc(command) +
            "&series=&forced_storage=&disable_ad=0&download=0&force_ch_link_check=0")
        val link = response.optJSONObject("js")?.s("cmd").orEmpty().ifBlank { command }
        return Regex("""https?://\S+""").find(link)?.value ?: link
    }
}
