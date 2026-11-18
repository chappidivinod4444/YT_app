package app.vidflow

import android.app.Application
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import app.vidflow.core.Library
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.player.PlayerHolder
import app.vidflow.core.GoogleAccount
import app.vidflow.ui.AccountListScreen
import app.vidflow.ui.ChannelScreen
import app.vidflow.ui.ShortsScreen
import kotlinx.coroutines.launch
import app.vidflow.ui.HomeScreen
import app.vidflow.ui.LibraryScreen
import app.vidflow.ui.Nav
import app.vidflow.ui.PlayerSurface
import app.vidflow.ui.PlaylistScreen
import app.vidflow.ui.SearchScreen
import app.vidflow.ui.Thumb
import app.vidflow.ui.VideoScreen
import app.vidflow.ui.VidflowTheme
import kotlinx.coroutines.flow.MutableStateFlow

class VidflowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Yt.init()
        Library.init(this)
        app.vidflow.core.GoogleAccount.init(this)
        PlayerHolder.init(this)
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data object Search : Screen
    data object Shorts : Screen
    data object Liked : Screen
    data object MyPlaylists : Screen
    data class AccountPlaylist(val url: String, val title: String) : Screen
    data class Watch(val video: Video) : Screen
    data class ChannelPage(val url: String) : Screen
    data class PlaylistPage(val url: String) : Screen
}

object Ui {
    val inPip = MutableStateFlow(false)
    val fullscreen = MutableStateFlow(false)
    val watching = MutableStateFlow(false)
    val openUrl = MutableStateFlow<String?>(null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent { VidflowTheme { Root(this) } }
    }

    override fun onStart() {
        super.onStart()
        PlayerHolder.connectSession(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Opens links shared from other apps (e.g. a video link from a chat). */
    private fun handleShare(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        Ui.openUrl.value = url
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Ui.watching.value && PlayerHolder.player.isPlaying) enterPip()
    }

    fun enterPip() {
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Throwable) {
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Ui.inPip.value = isInPictureInPictureMode
    }

    fun setFullscreen(on: Boolean) {
        Ui.fullscreen.value = on
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

@Composable
private fun Root(activity: MainActivity) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var tab by remember { mutableStateOf<Screen>(Screen.Home) }
    val inPip by Ui.inPip.collectAsState()
    val fullscreen by Ui.fullscreen.collectAsState()
    val current by PlayerHolder.current.collectAsState()
    val shared by Ui.openUrl.collectAsState()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val say: (String) -> Unit = { m -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(m) } }

    fun push(s: Screen) {
        if (s is Screen.Watch) stack.removeAll { it is Screen.Watch }
        stack.add(s)
    }

    val nav = remember {
        Nav(
            video = { push(Screen.Watch(it)) },
            channel = { push(Screen.ChannelPage(it)) },
            playlist = { push(Screen.PlaylistPage(it)) },
            search = { push(Screen.Search) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
        )
    }

    // A link shared into the app.
    if (shared != null) {
        val url = shared!!
        Ui.openUrl.value = null
        when {
            url.contains("list=") && !url.contains("watch") -> push(Screen.PlaylistPage(url))
            url.contains("/channel/") || url.contains("/@") || url.contains("/c/") -> push(Screen.ChannelPage(url))
            else -> push(
                Screen.Watch(
                    Video(url, "", null, "", null, null, 0, -1, null, null, live = false, short = false)
                )
            )
        }
    }

    val top = stack.last()
    DisposableEffect(top) {
        Ui.watching.value = top is Screen.Watch
        onDispose { }
    }

    if (inPip) {
        PlayerSurface(Modifier.fillMaxSize(), controls = false)
        return
    }
    if (fullscreen && top is Screen.Watch) {
        BackHandler { activity.setFullscreen(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(Modifier.fillMaxSize(), onFullscreen = { activity.setFullscreen(false) })
        }
        return
    }

    BackHandler(enabled = stack.size > 1) { nav.back() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when (top) {
                    Screen.Home -> HomeScreen(nav)
                    Screen.Library -> LibraryScreen(
                        nav,
                        openLiked = { push(Screen.Liked) },
                        openPlaylists = { push(Screen.MyPlaylists) },
                        say = say,
                    )
                    Screen.Shorts -> ShortsScreen(nav)
                    Screen.Liked -> AccountListScreen("Liked videos", nav, load = { GoogleAccount.liked() }, openPlaylist = {})
                    Screen.MyPlaylists -> AccountListScreen(
                        "Your playlists", nav, load = { GoogleAccount.playlists() },
                        openPlaylist = { p -> push(Screen.AccountPlaylist(p.url, p.title)) },
                    )
                    is Screen.AccountPlaylist -> AccountListScreen(top.title, nav, load = { GoogleAccount.playlistVideos(top.url) }, openPlaylist = {})
                    Screen.Search -> SearchScreen(nav)
                    is Screen.Watch -> VideoScreen(top.video, nav, onFullscreen = { activity.setFullscreen(true) })
                    is Screen.ChannelPage -> ChannelScreen(top.url, nav)
                    is Screen.PlaylistPage -> PlaylistScreen(top.url, nav)
                }
                androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }
            val playing = current
            if (playing != null && top !is Screen.Watch && top != Screen.Shorts && !playing.short) {
                MiniPlayer(playing, onOpen = { push(Screen.Watch(playing)) }, onClose = { PlayerHolder.stop() })
            }
            if (top == Screen.Home || top == Screen.Library || top == Screen.Shorts) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.height(64.dp)) {
                    NavigationBarItem(
                        selected = top == Screen.Home,
                        onClick = { stack.clear(); stack.add(Screen.Home); tab = Screen.Home },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = top == Screen.Shorts,
                        onClick = { stack.clear(); stack.add(Screen.Shorts); tab = Screen.Shorts },
                        icon = { Text("\u26A1", style = MaterialTheme.typography.titleMedium) },
                        label = { Text("Shorts") },
                    )
                    NavigationBarItem(
                        selected = top == Screen.Library,
                        onClick = { stack.clear(); stack.add(Screen.Library); tab = Screen.Library },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        label = { Text("Library") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(v: Video, onOpen: () -> Unit, onClose: () -> Unit) {
    var isPlaying by remember { mutableStateOf(PlayerHolder.player.isPlaying) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        PlayerHolder.player.addListener(l)
        onDispose { PlayerHolder.player.removeListener(l) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(v.thumbnail, 0, false, Modifier.width(96.dp), corner = 8)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(v.channel, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { val p = PlayerHolder.player; if (p.isPlaying) p.pause() else p.play() }) {
            Text(if (isPlaying) "⏸" else "▶", style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
    }
}

@Suppress("unused")
private fun Uri.isVideoLink() = host?.contains("youtu") == true
