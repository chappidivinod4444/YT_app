package app.vidflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Channel
import app.vidflow.core.Item
import app.vidflow.core.Paged
import app.vidflow.core.Playlist
import app.vidflow.core.Video
import app.vidflow.core.formatCount
import app.vidflow.core.formatDuration
import app.vidflow.core.friendlyError
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------- theme

val Accent = Color(0xFF7C5CFF)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA996FF),
    onPrimary = Color(0xFF1B0F5C),
    primaryContainer = Color(0xFF3A2A99),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF4DD0C8),
    background = Color(0xFF0D0D12),
    onBackground = Color(0xFFF1F1F5),
    surface = Color(0xFF0D0D12),
    onSurface = Color(0xFFF1F1F5),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFA3A3B3),
    surfaceContainer = Color(0xFF17171F),
    surfaceContainerHigh = Color(0xFF1E1E28),
    surfaceContainerHighest = Color(0xFF272733),
    surfaceContainerLow = Color(0xFF121219),
    outlineVariant = Color(0xFF2A2A35),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0F5C),
    secondary = Color(0xFF00897B),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F4),
    onSurfaceVariant = Color(0xFF5E5E6E),
    surfaceContainer = Color(0xFFF3F3F7),
    surfaceContainerHigh = Color(0xFFEBEBF0),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF8F8FB),
    outlineVariant = Color(0xFFE0E0E6),
)

@Composable
fun VidflowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}

// -------------------------------------------------------------- navigation

/** What the screens can ask the app to open. */
class Nav(
    val video: (Video) -> Unit,
    val channel: (String) -> Unit,
    val playlist: (String) -> Unit,
    val search: () -> Unit,
    val back: () -> Unit,
)

fun Nav.open(item: Item) = when (item) {
    is Video -> video(item)
    is Channel -> channel(item.url)
    is Playlist -> playlist(item.url)
}

// --------------------------------------------------------------- cards

fun videoSubtitle(v: Video, withChannel: Boolean = true): String = listOfNotNull(
    v.channel.takeIf { withChannel && it.isNotBlank() },
    if (v.views >= 0) formatCount(v.views) + if (v.live) " watching" else " views" else null,
    v.uploaded?.takeIf { it.isNotBlank() },
).joinToString(" · ")

@Composable
fun Thumb(url: String?, durationSec: Long, live: Boolean, modifier: Modifier = Modifier, corner: Int = 12) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val label = if (live) "LIVE" else formatDuration(durationSec)
        if (label.isNotEmpty()) {
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(if (live) Color(0xFFE53935) else Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun Avatar(url: String?, size: Int = 36) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** Big card: full-width thumbnail with title below. */
@Composable
fun VideoCard(v: Video, nav: Nav) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { nav.video(v) }
            .padding(bottom = 14.dp)
    ) {
        Thumb(v.thumbnail, v.durationSec, v.live, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Box(Modifier.clickable(enabled = v.channelUrl != null) { v.channelUrl?.let(nav.channel) }) {
                Avatar(v.channelAvatar)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(videoSubtitle(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Compact row: small thumbnail on the left. */
@Composable
fun VideoRow(v: Video, nav: Nav, withChannel: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.video(v) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Thumb(v.thumbnail, v.durationSec, v.live, Modifier.width(160.dp), corner = 10)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(videoSubtitle(v, withChannel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ChannelRow(c: Channel, nav: Nav) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.channel(c.url) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.thumbnail, 64)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (c.subscribers >= 0) formatCount(c.subscribers) + " subscribers" else null,
                    if (c.videos > 0) "${formatCount(c.videos)} videos" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlaylistRow(p: Playlist, nav: Nav) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.playlist(p.url) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(Modifier.width(160.dp)) {
            Thumb(p.thumbnail, 0, false, Modifier.fillMaxWidth(), corner = 10)
            Text(
                "▶ ${if (p.count >= 0) p.count else ""}",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist · ${p.owner}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ItemView(item: Item, nav: Nav, bigVideos: Boolean) {
    when (item) {
        is Video -> if (bigVideos) VideoCard(item, nav) else VideoRow(item, nav)
        is Channel -> ChannelRow(item, nav)
        is Playlist -> PlaylistRow(item, nav)
    }
}

// --------------------------------------------------------- loading states

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("⚠", fontSize = 32.sp)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** Holds a growing list that loads more when you reach the end. */
class PagedList {
    val items = mutableStateListOf<Item>()
    var next: (suspend () -> Paged)? by mutableStateOf(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun set(p: Paged) {
        items.clear()
        items.addAll(p.items)
        next = p.next
    }

    suspend fun loadMore() {
        val n = next ?: return
        if (loading) return
        loading = true
        try {
            val p = withContext(Dispatchers.IO) { n() }
            val seen = items.map { it.url }.toHashSet()
            items.addAll(p.items.filter { it.url !in seen })
            next = p.next
        } catch (t: Throwable) {
            next = null
        } finally {
            loading = false
        }
    }
}

/** A scrolling list that loads the first page with [load] and more pages automatically. */
@Composable
fun PagedColumn(
    key: Any,
    nav: Nav,
    load: () -> Paged,
    bigVideos: Boolean = true,
    header: LazyListScope.() -> Unit = {},
) {
    val list = remember(key) { PagedList() }
    val scope = rememberCoroutineScope()
    var first by remember(key) { mutableStateOf(true) }
    val state = rememberLazyListState()

    fun reload() {
        list.error = null
        list.loading = true
        scope.launch {
            try {
                list.set(withContext(Dispatchers.IO) { load() })
            } catch (t: Throwable) {
                list.error = friendlyError(t)
            } finally {
                list.loading = false
                first = false
            }
        }
    }

    LaunchedEffect(key) { if (list.items.isEmpty()) reload() }

    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, list.items.size) {
        if (nearEnd && list.next != null && !list.loading) list.loadMore()
    }

    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        header()
        val err = list.error
        when {
            err != null && list.items.isEmpty() -> item { ErrorBox(err) { reload() } }
            list.items.isEmpty() && (list.loading || first) -> item { Loading() }
            list.items.isEmpty() -> item {
                Text(
                    "Nothing here yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(list.items, key = { it.url }) { ItemView(it, nav, bigVideos) }
        if (list.loading && list.items.isNotEmpty()) item { Loading() }
    }
}
