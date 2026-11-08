package tv.rashed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Accent,
                        background = Color(0xFF0A0F2C),
                        surface = Color(0xFF131A33),
                        surfaceVariant = Color(0xFF1B2444)
                    )
                ) {
                    Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0F2C)) { App(vm) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.exo.pause()
    }
}

@Composable
fun App(vm: VM) {
    val act = LocalContext.current as ComponentActivity
    BackHandler {
        when {
            vm.fullscreen -> vm.exitFullscreen()
            vm.page == Page.SERIES_DETAIL -> vm.go(Page.SERIES)
            vm.page == Page.PLAYLISTS -> if (vm.current != null) vm.go(Page.HOME) else act.finish()
            vm.page == Page.HOME -> vm.logout()
            else -> vm.go(Page.HOME)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AppBackground(vm) {
            when (vm.page) {
                Page.PLAYLISTS -> PlaylistsPage(vm)
                Page.HOME -> Shell(vm, vm.current?.name ?: "راشد TV") { HomePage(vm) }
                Page.LIVE -> Shell(vm, "البث المباشر") { LivePage(vm) }
                Page.MOVIES -> Shell(vm, "أفلام") { VodPage(vm, "movie") }
                Page.SERIES -> Shell(vm, "مسلسلات") { VodPage(vm, "series") }
                Page.SERIES_DETAIL -> Shell(vm, vm.seriesOpen?.name ?: "مسلسلات") { SeriesDetailPage(vm) }
                Page.SEARCH -> Shell(vm, "بحث") { SearchPage(vm) }
                Page.SETTINGS -> Shell(vm, "الإعدادات") { SettingsPage(vm) }
            }
        }
        if (vm.fullscreen) FullPlayer(vm)
        if (vm.showAdd) AddDialog(vm)
        vm.epgLines?.let { lines ->
            AlertDialog(
                onDismissRequest = { vm.epgLines = null },
                title = { Text("دليل البرامج") },
                text = { Text(lines.joinToString("\n")) },
                confirmButton = { TextButton(onClick = { vm.epgLines = null }) { Text("إغلاق") } }
            )
        }
    }
}
