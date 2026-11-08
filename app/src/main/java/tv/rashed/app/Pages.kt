package tv.rashed.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun PlaylistsPage(vm: VM) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                vm.importM3uText("قائمة M3U", text)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "تعذرت قراءة الملف", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("راشد TV", color = Accent, style = MaterialTheme.typography.labelLarge)
                Text("مكتبة الترفيه الخاصة بك", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("أضف مصادرك وابدأ المشاهدة", color = Muted)
            }
            Button(onClick = { vm.openAdd() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("＋ إضافة مصدر", color = Color(0xFF17120A), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(22.dp))
        OutlinedButton(onClick = { picker.launch(arrayOf("application/x-mpegURL", "audio/x-mpegurl", "text/plain", "*/*")) }) {
            Text("استيراد ملف M3U")
        }
        SectionTitle("مصادرك", vm.playlists.size.toString() + " قوائم")
        if (vm.playlists.isEmpty()) EmptyState("لا توجد قوائم بعد", "أضف رابط M3U أو بيانات Xtream أو بوابة Stalker")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.playlists, key = { it.id }) { item ->
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).background(Accent.copy(alpha = .15f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            Text(if (item.type == PType.M3U) "M3U" else if (item.type == PType.XTREAM) "XC" else "STB",
                                color = Accent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(item.type.name, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { vm.selectPlaylist(item) }) { Text("فتح", color = Accent) }
                        TextButton(onClick = { vm.deletePlaylist(item) }) { Text("حذف", color = Color(0xFFFF7B78)) }
                    }
                }
            }
        }
        vm.message?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
fun HomePage(vm: VM) {
    val hero = vm.selected ?: (vm.movies.firstOrNull() ?: vm.series.firstOrNull() ?: vm.live.firstOrNull())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (hero != null) HeroPanel(vm, hero, "تابع المشاهدة واكتشف المزيد")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("مباشر", vm.live.size.toString(), Modifier.weight(1f)) { vm.go(Page.LIVE) }
            MetricCard("أفلام", vm.movies.size.toString(), Modifier.weight(1f)) { vm.go(Page.MOVIES) }
            MetricCard("مسلسلات", vm.series.size.toString(), Modifier.weight(1f)) { vm.go(Page.SERIES) }
        }
        if (vm.recents.isNotEmpty()) {
            SectionTitle("تابع من حيث توقفت")
            MediaRail(vm.recents.take(12), vm) { vm.play(it) }
        }
        SectionTitle("أحدث الأفلام", "عرض الكل")
        MediaRail(vm.movies.take(12), vm) { vm.loadMovieInfo(it) }
        SectionTitle("مسلسلات مختارة")
        MediaRail(vm.series.take(12), vm) { vm.openSeries(it) }
        SectionTitle("قنوات مباشرة")
        MediaRail(vm.live.take(12), vm) { vm.play(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(84.dp), color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(value, color = Accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, color = Muted)
        }
    }
}

@Composable
private fun HeroPanel(vm: VM, item: Channel, subtitle: String) {
    Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF172338),
        modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 14.dp)) {
        Box {
            val art = item.backdrop.ifBlank { item.logo }
            if (art.startsWith("http")) {
                AsyncImage(model = art, contentDescription = item.name, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF29496B), Color(0xFF121B2A)))))
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(Color(0xF0080D18), Color(0x99080D18), Color(0x11080D18)))))
            Column(Modifier.align(Alignment.CenterStart).padding(24.dp).widthIn(max = 460.dp)) {
                Text(subtitle, color = Accent, style = MaterialTheme.typography.labelLarge)
                Text(item.name, color = Color.White, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black, maxLines = 2)
                Text(listOf(item.year, item.genre, item.rating.takeIf { it.isNotBlank() }?.let { "★ " + it })
                    .filterNotNull().filter { it.isNotBlank() }.joinToString("  •  "),
                    color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        when (item.kind) {
                            "series" -> vm.openSeries(item)
                            else -> vm.play(item)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text(if (item.kind == "series") "عرض المسلسل" else "▶ تشغيل", color = Color(0xFF16120B))
                    }
                    OutlinedButton(onClick = { vm.toggleFavorite(item) }) {
                        Text(if (vm.isFavorite(item)) "★ محفوظ" else "☆ أضف للمفضلة", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRail(items: List<Channel>, vm: VM, onClick: (Channel) -> Unit) {
    if (items.isEmpty()) {
        Text("لا توجد عناصر في هذا القسم", color = Muted, modifier = Modifier.padding(10.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(items, key = { it.key + it.name }) { item ->
            Poster(item, selected = vm.selected?.key == item.key) { onClick(item) }
        }
    }
}

@Composable
fun LivePage(vm: VM) {
    Column(Modifier.fillMaxSize()) {
        SearchAndCategories(vm, vm.live)
        if (vm.filteredLive.isEmpty()) EmptyState("لا توجد قنوات", "تحقق من القائمة أو غيّر البحث")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(vm.filteredLive, key = { it.key }) { item ->
                Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.logo.startsWith("http")) AsyncImage(item.logo, null, Modifier.size(48.dp))
                        else Box(Modifier.size(48.dp).background(Color(0xFF24344B), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("TV", color = Accent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(item.group, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { vm.loadEpg(item) }) { Text("الدليل", color = Muted) }
                        Button(onClick = { vm.play(item) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                            Text("▶", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VodPage(vm: VM, kind: String) {
    val items = if (kind == "movie") vm.movies else vm.series
    val filtered = if (kind == "movie") vm.filteredMovies else vm.filteredSeries
    Column(Modifier.fillMaxSize()) {
        SearchAndCategories(vm, items)
        val chosen = vm.selected?.takeIf { it.kind == kind } ?: items.firstOrNull()
        if (chosen != null) HeroPanel(vm, chosen, if (kind == "movie") "اختيارك الحالي" else "المسلسل المحدد")
        if (filtered.isEmpty()) EmptyState("القائمة فارغة", "لا توجد نتائج مطابقة")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered.chunked(5), key = { it.firstOrNull()?.key.orEmpty() }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        Poster(item, modifier = Modifier.weight(1f).height(172.dp), selected = vm.selected?.key == item.key) {
                            if (kind == "series") vm.openSeries(item) else vm.loadMovieInfo(item)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SearchAndCategories(vm: VM, source: List<Channel>) {
    OutlinedTextField(value = vm.query, onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("ابحث بالاسم أو التصنيف") }, singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF39465B)))
    val categories = listOf("الكل") + source.map { it.group }.filter { it.isNotBlank() }.distinct().take(30)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        items(categories) { cat ->
            FilterChip(selected = vm.category == cat, onClick = { vm.setCategory(cat) },
                label = { Text(cat) }, colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(alpha = .2f), selectedLabelColor = Accent))
        }
    }
}

@Composable
fun SeriesDetailPage(vm: VM) {
    val item = vm.seriesOpen
    if (item == null) {
        EmptyState("لم يتم اختيار مسلسل")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            HeroPanel(vm, item, "تفاصيل المسلسل")
            Text(
                vm.seriesInfo.plot.ifBlank { item.plot }.ifBlank { "لا تتوفر نبذة لهذا المسلسل" },
                color = Muted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            SectionTitle("المواسم", vm.seasons.size.toString() + " موسم")
        }
        if (vm.seasons.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.seasons) { season ->
                        FilterChip(
                            selected = vm.selectedSeason == season,
                            onClick = { vm.setSeason(season) },
                            label = { Text("الموسم " + season) }
                        )
                    }
                }
            }
        }
        item { SectionTitle("الحلقات", vm.visibleEpisodes.size.toString() + " حلقة") }
        items(vm.visibleEpisodes, key = { it.id }) { ep ->
            Surface(
                onClick = { vm.playEpisode(ep) },
                color = Panel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ep.logo.startsWith("http")) {
                        AsyncImage(ep.logo, null, Modifier.size(72.dp, 48.dp))
                    } else {
                        Box(
                            Modifier.size(72.dp, 48.dp).background(Color(0xFF26374D), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("▶", color = Accent) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("الحلقة " + ep.num + " - " + ep.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(ep.duration, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("▶", color = Accent)
                }
            }
        }
        if (vm.episodes.isEmpty()) {
            item { EmptyState("لا توجد حلقات معروضة", "تأكد من أن مزود Xtream يعيد بيانات المواسم") }
        }
    }
}

@Composable
fun SearchPage(vm: VM) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = vm.query, onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث عن قناة أو فيلم أو مسلسل") }, singleLine = true)
        val results = vm.searchResults
        if (results.isEmpty()) EmptyState("ابدأ البحث", "ستظهر النتائج هنا")
        else LazyColumn {
            items(results, key = { it.key + it.name }) { item ->
                Surface(onClick = {
                    when (item.kind) {
                        "movie" -> vm.loadMovieInfo(item)
                        "series" -> vm.openSeries(item)
                        else -> vm.play(item)
                    }
                }, color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(item.name, color = Color.White, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsPage(vm: VM) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("تخصيص التطبيق")
        SettingSwitch("إخفاء الساعة", vm.settings.hideClock) { vm.updateSettings(vm.settings.copy(hideClock = it)) }
        SettingSwitch("تنسيق الساعة 24 ساعة", vm.settings.clock24) { vm.updateSettings(vm.settings.copy(clock24 = it)) }
        SettingSwitch("إخفاء قسم المباشر", vm.settings.hideLive) { vm.updateSettings(vm.settings.copy(hideLive = it)) }
        SettingSwitch("إخفاء قسم الأفلام", vm.settings.hideMovies) { vm.updateSettings(vm.settings.copy(hideMovies = it)) }
        SettingSwitch("إخفاء قسم المسلسلات", vm.settings.hideSeries) { vm.updateSettings(vm.settings.copy(hideSeries = it)) }
        SettingSwitch("ترتيب القنوات أبجدياً", vm.settings.sortLive) { vm.updateSettings(vm.settings.copy(sortLive = it)) }
        SettingSwitch("ترتيب الأفلام أبجدياً", vm.settings.sortVod) { vm.updateSettings(vm.settings.copy(sortVod = it)) }
        SettingSwitch("ترتيب المسلسلات أبجدياً", vm.settings.sortSeries) { vm.updateSettings(vm.settings.copy(sortSeries = it)) }
        SettingSwitch("إظهار البث بصيغة TS عند توفرها", vm.settings.liveTs) { vm.updateSettings(vm.settings.copy(liveTs = it)) }
        SettingSwitch("تفعيل الرقابة الأبوية", vm.settings.parental) { vm.updateSettings(vm.settings.copy(parental = it)) }
        SettingSwitch("استخدام مشغل خارجي", vm.settings.external) { vm.updateSettings(vm.settings.copy(external = it)) }
        SectionTitle("حول التطبيق")
        Text("Rashed TV • Android", color = Muted)
        Text("مشغل قوائم IPTV. أضف فقط المصادر التي تملك حق الوصول إليها.", color = Muted)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AddDialog(vm: VM) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("00:1A:79:") }
    var type by remember { mutableStateOf(PType.M3U) }
    AlertDialog(
        onDismissRequest = { vm.closeAdd() },
        title = { Text("إضافة مصدر", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PType.values().forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option },
                            label = { Text(when (option) { PType.M3U -> "M3U"; PType.XTREAM -> "Xtream"; PType.STALKER -> "Stalker" }) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("اسم القائمة") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(if (type == PType.STALKER) "رابط Portal" else if (type == PType.XTREAM) "Server URL" else "رابط M3U / M3U8") }, singleLine = true)
                if (type == PType.XTREAM) {
                    OutlinedTextField(username, { username = it }, label = { Text("اسم المستخدم") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true)
                }
                if (type == PType.STALKER) OutlinedTextField(mac, { mac = it }, label = { Text("MAC Address") }, singleLine = true)
                vm.message?.let { Text(it, color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = {
            Button(onClick = { vm.addPlaylist(name, type, url, username, password, mac) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("حفظ واتصال", color = Color.Black) }
        },
        dismissButton = { TextButton(onClick = { vm.closeAdd() }) { Text("إلغاء") } },
        containerColor = Color(0xFF172235)
    )
}

@Composable
fun FullPlayer(vm: VM) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                player = vm.exo
                useController = true
                controllerAutoShow = true
                keepScreenOn = true
            }
        }, update = { it.player = vm.exo }, modifier = Modifier.fillMaxSize())
        Text(vm.playerTitle, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(18.dp))
        vm.playerError?.let { error ->
            Surface(color = Color(0xDD351B1B), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(error, color = Color.White, modifier = Modifier.padding(18.dp))
            }
        }
    }
}
