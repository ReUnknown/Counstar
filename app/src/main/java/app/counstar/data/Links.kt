package app.counstar.data

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.WebApi
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** YouTube links opened in Counstar, or shared to it from another app. */
object Links {
    /** A link waiting to be opened, once you're signed in and the app is showing. */
    var pending by mutableStateOf<String?>(null)

    sealed interface Target {
        data class Video(val id: String, val startMs: Long) : Target
        data class Channel(val id: String) : Target
    }

    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    /** The first YouTube link in [text] (shared text often has the video's title before it). */
    fun find(text: String): String? = Regex("""https?://\S+""").findAll(text)
        .map { it.value.trimEnd('.', ',', ')', '"', '\'') }
        .firstOrNull { isYouTube(Uri.parse(it).host) }

    private fun isYouTube(host: String?): Boolean =
        host != null && (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com"))

    /** What [link] points to: read from the link itself, or asked of YouTube (@handles and the like). */
    suspend fun resolve(link: String): Target? {
        val uri = Uri.parse(link)
        val path = uri.pathSegments
        val id = when {
            uri.host == "youtu.be" -> path.firstOrNull()
            path.firstOrNull() == "watch" -> uri.getQueryParameter("v")
            path.size >= 2 && path[0] in setOf("live", "embed", "shorts", "v") -> path[1]
            else -> null
        }
        if (id != null) {
            if (!videoId.matches(id)) return null
            return Target.Video(id, startMs(uri.getQueryParameter("t") ?: uri.getQueryParameter("start")))
        }
        if (path.firstOrNull() == "channel") path.getOrNull(1)?.let { return Target.Channel(it) }
        return withContext(Dispatchers.IO) {
            WebApi.post("navigation/resolve_url", JSONObject().put("url", link)).mapCatching { json ->
                val endpoint = JSONObject(json).optJSONObject("endpoint")
                endpoint?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.startsWith("UC") }?.let { Target.Channel(it) }
                    ?: endpoint?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { videoId.matches(it) }?.let { Target.Video(it, 0L) }
            }.getOrNull()
        }
    }

    /** A card's worth of details for a video known only by its ID (what the mini player shows). */
    suspend fun video(id: String): VideoItem {
        val info = withContext(Dispatchers.IO) { YouTube.rawNext(videoId = id).mapCatching(YouTubeParser::watch) }.getOrNull()
        return VideoItem(
            id = id,
            title = info?.title.orEmpty(),
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            channelName = info?.channelName,
            channelId = info?.channelId,
            channelAvatar = info?.channelAvatar,
            views = info?.views,
            published = info?.published,
        )
    }

    /** "90", "90s", "1m30s" or "1h2m3s", as milliseconds. */
    private fun startMs(t: String?): Long {
        if (t.isNullOrBlank()) return 0L
        t.toLongOrNull()?.let { return it * 1000 }
        return Regex("""(\d+)([hms])""").findAll(t).sumOf { m ->
            val n = m.groupValues[1].toLong()
            when (m.groupValues[2]) {
                "h" -> n * 3600
                "m" -> n * 60
                else -> n
            }
        } * 1000
    }
}
