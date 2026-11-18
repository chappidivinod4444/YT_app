package app.vidflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidflow.core.Yt
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First page of comments, cached per video so the preview and the sheet share it. */
private object CommentCache {
    val pages = HashMap<String, Yt.CommentPage>()
}

private suspend fun firstPage(url: String): Yt.CommentPage =
    CommentCache.pages[url] ?: withContext(Dispatchers.IO) { Yt.comments(url) }.also { CommentCache.pages[url] = it }

/** The "Comments" box under the video, showing the top comment. */
@Composable
fun CommentsPreview(url: String, onOpen: () -> Unit) {
    var page by remember(url) { mutableStateOf<Yt.CommentPage?>(CommentCache.pages[url]) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (page == null) page = runCatching { firstPage(url) }.getOrElse { failed = true; null }
    }
    val p = page
    if (failed || p?.disabled == true) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .clickable(enabled = p != null, onClick = onOpen)
            .padding(12.dp)
    ) {
        Text("Comments", fontWeight = FontWeight.Bold)
        val top = p?.comments?.firstOrNull()
        if (top == null) {
            Text(
                if (p == null) "Loading…" else "No comments yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(top.avatar, 24)
                Spacer(Modifier.width(8.dp))
                Text(top.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(url: String, onClose: () -> Unit) {
    val comments = remember(url) { mutableStateListOf<Yt.Comment>() }
    var next by remember(url) { mutableStateOf<(suspend () -> Yt.CommentPage)?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val replies = remember(url) { mutableStateMapOf<Yt.Comment, List<Yt.Comment>>() }
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()

    LaunchedEffect(url) {
        try {
            val p = firstPage(url)
            comments.addAll(p.comments)
            next = p.next
        } catch (t: Throwable) {
            error = friendlyError(t)
        } finally {
            loading = false
        }
    }
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= comments.size - 4 } }
    LaunchedEffect(nearEnd, comments.size) {
        val n = next
        if (nearEnd && n != null && !loading) {
            loading = true
            try {
                val p = withContext(Dispatchers.IO) { n() }
                comments.addAll(p.comments)
                next = p.next
            } catch (_: Throwable) {
                next = null
            } finally {
                loading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            "Comments",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxHeight(0.9f)) {
            error?.let { e -> item { Text(e, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(comments, key = { System.identityHashCode(it) }) { c ->
                CommentRow(c)
                val shown = replies[c]
                if (shown != null) {
                    shown.forEach { r -> Row { Spacer(Modifier.width(44.dp)); CommentRow(r, small = true) } }
                } else if (c.replyCount > 0 && c.replies != null) {
                    Text(
                        "↳ ${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 60.dp, bottom = 8.dp)
                            .clickable {
                                scope.launch {
                                    replies[c] = runCatching {
                                        withContext(Dispatchers.IO) { Yt.replies(url, c.replies).comments }
                                    }.getOrDefault(emptyList())
                                }
                            },
                    )
                }
            }
            if (loading) item { Loading() }
        }
    }
}

@Composable
private fun CommentRow(c: Yt.Comment, small: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Avatar(c.avatar, if (small) 24 else 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(c.author, c.date).joinToString(" · ") + if (c.pinned) "  📌" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                c.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "👍 " + if (c.likes > 0) formatCount(c.likes.toLong()) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c.hearted) Text("  ❤️", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
