package app.vidflow.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Library
import app.vidflow.core.Paged
import app.vidflow.core.SeenShorts
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import app.vidflow.player.PlayerHolder
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Full-screen vertical videos: swipe up for the next one. Each plays on repeat.
 * The default feed is endless and skips shorts you've already seen.
 */
@Composable
fun ShortsScreen(
    nav: Nav,
    key: String = "shorts-home",
    startIndex: Int = 0,
    initial: List<Video>? = null,
    load: () -> Paged = { Yt.shortsFeed(Library.subscriptions.value.map { it.url }) },
) {
    var generation by remember(key) { mutableIntStateOf(0) }
    val list = remember(key, generation) { PagedList() }
    val scope = rememberCoroutineScope()
    var error by remember(key, generation) { mutableStateOf<String?>(null) }

    fun reload() {
        error = null
        list.loading = true
        scope.launch {
            try {
                list.set(withContext(Dispatchers.IO) { load() })
                if (list.items.isEmpty()) error = "No shorts found right now."
            } catch (t: Throwable) {
                error = friendlyError(t)
            } finally {
                list.loading = false
            }
        }
    }
    LaunchedEffect(key, generation) {
        if (list.items.isEmpty()) {
            if (initial != null && generation == 0) {
                list.set(Paged(initial, null))
                // Keep going with the endless feed after the ones you tapped.
                list.next = { withContext(Dispatchers.IO) { load() } }
            } else {
                reload()
            }
        }
    }

    // Leaving Shorts stops the looping short.
    DisposableEffect(Unit) {
        onDispose { if (PlayerHolder.current.value?.short == true) PlayerHolder.stop() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val videos = list.items.filterIsInstance<Video>()
        val err = error
        when {
            videos.isEmpty() && err != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(err, color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { reload() }) { Text("Try again") }
            }
            videos.isEmpty() -> ShortsSkeleton()
            else -> {
                val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, videos.lastIndex)) { videos.size }
                LaunchedEffect(pager.settledPage, videos.size) {
                    // Load more well before the end, and warm up the next two.
                    if (pager.settledPage >= videos.size - 4 && list.next != null) list.loadMore()
                    for (k in 1..2) {
                        val v = videos.getOrNull(pager.settledPage + k) ?: break
                        launch(Dispatchers.IO) { runCatching { Yt.stream(v.url) } }
                    }
                }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    key = { videos.getOrNull(it)?.url ?: it },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ShortPage(videos[page], active = page == pager.settledPage, nav = nav)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shorts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { generation++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "New shorts", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ShortsSkeleton() {
    Box(Modifier.fillMaxSize().shimmer(dark = true)) {
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(180.dp, 14.dp).background(Color(0x33FFFFFF), RoundedCornerShape(6.dp)))
            Box(Modifier.size(260.dp, 14.dp).background(Color(0x33FFFFFF), RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun ShortPage(v: Video, active: Boolean, nav: Nav) {
    val context = LocalContext.current
    val current by PlayerHolder.current.collectAsState()
    val playError by PlayerHolder.error.collectAsState()
    val subs by Library.subscriptions.collectAsState()
    var info by remember(v.url) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(v.url) { mutableStateOf<String?>(null) }
    var paused by remember(v.url) { mutableStateOf(false) }
    var progress by remember(v.url) { mutableFloatStateOf(0f) }

    LaunchedEffect(active, v.url) {
        if (!active) return@LaunchedEffect
        paused = false
        SeenShorts.mark(v.url)
        try {
            val i = info ?: withContext(Dispatchers.IO) { Yt.stream(v.url) }.also { info = it }
            val full = v.copy(
                title = i.name ?: v.title,
                channel = i.uploaderName ?: v.channel,
                channelUrl = i.uploaderUrl ?: v.channelUrl,
                channelAvatar = i.uploaderAvatars.best(200) ?: v.channelAvatar,
                short = true,
            )
            PlayerHolder.play(full, i, height = 720, resumeAt = 0, loop = true)
            launch(Dispatchers.Default) { Yt.addSuggestions(i) }
        } catch (t: Throwable) {
            error = friendlyError(t)
        }
    }

    val playingThis = active && current?.url == v.url
    LaunchedEffect(playingThis) {
        while (playingThis) {
            val p = PlayerHolder.player
            val d = p.duration
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(200)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!playingThis) return@clickable
                val p = PlayerHolder.player
                if (p.isPlaying) p.pause() else p.play()
                paused = !p.playWhenReady
            }
    ) {
        AsyncImage(
            model = v.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (playingThis) PlayerSurface(Modifier.fillMaxSize(), controls = false, fit = true)
        when {
            error != null || (playingThis && playError != null) -> Text(
                error ?: playError ?: "",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            active && !playingThis -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(40.dp))
        }
        AnimatedVisibility(paused, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier.size(72.dp).background(Color(0x66000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 30.sp) }
        }

        val channelUrl = info?.uploaderUrl ?: v.channelUrl
        val subscribed = remember(subs, channelUrl) { Library.isSubscribed(channelUrl) }

        // Actions on the right side.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val likes = info?.likeCount ?: -1
            SideAction("♥", if (likes >= 0) formatCount(likes) else "Like") {}
            SideAction(null, "Share", icon = { Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White) }) {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url), "Share")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Box(Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) }) {
                Avatar(info?.uploaderAvatars?.best(200) ?: v.channelAvatar, 40)
            }
        }

        // Title, channel and subscribe at the bottom.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(start = 16.dp, end = 72.dp, top = 48.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(info?.uploaderAvatars?.best(200) ?: v.channelAvatar, 30)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        info?.uploaderName ?: v.channel,
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (channelUrl != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (subscribed) "Subscribed" else "Subscribe",
                        color = if (subscribed) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (subscribed) Color(0x55FFFFFF) else Color.White, RoundedCornerShape(16.dp))
                            .clickable {
                                Library.toggle(channelUrl, info?.uploaderName ?: v.channel, info?.uploaderAvatars?.best(200) ?: v.channelAvatar)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Text(info?.name ?: v.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val views = info?.viewCount ?: v.views
            if (views >= 0) Text("${formatCount(views)} views", color = Color(0xCCFFFFFF), fontSize = 12.sp)
        }

        if (playingThis) {
            LinearProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
            )
        }
    }
}

@Composable
private fun SideAction(glyph: String?, label: String, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(46.dp).background(Color(0x40000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) icon() else Text(glyph ?: "", color = Color.White, fontSize = 22.sp)
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
