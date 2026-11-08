package tv.rashed.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

val Accent = Color(0xFFFFB547)
val Ink = Color(0xFF080D18)
val Panel = Color(0xE6172030)
val Muted = Color(0xFF9BA8BA)

@Composable
fun AppBackground(vm: VM, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101D31), Ink, Color(0xFF090D16))))) {
        val backdrop = vm.selected?.backdrop?.takeIf { it.startsWith("http") }
        if (backdrop != null) {
            AsyncImage(model = backdrop, contentDescription = null,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.56f).align(Alignment.TopCenter),
                alpha = 0.17f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0x22070B12), Color(0xCC080D18), Ink))))
        }
        content()
    }
}

@Composable
fun Shell(vm: VM, title: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.width(76.dp).fillMaxHeight().background(Panel, RoundedCornerShape(24.dp)).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("R", color = Accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            NavigationDot("⌂", vm.page == Page.HOME) { vm.go(Page.HOME) }
            NavigationDot("▣", vm.page == Page.LIVE) { vm.go(Page.LIVE) }
            NavigationDot("▤", vm.page == Page.MOVIES) { vm.go(Page.MOVIES) }
            NavigationDot("▧", vm.page == Page.SERIES) { vm.go(Page.SERIES) }
            NavigationDot("⌕", vm.page == Page.SEARCH) { vm.go(Page.SEARCH) }
            Spacer(Modifier.weight(1f))
            NavigationDot("⚙", vm.page == Page.SETTINGS) { vm.go(Page.SETTINGS) }
            NavigationDot("≡", false) { vm.go(Page.PLAYLISTS) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("راشد TV", color = Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(vm.current?.name.orEmpty(), color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            content()
        }
    }
}

@Composable
private fun NavigationDot(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Accent else Muted, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Poster(item: Channel, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = if (selected) Accent.copy(alpha = 0.18f) else Color(0xFF172235),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Accent) else null,
        modifier = modifier.width(142.dp).height(206.dp)) {
        Box {
            val image = item.logo.ifBlank { item.backdrop }
            if (image.startsWith("http")) {
                AsyncImage(model = image, contentDescription = item.name, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF243C58), Color(0xFF111827)))),
                    contentAlignment = Alignment.Center) {
                    Text(item.name.take(1), color = Accent, style = MaterialTheme.typography.displayMedium)
                }
            }
            Box(Modifier.fillMaxWidth().height(82.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))))
            Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge, maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String = "") {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✦", color = Accent, style = MaterialTheme.typography.displaySmall)
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (detail.isNotBlank()) Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
