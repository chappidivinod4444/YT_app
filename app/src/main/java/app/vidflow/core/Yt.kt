package app.vidflow.core

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ------------------------------------------------------------------ models

sealed interface Item {
    val url: String
    val title: String
    val thumbnail: String?
}

data class Video(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val channel: String,
    val channelUrl: String?,
    val channelAvatar: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val uploadedAtMillis: Long?,
    val live: Boolean,
    val short: Boolean,
) : Item

data class Channel(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val subscribers: Long,
    val videos: Long,
) : Item

data class Playlist(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val owner: String,
    val count: Long,
) : Item

/** A page of results plus a handle to load more. */
class Paged(val items: List<Item>, val next: (suspend () -> Paged)?)

// ---------------------------------------------------------------- helpers

fun List<Image>.best(maxHeight: Int = 720): String? {
    if (isEmpty()) return null
    val known = filter { it.height > 0 }
    if (known.isEmpty()) return last().url
    return (known.filter { it.height <= maxHeight }.maxByOrNull { it.height } ?: known.minByOrNull { it.height })?.url
}

fun InfoItem.toItem(): Item? = when (this) {
    is StreamInfoItem -> Video(
        url = url,
        title = name,
        thumbnail = thumbnails.best(),
        channel = uploaderName ?: "",
        channelUrl = uploaderUrl,
        channelAvatar = uploaderAvatars.best(200),
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        uploadedAtMillis = try {
            uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        } catch (_: Throwable) {
            null
        },
        live = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
        short = try {
            isShortFormContent
        } catch (_: Throwable) {
            false
        },
    )
    is ChannelInfoItem -> Channel(url, name, thumbnails.best(200), subscriberCount, streamCount)
    is PlaylistInfoItem -> Playlist(url, name, thumbnails.best(), uploaderName ?: "", streamCount)
    else -> null
}

private fun List<InfoItem>.items(): List<Item> = mapNotNull { runCatching { it.toItem() }.getOrNull() }

fun friendlyError(t: Throwable): String {
    var e: Throwable? = t
    while (e != null) {
        when (e) {
            is SignInConfirmNotBotException ->
                return "YouTube asked to confirm you're not a bot. Wait a few minutes or switch between Wi-Fi and mobile data."
            is ReCaptchaException -> return "YouTube is limiting requests right now. Try again in a little while."
            is AgeRestrictedContentException -> return "This video is age-restricted and needs a signed-in account."
            is GeographicRestrictionException -> return "This video isn't available in your country."
            is ContentNotAvailableException -> return e.message?.takeIf { it.isNotBlank() } ?: "This content isn't available."
            is IOException -> return "Network problem. Check your connection."
        }
        e = e.cause
    }
    return t.message ?: "Something went wrong"
}

// --------------------------------------------------------------------- api

/** All calls block on the network: call them from Dispatchers.IO. */
object Yt {
    val service: StreamingService get() = ServiceList.YouTube
    private val streamCache = ConcurrentHashMap<String, Pair<Long, StreamInfo>>()

    fun init() {
        val locale = Locale.getDefault()
        NewPipe.init(
            HttpDownloader(),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifEmpty { "US" }),
        )
    }

    // ---- home

    fun kiosks(): List<String> = try {
        service.kioskList.availableKiosks.toList().sortedBy { if (it.contains("music", true)) 0 else 1 }
    } catch (_: Throwable) {
        emptyList()
    }

    fun kioskName(id: String): String = id.replace("_", " ").replace("trending", "Trending").trim()
        .replaceFirstChar { it.uppercase() }.let { if (it.equals("live", true)) "Live now" else it }

    fun kiosk(id: String): Paged {
        val ex = service.kioskList.getExtractorById(id, null)
        ex.fetchPage()
        val first = ex.initialPage
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val next = ex.getPage(pg)
                Paged(next.items.items(), page(next.nextPage))
            }
        }
        return Paged(first.items.items(), page(first.nextPage))
    }

    // ---- search

    fun suggestions(query: String): List<String> = try {
        service.suggestionExtractor.suggestionList(query).take(8)
    } catch (_: Throwable) {
        emptyList()
    }

    fun search(query: String, filter: String? = null): Paged {
        val handler: SearchQueryHandler = if (filter == null) service.searchQHFactory.fromQuery(query)
        else service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val more = SearchInfo.getMoreItems(service, handler, pg)
                Paged(more.items.items(), page(more.nextPage))
            }
        }
        return Paged(info.relatedItems.items(), page(info.nextPage))
    }

    // ---- video

    fun stream(url: String, fresh: Boolean = false): StreamInfo {
        val now = System.currentTimeMillis()
        if (!fresh) streamCache[url]?.let { (at, info) -> if (now - at < 2 * 60 * 60 * 1000) return info }
        val info = StreamInfo.getInfo(service, url)
        streamCache[url] = now to info
        return info
    }

    fun related(info: StreamInfo): List<Item> = info.relatedItems.items()

    // ---- channel

    class ChannelPage(val info: ChannelInfo, val videos: Paged) {
        val shortsTab: ListLinkHandler? = info.tabs.firstOrNull { ChannelTabs.SHORTS in it.contentFilters }
    }

    fun channel(url: String): ChannelPage {
        val info = ChannelInfo.getInfo(service, url)
        val tab: ListLinkHandler? = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
            ?: info.tabs.firstOrNull()
        val videos = if (tab != null) tabPage(tab) else Paged(emptyList(), null)
        return ChannelPage(info, videos)
    }

    fun channelShorts(page: ChannelPage): Paged =
        page.shortsTab?.let { tabPage(it).let { p -> Paged(p.items.map { i -> if (i is Video) i.copy(short = true) else i }, p.next) } }
            ?: Paged(emptyList(), null)

    private val shortTopics = listOf(
        "#shorts", "#shorts funny", "#shorts comedy", "#shorts music", "#shorts cricket", "#shorts football",
        "#shorts tech", "#shorts cooking", "#shorts food", "#shorts travel", "#shorts gaming", "#shorts motivation",
        "#shorts dance", "#shorts animals", "#shorts science", "#shorts movie", "#shorts cars", "#shorts bike",
        "#shorts art", "#shorts life hacks", "#shorts nature", "#shorts fitness", "#shorts satisfying", "#shorts news",
        "#shorts viral", "#shorts trending", "#shorts song", "#shorts magic", "#shorts diy", "#shorts history",
    )

    /** Shorts suggested by what you watched (filled in while you watch). */
    private val suggested = java.util.concurrent.ConcurrentLinkedQueue<Video>()

    fun isShortLike(v: Video) = v.short || v.durationSec in 1..180

    fun addSuggestions(info: StreamInfo) {
        val more = info.relatedItems.items().filterIsInstance<Video>().filter { isShortLike(it) && !it.live }
        more.shuffled().take(6).forEach { suggested.add(it.copy(short = true)) }
        while (suggested.size > 60) suggested.poll()
    }

    /**
     * An endless, varied feed: your channels' shorts, suggestions from what you watched,
     * and a fresh random mix of topics each time. Shorts you've seen are skipped.
     */
    fun shortsFeed(subscriptionUrls: List<String>): Paged {
        val shown = java.util.Collections.synchronizedSet(HashSet<String>())

        fun batch(includeSubs: Boolean): List<Video> {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(5)
            try {
                val jobs = ArrayList<java.util.concurrent.Future<List<Video>>>()
                if (includeSubs) {
                    subscriptionUrls.shuffled().take(6).forEach { url ->
                        jobs.add(pool.submit<List<Video>> {
                            try {
                                val info = ChannelInfo.getInfo(service, url)
                                val tab = info.tabs.firstOrNull { ChannelTabs.SHORTS in it.contentFilters } ?: return@submit emptyList()
                                ChannelTabInfo.getInfo(service, tab).relatedItems.items().filterIsInstance<Video>().shuffled().take(5)
                            } catch (_: Throwable) {
                                emptyList()
                            }
                        })
                    }
                }
                shortTopics.shuffled().take(3).forEach { topic ->
                    jobs.add(pool.submit<List<Video>> {
                        try {
                            search(topic, "videos").items.filterIsInstance<Video>().filter { isShortLike(it) && !it.live }
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    })
                }
                val lists = jobs.map { runCatching { it.get() }.getOrDefault(emptyList()) }
                val fromSuggestions = generateSequence { suggested.poll() }.take(12).toList()
                // Interleave the sources so one topic doesn't fill the screen.
                val mixed = ArrayList<Video>()
                val all = (lists + listOf(fromSuggestions)).map { it.shuffled().toMutableList() }.filter { it.isNotEmpty() }
                var i = 0
                while (all.any { it.isNotEmpty() }) {
                    val l = all[i % all.size]
                    if (l.isNotEmpty()) mixed.add(l.removeAt(0))
                    i++
                }
                return mixed.map { it.copy(short = true) }
                    .filter { !app.vidflow.core.SeenShorts.has(it.url) && shown.add(it.url) }
            } finally {
                pool.shutdown()
            }
        }

        fun next(round: Int): suspend () -> Paged = {
            var items = batch(includeSubs = round % 3 == 0)
            if (items.isEmpty()) items = batch(includeSubs = false)
            Paged(items, if (round < 50) next(round + 1) else null)
        }

        var first = batch(includeSubs = true)
        if (first.isEmpty()) {
            // Everything was seen already: start over with the history cleared.
            app.vidflow.core.SeenShorts.clear()
            first = batch(includeSubs = true)
        }
        return Paged(first, next(1))
    }

    private fun tabPage(tab: ListLinkHandler): Paged {
        val first = ChannelTabInfo.getInfo(service, tab)
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val more = ChannelTabInfo.getMoreItems(service, tab, pg)
                Paged(more.items.items(), page(more.nextPage))
            }
        }
        return Paged(first.relatedItems.items(), page(first.nextPage))
    }

    /** Newest uploads of one channel, for the subscriptions feed. */
    fun latestUploads(channelUrl: String): List<Video> = try {
        channel(channelUrl).videos.items.filterIsInstance<Video>().take(15)
    } catch (_: Throwable) {
        emptyList()
    }

    // ---- playlist

    class PlaylistPage(val info: PlaylistInfo, val videos: Paged)

    fun playlist(url: String): PlaylistPage {
        val info = PlaylistInfo.getInfo(service, url)
        fun page(p: Page?): (suspend () -> Paged)? = p?.let { pg ->
            {
                val more = PlaylistInfo.getMoreItems(service, url, pg)
                Paged(more.items.items(), page(more.nextPage))
            }
        }
        return PlaylistPage(info, Paged(info.relatedItems.items(), page(info.nextPage)))
    }
}

// ---------------------------------------------------------------- format

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trim(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trim(n / 1_000_000.0) + "M"
    else -> trim(n / 1_000_000_000.0) + "B"
}

private fun trim(d: Double): String = if (d >= 10) d.toLong().toString() else String.format(Locale.US, "%.1f", d).removeSuffix(".0")

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
