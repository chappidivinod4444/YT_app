@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.vidflow.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import app.vidflow.core.Item
import app.vidflow.core.Library
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import app.vidflow.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/** The video surface. Shared by the watch page, full screen and picture-in-picture. */
@Composable
fun PlayerSurface(modifier: Modifier = Modifier, onFullscreen: (() -> Unit)? = null, controls: Boolean = true, fit: Boolean = true) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                player = PlayerHolder.player
                useController = controls
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowSubtitleButton(true)
                keepScreenOn = true
                resizeMode = if (fit) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                if (onFullscreen != null) setFullscreenButtonClickListener { onFullscreen() }
            }
        },
        update = { view ->
            if (view.player !== PlayerHolder.player) view.player = PlayerHolder.player
            view.useController = controls
        },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black),
    )
}

@Composable
fun VideoScreen(video: Video, nav: Nav, onFullscreen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val info by PlayerHolder.info.collectAsState()
    val current by PlayerHolder.current.collectAsState()
    val playError by PlayerHolder.error.collectAsState()
    var loadError by remember(video.url) { mutableStateOf<String?>(null) }
    var loading by remember(video.url) { mutableStateOf(false) }

    fun load(fresh: Boolean = false) {
        loadError = null
        loading = true
        scope.launch {
            try {
                val i = withContext(Dispatchers.IO) { Yt.stream(video.url, fresh) }
                val full = video.copy(
                    title = i.name ?: video.title,
                    channel = i.uploaderName ?: video.channel,
                    channelUrl = i.uploaderUrl ?: video.channelUrl,
                    channelAvatar = i.uploaderAvatars.best(200) ?: video.channelAvatar,
                    thumbnail = i.thumbnails.best() ?: video.thumbnail,
                    durationSec = if (i.duration > 0) i.duration else video.durationSec,
                    views = if (i.viewCount >= 0) i.viewCount else video.views,
                )
                Library.watched(full, Library.position(video.url))
                PlayerHolder.play(full, i)
            } catch (t: Throwable) {
                loadError = friendlyError(t)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(video.url) {
        if (current?.url != video.url || info == null) load()
    }

    val i = info?.takeIf { current?.url == video.url }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (i != null) PlayerSurface(Modifier.fillMaxSize(), onFullscreen = onFullscreen)
            when {
                loading -> CircularProgressIndicator(color = Color.White)
                loadError != null || playError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        loadError ?: playError ?: "",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Button(onClick = { load(fresh = true) }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                }
            }
        }
        val related: List<Item> = remember(i) { i?.let { Yt.related(it) }.orEmpty() }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item { Details(video, i, nav) }
            if (related.isNotEmpty()) {
                item {
                    Text(
                        "Up next", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
            }
            if (i == null && loading) item { SkeletonRows(4) }
            items(related, key = { "r-" + it.url }, contentType = { it::class }) { ItemView(it, nav, bigVideos = false) }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Details(video: Video, i: StreamInfo?, nav: Nav) {
    var expanded by remember(video.url) { mutableStateOf(false) }
    val subs by Library.subscriptions.collectAsState()
    val channelUrl = i?.uploaderUrl ?: video.channelUrl
    val subscribed = remember(subs, channelUrl) { Library.isSubscribed(channelUrl) }
    val qualities by PlayerHolder.qualities.collectAsState()
    val quality by PlayerHolder.quality.collectAsState()
    var qualityMenu by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize()) {
        Text(i?.name ?: video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val meta = listOfNotNull(
            (i?.viewCount ?: video.views).takeIf { it >= 0 }?.let { formatCount(it) + " views" },
            i?.textualUploadDate ?: video.uploaded,
            i?.likeCount?.takeIf { it >= 0 }?.let { formatCount(it) + " likes" },
        ).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(i?.uploaderAvatars?.best(200) ?: video.channelAvatar, 40)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(i?.uploaderName ?: video.channel, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val subsCount = i?.uploaderSubscriberCount ?: -1
                    if (subsCount >= 0) {
                        Text("${formatCount(subsCount)} subscribers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (channelUrl != null) {
                SubscribeButton(subscribed) {
                    Library.toggle(channelUrl, i?.uploaderName ?: video.channel, i?.uploaderAvatars?.best(200) ?: video.channelAvatar)
                }
            }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        var speedMenu by remember { mutableStateOf(false) }
        var speed by remember { mutableStateOf(PlayerHolder.player.playbackParameters.speed) }
        Row(
            Modifier
                .padding(top = 12.dp)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val likes = i?.likeCount ?: -1
            if (likes >= 0) Pill("\u2665  ${formatCount(likes)}") {}
            Pill("\uD83C\uDFA7  Background") { nav.background(video) }
            Pill("\u2197  Share") {
                context.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, video.url),
                        "Share",
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Box {
                Pill(if (speed == 1f) "\u23E9  Speed" else "\u23E9  ${speed}x") { speedMenu = true }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        DropdownMenuItem(
                            text = { Text((if (s == 1f) "Normal" else "${s}x") + if (s == speed) "  \u2713" else "") },
                            onClick = { speedMenu = false; speed = s; PlayerHolder.setSpeed(s) },
                        )
                    }
                }
            }
            if (qualities.isNotEmpty()) {
                Box {
                    Pill("\u2699  " + if (quality > 0) "${quality}p" else "Quality") { qualityMenu = true }
                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                        qualities.forEach { h ->
                            DropdownMenuItem(
                                text = { Text("${h}p" + if (h == quality) "  ✓" else "") },
                                onClick = { qualityMenu = false; PlayerHolder.changeQuality(h) },
                            )
                        }
                    }
                }
            }
        }

        val description = i?.description?.content.orEmpty()
            .replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        if (description.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(12.dp)
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (expanded) "Show less" else "More", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (!expanded) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
    }
}
