package app.counstar.data

import org.json.JSONArray
import org.json.JSONObject

/** A video as shown in a feed: everything a card needs. */
data class VideoItem(
    val id: String,
    val title: String,
    val thumbnail: String,
    val channelName: String? = null,
    val channelId: String? = null,
    val channelAvatar: String? = null,
    val views: String? = null,
    val published: String? = null,
    val duration: String? = null,
    val isLive: Boolean = false,
    /** How much of it you've watched, 0..1, when YouTube says. */
    val watched: Float? = null,
) {
    /** A sharp (1280×720) thumbnail; very old videos lack it, so fall back to [thumbnail]. */
    val hdThumbnail: String get() = "https://i.ytimg.com/vi/$id/hq720.jpg"

    /** "Channel · 1.2M views · 3 days ago", skipping what's missing. */
    val byline: String get() = listOfNotNull(channelName, views, published).joinToString(" · ")
}

/** A filter chip above a feed ("All", "Music", ...): selecting it loads [token]. */
data class Chip(val title: String, val token: String?, val selected: Boolean)

/** One page of a feed, the token for the next page if there is one, and the feed's chips. */
data class FeedPage(val items: List<VideoItem>, val continuation: String?, val chips: List<Chip> = emptyList())

/** One entry in the notifications inbox. */
data class Notification(
    val message: String,
    val time: String?,
    val avatar: String?,
    val thumbnail: String?,
    val videoId: String?,
    val read: Boolean,
)

/** What the watch page shows below the player. */
data class WatchInfo(
    val title: String,
    val views: String?,
    val published: String?,
    val description: String?,
    val channelName: String?,
    val channelId: String?,
    val channelAvatar: String?,
    val subscribers: String?,
    val subscribed: Boolean,
    val likes: String?,
    val liked: Boolean,
    val related: List<VideoItem>,
)

/**
 * Reads YouTube's (WEB client) JSON. YouTube mixes two generations of item formats
 * (videoRenderer and lockupViewModel) and nests them differently on every page, so instead of
 * following exact paths this walks the whole response and picks out every video it finds.
 * Shorts, ads and shelves of other things are skipped.
 */
object YouTubeParser {
    /** Things never to look inside: ads and Shorts. */
    private val skip = setOf(
        "adSlotRenderer", "promotedSparklesWebRenderer", "reelShelfRenderer", "reelItemRenderer",
        "shortsLockupViewModel", "richSectionRenderer", "statementBannerRenderer",
    )

    /** Things whose insides hold nothing more to find (and whose tokens aren't the page's). */
    private val leaves = setOf("videoRenderer", "compactVideoRenderer", "lockupViewModel", "chipCloudChipRenderer", "notificationRenderer")

    fun feed(json: String): FeedPage {
        val root = JSONObject(json)
        val found = LinkedHashMap<String, VideoItem>()
        var continuation: String? = null
        val chips = mutableListOf<Chip>()
        walk(root) { key, value ->
            when (key) {
                "chipCloudChipRenderer" -> (value as? JSONObject)?.let { chip ->
                    val title = text(chip.optJSONObject("text")) ?: return@let
                    chips += Chip(title, findFirst(chip, "continuationCommand")?.optString("token"), chip.optBoolean("isSelected"))
                }
                "videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "playlistVideoRenderer" ->
                    (value as? JSONObject)?.let(::videoRenderer)?.let { found.putIfAbsent(it.id, it) }
                "lockupViewModel" ->
                    (value as? JSONObject)?.let(::lockup)?.let { found.putIfAbsent(it.id, it) }
                "continuationCommand" ->
                    (value as? JSONObject)?.optString("token")?.takeIf { it.isNotBlank() }?.let { continuation = it }
            }
        }
        return FeedPage(found.values.toList(), continuation, chips)
    }

    fun watch(json: String): WatchInfo {
        val root = JSONObject(json)
        val results = root.optJSONObject("contents")?.optJSONObject("twoColumnWatchNextResults")
        val primary = findFirst(results?.optJSONObject("results"), "videoPrimaryInfoRenderer")
        val secondary = findFirst(results?.optJSONObject("results"), "videoSecondaryInfoRenderer")
        val owner = secondary?.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")
        val likeButton = findFirst(primary, "likeButtonViewModel")
        val likeLabel = likeButton?.let { findFirst(it, "buttonViewModel") }?.optString("title")?.takeIf { it.isNotBlank() }
        val liked = likeButton?.let { findFirst(it, "toggleButtonViewModel") }?.optBoolean("isToggled") ?: false
        val subscribeButton = findFirst(secondary?.optJSONObject("subscribeButton"), "subscribeButtonRenderer")
        val subscribedState = findFirst(secondary?.optJSONObject("subscribeButton"), "subscribeButtonViewModel")
        val related = results?.optJSONObject("secondaryResults")?.let { feed(it.toString()).items }.orEmpty()
        return WatchInfo(
            title = text(primary?.optJSONObject("title")).orEmpty(),
            views = primary?.optJSONObject("viewCount")?.optJSONObject("videoViewCountRenderer")?.let {
                text(it.optJSONObject("shortViewCount")) ?: text(it.optJSONObject("viewCount"))
            },
            published = text(primary?.optJSONObject("relativeDateText")) ?: text(primary?.optJSONObject("dateText")),
            description = secondary?.optJSONObject("attributedDescription")?.optString("content")?.takeIf { it.isNotBlank() },
            channelName = text(owner?.optJSONObject("title")),
            channelId = owner?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?: findFirst(owner, "browseEndpoint")?.optString("browseId"),
            channelAvatar = bestThumbnail(owner?.optJSONObject("thumbnail")),
            subscribers = text(owner?.optJSONObject("subscriberCountText")),
            subscribed = subscribeButton?.optBoolean("subscribed")
                ?: findFirst(subscribedState, "subscribeButtonViewModel")?.optBoolean("isSubscribed")
                ?: false,
            likes = likeLabel,
            liked = liked,
            related = related,
        )
    }

    fun notifications(json: String): List<Notification> {
        val list = mutableListOf<Notification>()
        walk(JSONObject(json)) { key, value ->
            if (key == "notificationRenderer" && value is JSONObject) {
                list += Notification(
                    message = text(value.optJSONObject("shortMessage")) ?: return@walk,
                    time = text(value.optJSONObject("sentTimeText")),
                    avatar = bestThumbnail(value.optJSONObject("thumbnail")),
                    thumbnail = bestThumbnail(value.optJSONObject("videoThumbnail")),
                    videoId = findFirst(value.optJSONObject("navigationEndpoint"), "watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() },
                    read = value.optBoolean("read"),
                )
            }
        }
        return list
    }

    private fun videoRenderer(v: JSONObject): VideoItem? {
        val id = v.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val byline = v.optJSONObject("ownerText") ?: v.optJSONObject("longBylineText") ?: v.optJSONObject("shortBylineText")
        val badges = v.optJSONArray("badges")?.toString().orEmpty() + v.optJSONArray("thumbnailOverlays")?.toString().orEmpty()
        val isLive = "LIVE" in badges && ("BADGE_STYLE_TYPE_LIVE_NOW" in badges || "\"style\":\"LIVE\"" in badges)
        val resume = findFirst(v.optJSONArray("thumbnailOverlays"), "thumbnailOverlayResumePlaybackRenderer")
        return VideoItem(
            id = id,
            title = text(v.optJSONObject("title")) ?: return null,
            thumbnail = bestThumbnail(v.optJSONObject("thumbnail")) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            channelName = text(byline),
            channelId = byline?.let { findFirst(it, "browseEndpoint") }?.optString("browseId"),
            channelAvatar = v.optJSONObject("channelThumbnailSupportedRenderers")
                ?.let { findFirst(it, "thumbnail") }
                ?.let(::bestThumbnail)
                ?: v.optJSONObject("avatar")?.let { findFirst(it, "image") }?.let(::bestSource),
            views = text(v.optJSONObject("shortViewCountText")) ?: text(v.optJSONObject("viewCountText")),
            published = text(v.optJSONObject("publishedTimeText")),
            duration = text(v.optJSONObject("lengthText")),
            isLive = isLive,
            watched = resume?.optInt("percentDurationWatched", -1)?.takeIf { it >= 0 }?.div(100f),
        )
    }

    private fun lockup(l: JSONObject): VideoItem? {
        if (l.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = l.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val rows = findFirst(meta.optJSONObject("metadata"), "contentMetadataViewModel")?.optJSONArray("metadataRows")
        fun part(row: Int, index: Int): String? = rows?.optJSONObject(row)?.optJSONArray("metadataParts")
            ?.optJSONObject(index)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() }
        val image = l.optJSONObject("contentImage")
        val badge = findFirst(image, "thumbnailBadgeViewModel")
        val isLive = badge?.optString("badgeStyle")?.contains("LIVE") == true
        val progress = findFirst(image, "thumbnailOverlayProgressBarViewModel")
        // Two rows: channel, then views and when. Some lockups put all three on one row.
        val singleRow = (rows?.length() ?: 0) < 2
        return VideoItem(
            id = id,
            title = meta.optJSONObject("title")?.optString("content")?.takeIf { it.isNotBlank() } ?: return null,
            thumbnail = findFirst(image, "image")?.let(::bestSource) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            channelName = part(0, 0),
            channelId = findFirst(meta.optJSONObject("image"), "browseEndpoint")?.optString("browseId"),
            channelAvatar = findFirst(meta.optJSONObject("image"), "image")?.let(::bestSource),
            views = if (singleRow) part(0, 1) else part(1, 0),
            published = if (singleRow) part(0, 2) else part(1, 1),
            duration = badge?.optString("text")?.takeIf { it.isNotBlank() && !isLive },
            isLive = isLive,
            watched = progress?.optInt("startPercent", -1)?.takeIf { it >= 0 }?.div(100f),
        )
    }

    /** simpleText, runs, or (view models) content. */
    private fun text(o: JSONObject?): String? {
        o ?: return null
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        o.optString("content").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty()) }
            .takeIf { it.isNotBlank() }
    }

    /** The largest of {"thumbnails":[...]}. */
    private fun bestThumbnail(o: JSONObject?): String? {
        val list = o?.optJSONArray("thumbnails") ?: return null
        return largest(list)?.let(::fixUrl)
    }

    /** The largest of {"sources":[...]}. */
    private fun bestSource(o: JSONObject): String? = o.optJSONArray("sources")?.let(::largest)?.let(::fixUrl)

    private fun largest(list: JSONArray): String? {
        var best: JSONObject? = null
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i) ?: continue
            if (best == null || t.optInt("width") >= best.optInt("width")) best = t
        }
        return best?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private val avatarSize = Regex("=s\\d+-")

    /** Absolute URLs, and channel avatars asked for at a size that stays sharp. */
    private fun fixUrl(url: String): String {
        val absolute = if (url.startsWith("//")) "https:$url" else url
        return if ("ggpht.com" in absolute || "googleusercontent.com" in absolute) absolute.replace(avatarSize, "=s176-") else absolute
    }

    /** The first object stored under [key], anywhere inside [node]. */
    private fun findFirst(node: Any?, key: String): JSONObject? {
        var result: JSONObject? = null
        walk(node) { k, v -> if (result == null && k == key && v is JSONObject) result = v }
        return result
    }

    private fun walk(node: Any?, visit: (String, Any) -> Unit) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key in skip) continue
                    val value = node.opt(key) ?: continue
                    visit(key, value)
                    // A video's own insides hold nothing more to find.
                    if (key in leaves) continue
                    walk(value, visit)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
