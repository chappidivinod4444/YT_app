package app.vidflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.vidflow.core.formatDuration
import app.vidflow.player.PlayerHolder
import kotlinx.coroutines.delay

private val Scrim = Color(0x73000000)

/**
 * The video with Vidflow's own controls on top: tap to show/hide, double-tap a side to skip 10s,
 * swipe down to shrink into the mini player.
 */
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
) {
    val player = PlayerHolder.player
    var visible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var buffered by remember { mutableLongStateOf(player.bufferedPosition) }
    var touchedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var skipLabel by remember { mutableStateOf<String?>(null) }
    var skipLeft by remember { mutableStateOf(false) }
    var skipCount by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val chapters by PlayerHolder.chapters.collectAsState()
    val autoplay by PlayerHolder.autoplay.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val captions by PlayerHolder.captions.collectAsState()

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            buffered = player.bufferedPosition
            // Hide controls after 3 seconds of no touches while playing.
            if (visible && isPlaying && System.currentTimeMillis() - touchedAt > 3000) visible = false
            delay(250)
        }
    }
    LaunchedEffect(skipCount) {
        if (skipLabel != null) {
            delay(700)
            skipLabel = null
        }
    }

    fun touch() {
        touchedAt = System.currentTimeMillis()
    }

    BoxWithConstraints(modifier.background(Color.Black)) {
        val width = constraints.maxWidth
        PlayerSurface(Modifier.fillMaxSize(), controls = false, fit = true)

        // Gestures: taps, double-taps, swipe down.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { visible = !visible; touch() },
                        onDoubleTap = { o ->
                            val left = o.x < width / 2f
                            val delta = if (left) -10_000L else 10_000L
                            player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0)))
                            skipLeft = left
                            skipLabel = if (left) "« 10 seconds" else "10 seconds »"
                            skipCount++
                        },
                    )
                }
                .pointerInput(fullscreen) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 120.dp.toPx()) {
                                if (fullscreen) onFullscreen() else onMinimize()
                            } else if (dragY < -120.dp.toPx() && !fullscreen) {
                                onFullscreen()
                            }
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    ) { _, dy -> dragY += dy }
                }
        )

        // Skip feedback on the side that was double-tapped.
        skipLabel?.let { label ->
            Box(
                Modifier
                    .align(if (skipLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(140.dp)
                    .background(Color(0x26FFFFFF), RoundedCornerShape(if (skipLeft) 0 else 70, if (skipLeft) 70 else 0, if (skipLeft) 70 else 0, if (skipLeft) 0 else 70)),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }

        if (buffering) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Scrim)) {
                // Top row.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconText(if (fullscreen) "✕" else "⌄", 22) { if (fullscreen) onFullscreen() else onMinimize() }
                    Spacer(Modifier.weight(1f))
                    // Autoplay switch.
                    Box(
                        Modifier
                            .clip20()
                            .clickable { PlayerHolder.autoplay.value = !autoplay; touch() }
                            .background(if (autoplay) Color(0x66FFFFFF) else Color(0x33FFFFFF))
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .width(46.dp),
                        contentAlignment = if (autoplay) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(Modifier.size(22.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Text(if (autoplay) "▶" else "⏸", color = Color.Black, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    if (captions.isNotEmpty()) {
                        Text(
                            "CC",
                            color = if (caption != null) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier
                                .background(if (caption != null) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                                .border1()
                                .clickable {
                                    touch()
                                    if (caption != null) PlayerHolder.setCaption(null) else PlayerHolder.setCaption(captions.first().first)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconText("⚙", 22) { settings = true; touch() }
                }

                // Centre: previous / play-pause / next.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton("⏮", 48, enabled = onPrevious != null) { onPrevious?.invoke(); touch() }
                    if (!buffering) {
                        RoundButton(if (isPlaying) "⏸" else "▶", 64) {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            if (player.isPlaying) player.pause() else player.play()
                            touch()
                        }
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }
                    RoundButton("⏭", 48, enabled = onNext != null) { onNext?.invoke(); touch() }
                }

                // Bottom: time, chapter, fullscreen, seek bar.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${formatMs(position)} / ${formatMs(duration)}",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        PlayerHolder.chapterAt(position)?.let { ch ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$ch ›",
                                color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .background(Color(0x66000000), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconText(if (fullscreen) "↙" else "⤢", 22) { onFullscreen(); touch() }
                    }
                    SeekBar(
                        position = position, duration = duration, buffered = buffered,
                        chapters = chapters.map { it.first },
                        onSeek = { player.seekTo(it); position = it; touch() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (fullscreen) 18.dp else 0.dp),
                    )
                }
            }
        }

        // When controls are hidden, keep a thin progress line at the bottom.
        if (!visible && !fullscreen && duration > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(position.toFloat() / duration)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }

    if (settings) PlayerSettingsSheet { settings = false }
}

private fun Modifier.clip20() = clip(RoundedCornerShape(20.dp))

private fun Modifier.border1() = border(1.5.dp, Color.White, RoundedCornerShape(4.dp))

private fun formatMs(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

@Composable
private fun IconText(glyph: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .androidx_clip()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = size.sp) }
}

private fun Modifier.androidx_clip() = clip(CircleShape)

@Composable
private fun RoundButton(glyph: String, size: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .androidx_clip()
            .background(Color(0x59000000))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (enabled) Color.White else Color(0x66FFFFFF), fontSize = (size * 0.45).sp)
    }
}

/** Thin bar with buffered part, chapter gaps and a draggable dot. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    chapters: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val shown = if (dragging) dragFraction else if (duration > 0) position.toFloat() / duration else 0f
    Box(
        modifier
            .height(24.dp)
            .pointerInput(duration) {
                detectTapGestures { o -> if (duration > 0) onSeek((o.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; if (duration > 0) onSeek((dragFraction * duration).toLong()) },
                    onDragCancel = { dragging = false },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height - with(density) { 6.dp.toPx() }
            val h = with(density) { (if (dragging) 5.dp else 3.dp).toPx() }
            drawRect(Color(0x4DFFFFFF), Offset(0f, y - h / 2), Size(size.width, h))
            if (duration > 0) {
                drawRect(Color(0x80FFFFFF), Offset(0f, y - h / 2), Size(size.width * (buffered.toFloat() / duration).coerceIn(0f, 1f), h))
            }
            drawRect(accent, Offset(0f, y - h / 2), Size(size.width * shown, h))
            // Small gaps where chapters start.
            if (duration > 0) for (c in chapters) {
                if (c <= 0) continue
                val x = size.width * c / duration
                drawRect(Color.Black, Offset(x - 1.5f, y - h / 2), Size(3f, h))
            }
            drawCircle(accent, radius = with(density) { (if (dragging) 9.dp else 6.dp).toPx() }, center = Offset(size.width * shown, y))
        }
    }
}

/** Settings sheet: quality, speed, captions, loop, autoplay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(onClose: () -> Unit) {
    var page by remember { mutableStateOf("main") }
    val qualities by PlayerHolder.qualities.collectAsState()
    val quality by PlayerHolder.quality.collectAsState()
    val captions by PlayerHolder.captions.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val looping by PlayerHolder.looping.collectAsState()
    val autoplay by PlayerHolder.autoplay.collectAsState()
    var speed by remember { mutableFloatStateOf(PlayerHolder.player.playbackParameters.speed) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            when (page) {
                "quality" -> {
                    SheetTitle("Quality") { page = "main" }
                    qualities.forEach { h ->
                        SheetRow("${h}p" + if (h >= 1080) "  HD" else "", if (h == quality) "✓" else "") {
                            PlayerHolder.changeQuality(h); onClose()
                        }
                    }
                }
                "speed" -> {
                    SheetTitle("Playback speed") { page = "main" }
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        SheetRow(if (s == 1f) "Normal" else "${s}x", if (s == speed) "✓" else "") {
                            speed = s; PlayerHolder.setSpeed(s); onClose()
                        }
                    }
                }
                "captions" -> {
                    SheetTitle("Captions") { page = "main" }
                    SheetRow("Off", if (caption == null) "✓" else "") { PlayerHolder.setCaption(null); onClose() }
                    captions.forEach { (tag, name) ->
                        SheetRow(name.ifBlank { tag }, if (caption == tag) "✓" else "") { PlayerHolder.setCaption(tag); onClose() }
                    }
                }
                else -> {
                    SheetRow("⚙  Quality", if (quality > 0) "${quality}p ›" else "Auto ›") { page = "quality" }
                    SheetRow("⏩  Playback speed", (if (speed == 1f) "Normal" else "${speed}x") + " ›") { page = "speed" }
                    if (captions.isNotEmpty()) {
                        SheetRow("CC  Captions", (captions.firstOrNull { it.first == caption }?.second ?: "Off") + " ›") { page = "captions" }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow("↻  Loop video", looping) { PlayerHolder.setLoop(it) }
                    SwitchRow("▶  Autoplay next video", autoplay) { PlayerHolder.autoplay.value = it }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", fontSize = 20.sp)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SheetRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
