package app.vidflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidflow.core.DownloadService
import app.vidflow.core.Downloads
import app.vidflow.core.LocalPlaylist
import app.vidflow.core.Saved
import app.vidflow.core.UploadsWorker
import app.vidflow.core.Video
import coil3.compose.AsyncImage

// ------------------------------------------------------------------ dialogs

/** "Save to…" sheet, like YouTube's: tap a list to add or remove the video. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistDialog(video: Video, shorts: Boolean = false, onClose: () -> Unit) {
    val lists by Saved.playlists.collectAsState()
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    // Saved Shorts only shows up when saving a short.
    val shown = lists.filter { shorts || it.id != Saved.SAVED_SHORTS }
        .sortedBy { if (it.id == Saved.WATCH_LATER) 0 else if (it.id == Saved.SAVED_SHORTS) 1 else 2 }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF212121),
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Save to…", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                shown.forEach { p ->
                    val inside = p.videos.any { it.url == video.url }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (inside) Saved.removeFrom(p.id, video.url) else Saved.addTo(p.id, video)
                                android.widget.Toast.makeText(
                                    context, if (inside) "Removed from ${p.name}" else "Saved to ${p.name}", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistStackThumb(p.videos.firstOrNull()?.thumbnail)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(
                                "Private" + if (p.videos.isNotEmpty()) " · ${p.videos.size} video${if (p.videos.size == 1) "" else "s"}" else "",
                                fontSize = 13.sp, color = Color(0xFFAAAAAA),
                            )
                        }
                        androidx.compose.material3.Icon(
                            if (inside) androidx.compose.material.icons.Icons.Filled.Bookmark
                            else androidx.compose.material.icons.Icons.Outlined.BookmarkBorder,
                            contentDescription = if (inside) "Saved" else "Save",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x1FFFFFFF))
                    .clickable { creating = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New playlist", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text("Choose a title") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val p = Saved.createPlaylist(name)
                    Saved.addTo(p.id, video)
                    android.widget.Toast.makeText(context, "Saved to ${p.name}", android.widget.Toast.LENGTH_SHORT).show()
                    creating = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

/** A playlist cover with the little "stack" edge above it. */
@Composable
private fun PlaylistStackThumb(url: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(100.dp).height(4.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(Color(0x55FFFFFF))
        )
        Box(
            Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A3A3A)),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) coil3.compose.AsyncImage(
                model = url, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            ) else androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.BookmarkBorder, contentDescription = null, tint = Color(0x99FFFFFF),
            )
        }
    }
}

@Composable
fun DownloadDialog(video: Video, qualities: List<Int>, onClose: () -> Unit) {
    val context = LocalContext.current
    val choices = qualities.filter { it <= 1080 }.ifEmpty { listOf(720, 480, 360) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Download") },
        text = {
            Column {
                Text(
                    "Saved to your phone's Downloads/Vidflow folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                choices.forEach { h ->
                    Text(
                        "▶  Video ${h}p",
                        modifier = Modifier.fillMaxWidth().clickable {
                            DownloadService.start(context, video, h); onClose()
                        }.padding(vertical = 12.dp),
                    )
                }
                Text(
                    "♫  Sound only (M4A)",
                    modifier = Modifier.fillMaxWidth().clickable {
                        DownloadService.start(context, video, 0); onClose()
                    }.padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

// --------------------------------------------------------------- You tab

/** Liked, playlists, downloads and alerts on the You tab. */
@Composable
fun YouSections(nav: Nav, openLocalPlaylist: (String) -> Unit, openLiked: () -> Unit, openSetup: () -> Unit) {
    val context = LocalContext.current
    val liked by Saved.liked.collectAsState()
    val lists by Saved.playlists.collectAsState()
    val jobs by Downloads.jobs.collectAsState()
    var alerts by remember {
        mutableStateOf(context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("uploadAlerts", true))
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PlaylistTile("Liked videos", "${liked.size} videos", liked.firstOrNull()?.thumbnail, openLiked) }
            items(lists, key = { it.id }) { p ->
                PlaylistTile(p.name, "${p.videos.size} videos", p.videos.firstOrNull()?.thumbnail) { openLocalPlaylist(p.id) }
            }
        }
        if (lists.isEmpty()) {
            Text(
                "Tap ➕ Save under a video to make a playlist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (jobs.isNotEmpty()) {
            Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
            jobs.reversed().take(10).forEach { j ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(j.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    if (!j.done && !j.failed) LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Text(
                        j.status, style = MaterialTheme.typography.bodySmall,
                        color = if (j.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("New upload alerts", fontWeight = FontWeight.SemiBold)
                Text(
                    "A notification when your channels post (checked every few hours)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alerts, onCheckedChange = {
                alerts = it
                context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("uploadAlerts", it).apply()
                UploadsWorker.schedule(context, it)
            })
        }
        OutlinedButton(onClick = openSetup, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Set up Google sign-in (step by step)")
        }
    }
}

@Composable
private fun PlaylistTile(title: String, subtitle: String, thumb: String?, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("☰", style = MaterialTheme.typography.headlineMedium)
        }
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A list saved on the phone: liked videos or one of your playlists. */
@Composable
fun LocalListScreen(title: String, videos: List<Video>, nav: Nav, onRemove: ((Video) -> Unit)?, onDeleteList: (() -> Unit)?) {
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        item {
            AppBar(title, nav, showBack = true) {
                if (onDeleteList != null) TextButton(onClick = { confirm = true }) { Text("Delete") }
            }
        }
        if (videos.isEmpty()) item {
            Text("Nothing here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
        }
        items(videos, key = { "l-" + it.url }) { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { VideoRow(v, nav) }
                if (onRemove != null) TextButton(onClick = { onRemove(v) }) { Text("✕") }
            }
        }
    }
    if (confirm && onDeleteList != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete \"$title\"?") },
            confirmButton = { TextButton(onClick = { confirm = false; onDeleteList() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------- Google setup

private const val SHA1 = "F6:DD:C7:FE:6F:92:9B:F8:41:4E:06:85:D2:58:CD:12:61:4C:DC:B0"
private const val PACKAGE = "app.vidflow"
private val SHELL_COMMANDS = """
P=vidflow-${'$'}RANDOM
gcloud projects create ${'$'}P --name=Vidflow && gcloud config set project ${'$'}P && gcloud services enable youtube.googleapis.com && echo "Done. Project: ${'$'}P"
""".trimIndent()

/** Guided, copy-paste setup for "Sign in with Google". */
@Composable
fun GoogleSetupScreen(nav: Nav) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = label
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), modifier = Modifier.fillMaxSize()) {
        item { AppBar("Google sign-in setup", nav, showBack = true) }
        item {
            Text(
                "Optional, one time, about 4 minutes. Google requires every app to be registered by its owner. " +
                    "Everything happens in your own Google account and it's free.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            SetupStep(
                1, "Create the project and turn on the YouTube API (one tap)",
                "Copy the command, open Cloud Shell, paste it and press Enter. It creates a project in your account and switches the API on.",
            ) {
                Button(onClick = { copy("Command", SHELL_COMMANDS) }) { Text("Copy command") }
                OutlinedButton(onClick = { open("https://shell.cloud.google.com/?show=terminal") }) { Text("Open Cloud Shell") }
            }
        }
        item {
            SetupStep(
                2, "Consent screen",
                "Choose External, give it any name (for example Vidflow), your email, then add your own Gmail under Test users and save.",
            ) {
                OutlinedButton(onClick = { open("https://console.cloud.google.com/auth/overview") }) { Text("Open consent screen") }
            }
        }
        item {
            SetupStep(
                3, "Register the Android app",
                "Create client → Android. Paste the package name and SHA-1 below, then Create.",
            ) {
                CopyLine("Package name", PACKAGE) { copy("Package name", PACKAGE) }
                CopyLine("SHA-1", SHA1) { copy("SHA-1", SHA1) }
                OutlinedButton(onClick = { open("https://console.cloud.google.com/auth/clients/create") }) { Text("Open Create client") }
            }
        }
        item {
            SetupStep(
                4, "Sign in",
                "Come back to the You tab and tap Sign in with Google. Google will say the app isn't verified: tap Continue (it's your own app).",
            ) {}
        }
        copied?.let { c ->
            item {
                Text(
                    "✓ $c copied",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupStep(n: Int, title: String, text: String, actions: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { }
        actions()
    }
}

@Composable
private fun CopyLine(label: String, value: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .clickable(onClick = onCopy)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Text("Copy", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Suppress("unused")
private fun LocalPlaylist.cover() = videos.firstOrNull()?.thumbnail
