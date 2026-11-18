package app.vidflow.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Reads the subscriptions.csv file from Google Takeout and adds those channels. Returns how many were new. */
object TakeoutImport {
    fun import(csv: String): Int {
        var added = 0
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        for (line in lines.drop(1)) { // first line is the header
            val cols = splitCsv(line)
            if (cols.size < 3) continue
            val id = cols[0].trim()
            if (!id.startsWith("UC")) continue
            val url = "https://www.youtube.com/channel/$id"
            if (!Library.isSubscribed(url)) {
                Library.toggle(url, cols[2].trim(), null)
                added++
            }
        }
        return added
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}

/**
 * Optional Google account features through the official YouTube Data API.
 * Only reads (subscriptions, playlists, likes). Playback never uses the account.
 * The access token comes from Google Play services and is never stored.
 */
object GoogleAccount {
    const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

    val signedIn = MutableStateFlow(false)
    val name = MutableStateFlow<String?>(null)
    val avatar = MutableStateFlow<String?>(null)

    @Volatile
    var token: String? = null

    private lateinit var app: Context
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun init(context: Context) {
        app = context.applicationContext
        val p = prefs()
        signedIn.value = p.getBoolean("signedIn", false)
        name.value = p.getString("name", null)
        avatar.value = p.getString("avatar", null)
    }

    private fun prefs() = app.getSharedPreferences("account", Context.MODE_PRIVATE)

    /** Called after Google gives a token. Blocking: run on Dispatchers.IO. */
    fun onToken(t: String) {
        token = t
        val me = get("channels?part=snippet&mine=true").optJSONArray("items")?.optJSONObject(0)?.optJSONObject("snippet")
        val n = me?.optString("title")?.takeIf { it.isNotBlank() } ?: "Google account"
        val a = me?.optJSONObject("thumbnails")?.let { thumb(it) }
        name.value = n
        avatar.value = a
        signedIn.value = true
        prefs().edit().putBoolean("signedIn", true).putString("name", n).putString("avatar", a).apply()
    }

    /** Blocking. */
    fun signOut() {
        val t = token
        token = null
        signedIn.value = false
        name.value = null
        avatar.value = null
        prefs().edit().clear().apply()
        if (t != null) {
            try {
                http.newCall(
                    okhttp3.Request.Builder().url("https://oauth2.googleapis.com/revoke")
                        .post(FormBody.Builder().add("token", t).build()).build()
                ).execute().close()
            } catch (_: Throwable) {
            }
        }
    }

    class ApiException(message: String, val needsSignIn: Boolean = false) : IOException(message)

    private fun get(path: String): JSONObject {
        val t = token ?: throw ApiException("Please sign in again.", needsSignIn = true)
        val req = okhttp3.Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/$path")
            .header("Authorization", "Bearer $t")
            .build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 401) {
                token = null
                throw ApiException("Your Google sign-in expired. Tap it again.", needsSignIn = true)
            }
            if (!r.isSuccessful) {
                val reason = runCatching {
                    JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).optString("reason")
                }.getOrDefault("")
                throw ApiException(
                    when (reason) {
                        "accessNotConfigured" -> "Turn on \"YouTube Data API v3\" in your Google Cloud project, then try again."
                        "quotaExceeded" -> "Daily limit for your Google project reached. Try again tomorrow."
                        else -> "Google said: HTTP ${r.code} $reason"
                    }
                )
            }
            return JSONObject(body)
        }
    }

    private fun thumb(t: JSONObject): String? =
        listOf("high", "medium", "default").firstNotNullOfOrNull { t.optJSONObject(it)?.optString("url")?.takeIf { u -> u.isNotEmpty() } }

    /** All pages of a list call. */
    private fun all(path: String, max: Int = 1000): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var pageToken: String? = null
        do {
            val o = get(path + "&maxResults=50" + (pageToken?.let { "&pageToken=$it" } ?: ""))
            val items = o.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) out.add(items.getJSONObject(i))
            pageToken = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null && out.size < max)
        return out
    }

    /** Adds your account's subscriptions to Vidflow. Returns how many were new. Blocking. */
    fun importSubscriptions(): Int {
        var added = 0
        for (s in all("subscriptions?part=snippet&mine=true")) {
            val sn = s.optJSONObject("snippet") ?: continue
            val id = sn.optJSONObject("resourceId")?.optString("channelId") ?: continue
            val url = "https://www.youtube.com/channel/$id"
            if (!Library.isSubscribed(url)) {
                Library.toggle(url, sn.optString("title"), sn.optJSONObject("thumbnails")?.let { thumb(it) })
                added++
            }
        }
        return added
    }

    fun playlists(): List<Playlist> = all("playlists?part=snippet,contentDetails&mine=true").map { p ->
        val sn = p.optJSONObject("snippet")
        Playlist(
            url = "https://www.youtube.com/playlist?list=" + p.optString("id"),
            title = sn?.optString("title").orEmpty(),
            thumbnail = sn?.optJSONObject("thumbnails")?.let { thumb(it) },
            owner = name.value ?: "",
            count = p.optJSONObject("contentDetails")?.optLong("itemCount") ?: -1,
        )
    }

    fun liked(): List<Video> = all("videos?part=snippet,contentDetails,statistics&myRating=like", max = 200).map { toVideo(it.optString("id"), it) }

    fun playlistVideos(playlistUrl: String): List<Video> {
        val id = playlistUrl.substringAfter("list=").substringBefore('&')
        return all("playlistItems?part=snippet&playlistId=$id", max = 500).mapNotNull { item ->
            val sn = item.optJSONObject("snippet") ?: return@mapNotNull null
            val vid = sn.optJSONObject("resourceId")?.optString("videoId") ?: return@mapNotNull null
            Video(
                url = "https://www.youtube.com/watch?v=$vid",
                title = sn.optString("title"),
                thumbnail = sn.optJSONObject("thumbnails")?.let { thumb(it) },
                channel = sn.optString("videoOwnerChannelTitle"),
                channelUrl = sn.optString("videoOwnerChannelId").takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/channel/$it" },
                channelAvatar = null, durationSec = 0, views = -1, uploaded = null, uploadedAtMillis = null,
                live = false, short = false,
            )
        }
    }

    private fun toVideo(id: String, o: JSONObject): Video {
        val sn = o.optJSONObject("snippet")
        return Video(
            url = "https://www.youtube.com/watch?v=$id",
            title = sn?.optString("title").orEmpty(),
            thumbnail = sn?.optJSONObject("thumbnails")?.let { thumb(it) },
            channel = sn?.optString("channelTitle").orEmpty(),
            channelUrl = sn?.optString("channelId")?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = null,
            durationSec = isoDuration(o.optJSONObject("contentDetails")?.optString("duration").orEmpty()),
            views = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: -1,
            uploaded = null, uploadedAtMillis = null, live = false, short = false,
        )
    }

    /** "PT1H2M3S" -> seconds. */
    private fun isoDuration(s: String): Long {
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").find(s) ?: return 0
        val (h, mi, se) = m.destructured
        return (h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60 + (se.toLongOrNull() ?: 0)
    }
}
