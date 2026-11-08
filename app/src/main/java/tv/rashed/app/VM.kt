package tv.rashed.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class VM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val exo: ExoPlayer = ExoPlayer.Builder(app).build()

    var page by mutableStateOf<Page>(Page.PLAYLISTS)
        private set
    var playlists by mutableStateOf<List<Playlist>>(store.playlists())
        private set
    var current by mutableStateOf<Playlist?>(null)
        private set
    var live by mutableStateOf<List<Channel>>(emptyList())
        private set
    var movies by mutableStateOf<List<Channel>>(emptyList())
        private set
    var series by mutableStateOf<List<Channel>>(emptyList())
        private set
    var selected by mutableStateOf<Channel?>(null)
        private set
    var seriesOpen by mutableStateOf<Channel?>(null)
        private set
    var episodes by mutableStateOf<List<Episode>>(emptyList())
        private set
    var seriesInfo by mutableStateOf<Info>(Info())
        private set
    var movieInfo by mutableStateOf<Info>(Info())
        private set
    var selectedSeason by mutableStateOf<Int>(1)
        private set
    var favorites by mutableStateOf<List<Channel>>(emptyList())
        private set
    var recents by mutableStateOf<List<Channel>>(emptyList())
        private set
    var query by mutableStateOf<String>("")
        private set
    var category by mutableStateOf<String>("الكل")
        private set
    var settings by mutableStateOf<Settings>(store.settings())
        private set
    var fullscreen by mutableStateOf<Boolean>(false)
        private set
    var showAdd by mutableStateOf<Boolean>(false)
    var epgLines by mutableStateOf<List<String>?>(null)
    var loading by mutableStateOf<Boolean>(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf<Boolean>(false)
        private set
    var playerTitle by mutableStateOf<String>("")
        private set
    var playerError by mutableStateOf<String?>(null)
        private set
    var busyLabel by mutableStateOf<String>("")
        private set
    private var stalker: Stalker? = null

    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
    val visibleEpisodes: List<Episode> get() = episodes.filter { it.season == selectedSeason }
    val filteredLive: List<Channel> get() = filter(live)
    val filteredMovies: List<Channel> get() = filter(movies)
    val filteredSeries: List<Channel> get() = filter(series)
    val searchResults: List<Channel> get() = filter(live + movies + series)

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerError = "تعذّر تشغيل هذا الرابط: " + (error.message ?: "خطأ في البث")
            }
        })
        if (playlists.isNotEmpty()) current = playlists.first()
    }

    private fun filter(source: List<Channel>): List<Channel> {
        val q = query.trim()
        return source.asSequence()
            .filter { q.isEmpty() || it.name.contains(q, true) || it.group.contains(q, true) }
            .filter { category == "الكل" || it.group == category }
            .toList()
    }

    fun go(destination: Page) { page = destination; query = ""; category = "الكل" }
    fun updateQuery(value: String) { query = value }
    fun updateCategory(value: String) { category = value }
    fun setSeason(value: Int) { selectedSeason = value }

    fun openAdd() { showAdd = true; message = null }
    fun closeAdd() { showAdd = false }

    fun addPlaylist(name: String, type: PType, url: String, user: String = "", pass: String = "", mac: String = "") {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) { message = "أدخل رابط المصدر أولاً"; return }
        if (type == PType.XTREAM && (user.isBlank() || pass.isBlank())) {
            message = "أدخل اسم المستخدم وكلمة المرور"; return
        }
        if (type == PType.STALKER && mac.isBlank()) { message = "أدخل عنوان MAC"; return }
        val item = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "قائمة جديدة" },
            type, cleanUrl, user.trim(), pass.trim(), mac.trim())
        playlists = playlists + item
        store.savePlaylists(playlists)
        showAdd = false
        loadPlaylist(item)
    }

    fun importM3uText(name: String, text: String) {
        viewModelScope.launch {
            loading = true; busyLabel = "قراءة ملف القائمة"
            try {
                val parsed = withContext(Dispatchers.Default) { M3u.parse(text) }
                if (parsed.isEmpty()) throw IllegalStateException("لم يتم العثور على عناصر M3U صالحة")
                val item = Playlist(UUID.randomUUID().toString(), name.ifBlank { "قائمة مستوردة" }, PType.M3U, "local://import")
                playlists = playlists + item
                store.savePlaylists(playlists)
                current = item
                setContent(Content(
                    parsed.filter { it.kind == "live" },
                    parsed.filter { it.kind == "movie" },
                    parsed.filter { it.kind == "series" }
                ))
                page = Page.HOME
                message = "تم استيراد " + parsed.size + " عنصر"
            } catch (e: Exception) { message = e.message ?: "فشل استيراد الملف" }
            finally { loading = false; busyLabel = "" }
        }
    }

    fun loadPlaylist(item: Playlist) {
        current = item
        viewModelScope.launch {
            loading = true; busyLabel = "جارٍ تحميل " + item.name; message = null
            try {
                val content = withContext(Dispatchers.IO) {
                    when (item.type) {
                        PType.M3U -> {
                            val text = Net.get(item.url)
                            val parsed = M3u.parse(text)
                            Content(parsed.filter { it.kind == "live" },
                                parsed.filter { it.kind == "movie" }, parsed.filter { it.kind == "series" })
                        }
                        PType.XTREAM -> Xtream.load(item)
                        PType.STALKER -> {
                            val api = Stalker(item.url, item.mac)
                            api.connect()
                            stalker = api
                            Content(api.channels(), emptyList(), emptyList())
                        }
                    }
                }
                setContent(content)
                page = Page.HOME
                message = "تم تحميل القوائم بنجاح"
            } catch (e: Exception) {
                message = e.message ?: "حدث خطأ أثناء تحميل القائمة"
                page = Page.PLAYLISTS
            } finally { loading = false; busyLabel = "" }
        }
    }

    private fun setContent(content: Content) {
        live = content.live
        movies = content.movies
        series = content.series
        current?.let {
            favorites = store.favs(it.id)
            recents = store.recents(it.id)
        }
        selected = (live + movies + series).firstOrNull()
    }

    fun selectPlaylist(item: Playlist) = loadPlaylist(item)

    fun deletePlaylist(item: Playlist) {
        playlists = playlists.filterNot { it.id == item.id }
        store.savePlaylists(playlists)
        if (current?.id == item.id) {
            current = null; live = emptyList(); movies = emptyList(); series = emptyList()
            favorites = emptyList(); recents = emptyList(); page = Page.PLAYLISTS
        }
    }

    fun logout() {
        current = null; live = emptyList(); movies = emptyList(); series = emptyList()
        selected = null; favorites = emptyList(); recents = emptyList(); page = Page.PLAYLISTS
    }

    fun toggleFavorite(item: Channel) {
        val exists = favorites.any { it.key == item.key }
        favorites = if (exists) favorites.filterNot { it.key == item.key } else listOf(item) + favorites
        current?.let { store.saveFavs(it.id, favorites) }
    }

    fun isFavorite(item: Channel): Boolean = favorites.any { it.key == item.key }

    fun hideChannel(item: Channel) {
        val pid = current?.id ?: return
        val hidden = store.hidden(pid).toMutableSet()
        if (!hidden.add(item.key)) hidden.remove(item.key)
        store.saveHidden(pid, hidden)
        live = live.filterNot { it.key in hidden }
        movies = movies.filterNot { it.key in hidden }
        series = series.filterNot { it.key in hidden }
    }

    fun play(item: Channel) {
        selected = item
        playerTitle = item.name
        playerError = null
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    if (current?.type == PType.STALKER && item.cmd.isNotBlank()) {
                        (stalker ?: Stalker(current!!.url, current!!.mac).also { it.connect(); stalker = it }).resolve(item.cmd)
                    } else item.url
                }
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                exo.prepare()
                exo.playWhenReady = true
                playing = true
                fullscreen = true
                current?.let { p ->
                    recents = (listOf(item) + recents.filterNot { it.key == item.key }).take(40)
                    store.saveRecents(p.id, recents)
                }
            } catch (e: Exception) { playerError = e.message ?: "فشل تجهيز البث" }
        }
    }

    fun playEpisode(episode: Episode) {
        playerTitle = episode.title
        playerError = null
        exo.setMediaItem(MediaItem.fromUri(episode.url))
        exo.prepare()
        exo.playWhenReady = true
        fullscreen = true
    }

    fun openSeries(item: Channel) {
        seriesOpen = item
        selected = item
        selectedSeason = 1
        episodes = emptyList()
        seriesInfo = Info()
        page = Page.SERIES_DETAIL
        val p = current ?: return
        if (p.type == PType.XTREAM) {
            viewModelScope.launch {
                loading = true; busyLabel = "تحميل المواسم والحلقات"
                try {
                    val result = withContext(Dispatchers.IO) { Xtream.series(p, item.id) }
                    seriesInfo = result.first
                    episodes = result.second
                    selectedSeason = seasons.firstOrNull() ?: 1
                    if (episodes.isEmpty()) message = "لم تصل حلقات لهذا المسلسل من المزود"
                } catch (e: Exception) { message = e.message ?: "تعذّر تحميل تفاصيل المسلسل" }
                finally { loading = false; busyLabel = "" }
            }
        } else {
            message = "تفاصيل المواسم والحلقات تتطلب بيانات Xtream أو ملف M3U يحتوي روابط الحلقات"
        }
    }

    fun loadMovieInfo(item: Channel) {
        selected = item
        movieInfo = Info(item.plot, item.genre, item.cast, item.rating, item.year, backdrop = item.backdrop)
        val p = current ?: return
        if (p.type == PType.XTREAM) viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { Xtream.vodInfo(p, item.id) }
            movieInfo = result
        }
    }

    fun loadEpg(item: Channel) {
        val p = current ?: return
        if (p.type != PType.XTREAM) { message = "دليل البرامج غير متاح لهذا المصدر"; return }
        viewModelScope.launch {
            try { epgLines = withContext(Dispatchers.IO) { Xtream.epg(p, item.id) } }
            catch (e: Exception) { message = e.message ?: "تعذّر تحميل دليل البرامج" }
        }
    }

    fun updateSettings(value: Settings) { settings = value; store.saveSettings(value) }
    fun enterFullscreen() { fullscreen = true }
    fun exitFullscreen() { fullscreen = false }
    fun toggleFullscreen() { fullscreen = !fullscreen }
    fun pause() { exo.pause() }
    fun resume() { exo.play() }
    fun dismissMessage() { message = null; playerError = null }

    override fun onCleared() {
        exo.release()
        super.onCleared()
    }
}
