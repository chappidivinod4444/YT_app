@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vidflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Feed
import app.vidflow.core.Library
import app.vidflow.core.Paged
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -------------------------------------------------------------------- top

@Composable
fun AppBar(title: String, nav: Nav, showBack: Boolean = false, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        } else {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF4DD0C8)))),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 12.sp) }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        IconButton(onClick = nav.search) { Icon(Icons.Filled.Search, contentDescription = "Search") }
    }
}

// ------------------------------------------------------------------- home

@Composable
fun HomeScreen(nav: Nav) {
    val subs by Library.subscriptions.collectAsState()
    val kiosks = remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { kiosks.value = withContext(Dispatchers.IO) { Yt.kiosks() } }
    var tab by rememberSaveable { mutableStateOf(if (Library.subscriptions.value.isNotEmpty()) "subs" else "") }
    val tabs = buildList {
        if (subs.isNotEmpty()) add("subs")
        addAll(kiosks.value)
    }
    if (tab.isEmpty() && tabs.isNotEmpty()) tab = tabs.first()

    // A few shorts for the shelf on Home.
    var shelf by remember { mutableStateOf<List<Video>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (shelf.isEmpty()) shelf = withContext(Dispatchers.IO) {
            runCatching { Yt.shortsFeed(Library.subscriptions.value.map { it.url }).items.filterIsInstance<Video>().take(12) }.getOrDefault(emptyList())
        }
    }

    val header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item(key = "appbar", contentType = "appbar") { AppBar("Vidflow", nav) }
        stickyHeader(key = "chips", contentType = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 8.dp),
            ) {
                items(tabs) { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(if (t == "subs") "Subscriptions" else Yt.kioskName(t)) },
                    )
                }
            }
        }
    }

    when {
        tab == "subs" -> SubscriptionFeed(nav, header)
        tab.isNotEmpty() -> PagedColumn(
            key = "kiosk-$tab", nav = nav, load = { Yt.kiosk(tab) }, header = header,
            insert = { ShortsShelf(shelf, nav) },
        )
        else -> Column(Modifier.fillMaxSize()) {
            AppBar("Vidflow", nav)
            SkeletonCards()
        }
    }
}

@Composable
private fun SearchPrompt(nav: Nav) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Loading()
        TextButton(onClick = nav.search) { Text("Search for something to watch") }
    }
}

@Composable
private fun SubscriptionFeed(nav: Nav, header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val videos by Feed.videos.collectAsState()
    val loading by Feed.loading.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (videos == null || Feed.stale()) withContext(Dispatchers.IO) { Feed.refresh() }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        header()
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Latest from your channels", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(enabled = !loading, onClick = { scope.launch(Dispatchers.IO) { Feed.refresh() } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
        val list = videos
        when {
            list == null || (loading && list.isEmpty()) -> item { SkeletonCards() }
            list.isEmpty() -> item {
                Text(
                    "No new videos. Subscribe to channels to see their uploads here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            else -> items(list, key = { it.url }, contentType = { "video" }) { VideoCard(it, nav) }
        }
    }
}

// ----------------------------------------------------------------- search

@Composable
fun SearchScreen(nav: Nav) {
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    LaunchedEffect(text) {
        if (text.isBlank() || text == query) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = withContext(Dispatchers.IO) { Yt.suggestions(text) }
    }

    fun go(q: String) {
        if (q.isBlank()) return
        text = q
        query = q.trim()
        suggestions = emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Search videos and channels") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go(text) }),
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = ""; runCatching { focus.requestFocus() } }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (suggestions.isNotEmpty()) {
            LazyColumn {
                items(suggestions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { go(s) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(s)
                    }
                }
            }
        } else if (query.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(null to "All", "videos" to "Videos", "channels" to "Channels", "playlists" to "Playlists").forEach { (f, label) ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) })
                }
            }
            val q = query
            val f = filter
            PagedColumn(key = "search-$q-$f", nav = nav, load = { Yt.search(q, f) }, bigVideos = false)
        }
    }
}

// ---------------------------------------------------------------- channel

@Composable
fun ChannelScreen(url: String, nav: Nav) {
    var page by remember(url) { mutableStateOf<Yt.ChannelPage?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val subs by Library.subscriptions.collectAsState()
    val scope = rememberCoroutineScope()

    fun load() {
        error = null
        scope.launch {
            try {
                page = withContext(Dispatchers.IO) { Yt.channel(url) }
            } catch (t: Throwable) {
                error = friendlyError(t)
            }
        }
    }
    LaunchedEffect(url) { load() }

    Column(Modifier.fillMaxSize()) {
        AppBar(page?.info?.name ?: "", nav, showBack = true)
        val p = page
        val err = error
        when {
            err != null -> ErrorBox(err) { load() }
            p == null -> Loading()
            else -> {
                val info = p.info
                val subscribed = remember(subs) { Library.isSubscribed(url) }
                var showShorts by remember(url) { mutableStateOf(false) }
                if (showShorts) {
                    BackHandlerCompat { showShorts = false }
                    ShortsScreen(nav, key = "channel-shorts-$url", load = { Yt.channelShorts(p) })
                    return@Column
                }
                PagedColumn(key = "channel-$url", nav = nav, load = { p.videos }, bigVideos = false) {
                    item {
                        Column {
                            info.banners.best(400)?.let {
                                AsyncImage(
                                    model = it, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(6f).padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            }
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(info.avatars.best(300), 72)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(info.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (info.subscriberCount >= 0) {
                                        Text("${formatCount(info.subscriberCount)} subscribers", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            val about = info.description.orEmpty()
                            if (about.isNotBlank()) {
                                Text(
                                    about, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            SubscribeButton(subscribed, Modifier.fillMaxWidth().padding(16.dp)) {
                                Library.toggle(url, info.name, info.avatars.best(200))
                            }
                            if (p.shortsTab != null) {
                                OutlinedButton(
                                    onClick = { showShorts = true },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                ) { Text("Watch Shorts") }
                            }
                            Text(
                                "Videos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackSettings() {
    val mode by app.vidflow.core.Prefs.leaveMode.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("When you leave the app during a video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        app.vidflow.core.LeaveMode.entries.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { app.vidflow.core.Prefs.setLeaveMode(m) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = mode == m, onClick = { app.vidflow.core.Prefs.setLeaveMode(m) })
                Text(m.label)
            }
        }
    }
}

@Composable
fun BackHandlerCompat(onBack: () -> Unit) = androidx.activity.compose.BackHandler(onBack = onBack)

@Composable
fun SubscribeButton(subscribed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (subscribed) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text("Subscribed ✓") }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text("Subscribe") }
    }
}

// --------------------------------------------------------------- playlist

@Composable
fun PlaylistScreen(url: String, nav: Nav) {
    var page by remember(url) { mutableStateOf<Yt.PlaylistPage?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() {
        error = null
        scope.launch {
            try {
                page = withContext(Dispatchers.IO) { Yt.playlist(url) }
            } catch (t: Throwable) {
                error = friendlyError(t)
            }
        }
    }
    LaunchedEffect(url) { load() }
    Column(Modifier.fillMaxSize()) {
        AppBar(page?.info?.name ?: "Playlist", nav, showBack = true)
        val p = page
        val err = error
        when {
            err != null -> ErrorBox(err) { load() }
            p == null -> Loading()
            else -> PagedColumn(key = "playlist-$url", nav = nav, load = { p.videos }, bigVideos = false) {
                item {
                    Text(
                        "${p.info.uploaderName ?: ""} · ${if (p.info.streamCount >= 0) "${p.info.streamCount} videos" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- library

@Composable
fun LibraryScreen(nav: Nav, openLiked: () -> Unit = {}, openPlaylists: () -> Unit = {}, say: (String) -> Unit = {}) {
    val subs by Library.subscriptions.collectAsState()
    val history by Library.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        item { AppBar("Library", nav) }
        item { AccountSection(nav, openLiked, openPlaylists, say) }
        item {
            Text("Subscriptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
        }
        if (subs.isEmpty()) {
            item {
                Text(
                    "Open a channel and tap Subscribe. Subscriptions are saved on this phone only, no account needed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(subs, key = { it.url }) { s ->
                        Column(
                            Modifier
                                .width(72.dp)
                                .clickable { nav.channel(s.url) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(s.avatar, 60)
                            Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            item {
                Text("Videos you watch show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        items(history, key = { "h-" + it.video.url }) { w -> VideoRow(w.video, nav) }
        item { PlaybackSettings() }
        item {
            Text(
                "Vidflow has no account and no ads. Your subscriptions and history never leave this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?") },
            confirmButton = { TextButton(onClick = { Library.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// Placeholder used while unavailable data loads.
@Suppress("unused")
private fun emptyPaged() = Paged(emptyList<Video>(), null)
