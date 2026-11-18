package app.vidflow.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Library
import app.vidflow.core.Paged
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import app.vidflow.player.PlayerHolder
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen vertical videos: swipe up for the next one. Each plays on repeat.
 * [load] gives the list; by default your channels' shorts plus popular ones.
 */
@Composable
fun ShortsScreen(
    nav: Nav,
    key: String = "shorts-home",
    startIndex: Int = 0,
    load: () -> Paged = { Yt.shortsFeed(Library.subscriptions.value.map { it.url }) },
) {
    val list = remember(key) { PagedList() }
    val scope = rememberCoroutineScope()
    var error by remember(key) { mutableStateOf<String?>(null) }

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
    LaunchedEffect(key) { if (list.items.isEmpty()) reload() }

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
            videos.isEmpty() -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> {
                val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, videos.lastIndex)) { videos.size }
                LaunchedEffect(pager.settledPage, videos.size) {
                    if (pager.settledPage >= videos.size - 3 && list.next != null) list.loadMore()
                }
                VerticalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                    ShortPage(videos[page], active = page == pager.settledPage, nav = nav)
                }
            }
        }
        Text(
            "Shorts",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ShortPage(v: Video, active: Boolean, nav: Nav) {
    val current by PlayerHolder.current.collectAsState()
    val playError by PlayerHolder.error.collectAsState()
    val subs by Library.subscriptions.collectAsState()
    var info by remember(v.url) { mutableStateOf<org.schabi.newpipe.extractor.stream.StreamInfo?>(null) }
    var error by remember(v.url) { mutableStateOf<String?>(null) }
    var paused by remember(v.url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active, v.url) {
        if (!active) return@LaunchedEffect
        paused = false
        try {
            val i = info ?: withContext(Dispatchers.IO) { Yt.stream(v.url) }.also { info = it }
            val full = v.copy(
                title = i.name ?: v.title,
                channel = i.uploaderName ?: v.channel,
                channelUrl = i.uploaderUrl ?: v.channelUrl,
                channelAvatar = i.uploaderAvatars.best(200) ?: v.channelAvatar,
                short = true,
            )
            PlayerHolder.play(full, i, height = 1080, resumeAt = 0, loop = true)
        } catch (t: Throwable) {
            error = friendlyError(t)
        }
    }

    val playingThis = active && current?.url == v.url
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
            active && !playingThis -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            paused -> Text("▶", color = Color.White, fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
        }

        // Title, channel and subscribe at the bottom.
        val channelUrl = info?.uploaderUrl ?: v.channelUrl
        val subscribed = remember(subs, channelUrl) { Library.isSubscribed(channelUrl) }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(info?.uploaderAvatars?.best(200) ?: v.channelAvatar, 32)
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
                            .background(
                                if (subscribed) Color(0x55FFFFFF) else Color.White,
                                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            )
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
    }
}
