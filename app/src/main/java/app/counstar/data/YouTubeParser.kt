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
    /** The heading YouTube files it under on a list page ("Today", "Yesterday"), if any. */
    val section: String? = null,
) {
    /** A sharp (1280×720) thumbnail; very old videos lack it, so fall back to [thumbnail]. */
    val hdThumbnail: String get() = "https://i.ytimg.com/vi/$id/hq720.jpg"

    /** "Channel · 1.2M views · 3 days ago", skipping what's missing. */
    val byline: String get() = listOfNotNull(channelName, views, published).joinToString(" · ")

    /** Views the way YouTube's app shows them under a card: "77M" (with a play icon beside it). */
    val shortViews: String? get() = views?.removeSuffix(" views")?.removeSuffix(" view")?.removeSuffix(" watching")?.trim()
}

/** A filter chip above a feed ("All", "Music", ...): selecting it loads [token]. */
data class Chip(val title: String, val token: String?, val selected: Boolean)

/** One page of a feed, the token for the next page if there is one, and the feed's chips. */
data class FeedPage(val items: List<VideoItem>, val continuation: String?, val chips: List<Chip> = emptyList())

data class Comment(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val time: String?,
    val likes: String?,
    val replies: String?,
    val byCreator: Boolean,
    val verified: Boolean = false,
    /** Loads this comment's replies. */
    val repliesToken: String? = null,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    /** What to send to like, unlike, dislike and undislike it (signed in only). */
    val likeAction: String? = null,
    val unlikeAction: String? = null,
    val dislikeAction: String? = null,
    val undislikeAction: String? = null,
    /** The creator hearted it. */
    val hearted: Boolean = false,
    /** Links (@mentions, timestamps) and images (emoji, GIFs) laid over parts of [text]. */
    val runs: List<TextRun> = emptyList(),
    /** What to send to reply to it (signed in only). */
    val replyParams: String? = null,
)

/** Something laid over a stretch of a comment's text, from [start] for [length] characters. */
sealed interface TextRun {
    val start: Int
    val length: Int
}

/** A tappable stretch: an @mention, a link, or a timestamp ([seekSeconds]). */
data class LinkRun(override val start: Int, override val length: Int, val seekSeconds: Int?, val url: String?) : TextRun

/** An image in place of text: a custom emoji (small) or a GIF / sticker (large). */
data class ImageRun(override val start: Int, override val length: Int, val url: String, val width: Int, val height: Int, val label: String?) : TextRun

/** [createParams]: what to send to post a new comment on the video (signed in only). */
data class CommentsPage(val count: String?, val comments: List<Comment>, val continuation: String?, val createParams: String? = null)

/** A chapter of a video, from [startMs]. */
data class Chapter(val title: String, val startMs: Long, val thumbnail: String?)

/** A captions track: [url] fetches its text. */
data class CaptionTrack(val name: String, val language: String, val url: String, val auto: Boolean)

/** One line of captions, on screen from [startMs] to [endMs]. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/** One of your playlists in the "Save to" list, and whether this video is already in it. */
data class PlaylistOption(val id: String, val title: String, val contains: Boolean, val private: Boolean)

/** A playlist (or album, course, podcast) in your library. */
data class Playlist(
    val id: String,
    val title: String,
    val thumbnail: String?,
    /** "Private · Playlist", "Album · Artist". */
    val subtitle: String?,
    /** "24 videos". */
    val count: String?,
    val kind: Kind,
) {
    enum class Kind(val title: String) { Playlist("Playlists"), Music("Music"), Course("Courses"), Podcast("Podcasts") }
}

/** Who's signed in, as YouTube shows it. */
data class Account(val name: String?, val handle: String?, val photo: String?)

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
    /** The exact like count, when YouTube gives it, so liking can update it. */
    val likeCount: Long?,
    val liked: Boolean,
    val disliked: Boolean,
    /** The bell's choices (all / personalized / none) for a channel you're subscribed to. */
    val bellOptions: List<BellOption>,
    /** "Most replayed" intensities across the video, 0..1, or empty. */
    val heatmap: List<Float>,
    /** Loads the first page of comments. */
    val commentsToken: String?,
    /** "2.4M". */
    val commentCount: String?,
    /** Comment orders ("Top", "Newest") and the token that loads each. */
    val commentSorts: List<Chip>,
    val chapters: List<Chapter> = emptyList(),
    /** "49,439,418". */
    val exactViews: String? = null,
    /** "1,123,456", or YouTube's rounded figure when it gives no exact one. */
    val exactLikes: String? = null,
    /** "11 July 2018". */
    val date: String? = null,
    val hashtags: List<String> = emptyList(),
    val related: List<VideoItem>,
)

data class BellOption(val title: String, val params: String, val selected: Boolean, val kind: Kind) {
    enum class Kind { All, Personalized, None }
}

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
    private val leaves = setOf(
        "videoRenderer", "compactVideoRenderer", "lockupViewModel", "chipCloudChipRenderer", "notificationRenderer",
        "sortFilterSubMenuRenderer", "commentRepliesRenderer",
    )

    fun feed(json: String): FeedPage {
        val root = JSONObject(json)
        val found = LinkedHashMap<String, VideoItem>()
        var continuation: String? = null
        val chips = mutableListOf<Chip>()
        walk(root) { key, value ->
            when (key) {
                "chipCloudChipRenderer" -> (value as? JSONObject)?.let { chip ->
                    val title = text(chip.optJSONObject("text")) ?: return@let
                    // A shortcut to YouTube's feed editor, not a filter.
                    if ("custom feed" in title.lowercase() || title == "Shorts") return@let
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
        // History and similar pages file videos under dated headings ("Today", "Yesterday").
        val sections = HashMap<String, String>()
        walk(root) { key, value ->
            if (key != "itemSectionRenderer" || value !is JSONObject) return@walk
            val title = value.optJSONObject("header")?.let { h -> text(findFirst(h, "title")) } ?: return@walk
            walk(value.optJSONArray("contents")) { k, v ->
                if (v !is JSONObject) return@walk
                val id = when (k) {
                    "videoRenderer", "compactVideoRenderer" -> v.optString("videoId")
                    "lockupViewModel" -> v.optString("contentId")
                    else -> null
                }
                if (!id.isNullOrBlank()) sections.putIfAbsent(id, title)
            }
        }
        val items = if (sections.isEmpty()) found.values.toList() else found.values.map { it.copy(section = sections[it.id]) }
        return FeedPage(items, continuation, chips)
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
        val disliked = findFirst(findFirst(primary, "dislikeButtonViewModel"), "toggleButtonViewModel")?.optBoolean("isToggled") ?: false
        val likeCount = likeButton?.toString()?.let { Regex("along with ([\\d,]+)").find(it)?.groupValues?.get(1) }
            ?.replace(",", "")?.toLongOrNull()
        val subscribeButton = findFirst(secondary?.optJSONObject("subscribeButton"), "subscribeButtonRenderer")
        val subscribedState = findFirst(secondary?.optJSONObject("subscribeButton"), "subscribeButtonViewModel")
        val related = results?.optJSONObject("secondaryResults")?.let { feed(it.toString()).items }.orEmpty()
        val commentsPanel = commentsPanel(root)
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
            likeCount = likeCount,
            liked = liked,
            disliked = disliked,
            bellOptions = bellOptions(secondary?.optJSONObject("subscribeButton")),
            heatmap = heatmap(root),
            commentsToken = results?.optJSONObject("results")?.let { r ->
                var token: String? = null
                walk(r) { key, value ->
                    if (token == null && key == "itemSectionRenderer" && value is JSONObject &&
                        value.optString("sectionIdentifier") == "comment-item-section"
                    ) {
                        token = findFirst(value, "continuationCommand")?.optString("token")
                    }
                }
                token
            },
            commentCount = commentsPanel?.let { findFirst(it, "engagementPanelTitleHeaderRenderer") }?.let { text(it.optJSONObject("contextualInfo")) },
            commentSorts = commentsPanel?.let { findFirst(it, "sortFilterSubMenuRenderer") }?.optJSONArray("subMenuItems")?.let { items ->
                (0 until items.length()).mapNotNull { i ->
                    val item = items.optJSONObject(i) ?: return@mapNotNull null
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Chip(title, findFirst(item, "continuationCommand")?.optString("token"), item.optBoolean("selected"))
                }
            }.orEmpty(),
            chapters = chapters(root),
            exactViews = primary?.optJSONObject("viewCount")?.optJSONObject("videoViewCountRenderer")
                ?.let { text(it.optJSONObject("viewCount")) }?.removeSuffix(" views")?.removeSuffix(" view"),
            exactLikes = likeCount?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it) }
                ?: factoid(root, "Likes"),
            date = text(primary?.optJSONObject("dateText"))?.let(::fullDate),
            hashtags = (
                text(primary?.optJSONObject("superTitleLink")).orEmpty() + " " +
                    secondary?.optJSONObject("attributedDescription")?.optString("content").orEmpty()
                ).let { all -> Regex("#[\\p{L}\\p{N}_]+").findAll(all).map { it.value }.distinct().take(12).toList() },
            related = related,
        )
    }

    /** A video's chapters: the player bar's own, or the chapters panel's. */
    private fun chapters(root: JSONObject): List<Chapter> {
        val found = mutableListOf<Chapter>()
        walk(root) { key, value ->
            if (key == "chapterRenderer" && value is JSONObject) {
                val title = text(value.optJSONObject("title")) ?: return@walk
                found += Chapter(title, value.optLong("timeRangeStartMillis"), bestThumbnail(value.optJSONObject("thumbnail")))
            }
        }
        if (found.isEmpty()) {
            walk(root.optJSONArray("engagementPanels")) { key, value ->
                if (key == "macroMarkersListItemRenderer" && value is JSONObject) {
                    val title = text(value.optJSONObject("title")) ?: return@walk
                    val seconds = findFirst(value.optJSONObject("onTap"), "watchEndpoint")?.optLong("startTimeSeconds") ?: return@walk
                    found += Chapter(title, seconds * 1000, bestThumbnail(value.optJSONObject("thumbnail")))
                }
            }
        }
        return found.distinctBy { it.startMs }.sortedBy { it.startMs }.takeIf { it.size > 1 }.orEmpty()
    }

    /** A figure from the description's header ("Likes", "Views"). */
    private fun factoid(root: JSONObject, label: String): String? {
        var found: String? = null
        walk(root.optJSONArray("engagementPanels")) { key, value ->
            if (found == null && key == "factoidRenderer" && value is JSONObject && text(value.optJSONObject("label")) == label) {
                found = text(value.optJSONObject("value"))
            }
        }
        return found
    }

    /** "Premiered Jul 11, 2018" → "11 July 2018". */
    private fun fullDate(raw: String): String {
        val date = Regex("[A-Z][a-z]{2} \\d{1,2}, \\d{4}").find(raw)?.value ?: return raw
        return runCatching {
            val parsed = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).parse(date)!!
            java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.US).format(parsed)
        }.getOrDefault(raw)
    }

    /** The comments engagement panel of a watch page: its count and sort menu. */
    private fun commentsPanel(root: JSONObject): JSONObject? {
        val panels = root.optJSONArray("engagementPanels") ?: return null
        for (i in 0 until panels.length()) {
            val panel = panels.optJSONObject(i)?.optJSONObject("engagementPanelSectionListRenderer") ?: continue
            if ("comments" in panel.optString("panelIdentifier") || "comments" in panel.optString("targetId")) return panel
        }
        return null
    }

    /**
     * A page of comments (or, with [replies], of one comment's replies): new-style entity
     * payloads tied together by keys, or old commentRenderers.
     */
    fun comments(json: String, replies: Boolean = false): CommentsPage {
        val root = JSONObject(json)
        val entities = mutableListOf<JSONObject>()
        val old = mutableListOf<Comment>()
        val surfaceKeys = HashMap<String, String>()
        val stateKeys = HashMap<String, String>()
        val surfaces = HashMap<String, JSONObject>()
        val states = HashMap<String, JSONObject>()
        val replyTokens = HashMap<String, String>()
        var count: String? = null
        var continuation: String? = null
        walk(root) { key, value ->
            if (value !is JSONObject) return@walk
            when (key) {
                "commentsHeaderRenderer" -> text(value.optJSONObject("countText"))?.let { count = it }
                "commentEntityPayload" -> entities += value
                "commentViewModel" -> value.optString("commentId").takeIf { it.isNotBlank() }?.let { id ->
                    value.optString("toolbarSurfaceKey").takeIf { it.isNotBlank() }?.let { surfaceKeys[id] = it }
                    value.optString("toolbarStateKey").takeIf { it.isNotBlank() }?.let { stateKeys[id] = it }
                }
                "engagementToolbarSurfaceEntityPayload" -> surfaces[value.optString("key")] = value
                "engagementToolbarStateEntityPayload" -> states[value.optString("key")] = value
                "commentThreadRenderer" -> {
                    // YouTube nests it: {"commentViewModel": {"commentViewModel": {"commentId": ...}}}.
                    val id = findFirst(value, "commentViewModel")?.let { it.optJSONObject("commentViewModel") ?: it }
                        ?.optString("commentId")?.takeIf { it.isNotBlank() }
                        ?: findFirst(value, "commentRenderer")?.optString("commentId")
                    val token = value.optJSONObject("replies")?.optJSONObject("commentRepliesRenderer")
                        ?.let { findFirst(it, "continuationCommand") }?.optString("token")
                    if (!id.isNullOrBlank() && !token.isNullOrBlank()) replyTokens[id] = token
                }
                "commentRenderer" -> old += Comment(
                    id = value.optString("commentId"),
                    author = text(value.optJSONObject("authorText")).orEmpty(),
                    avatar = bestThumbnail(value.optJSONObject("authorThumbnail")),
                    text = text(value.optJSONObject("contentText")).orEmpty(),
                    time = text(value.optJSONObject("publishedTimeText")),
                    likes = text(value.optJSONObject("voteCount")),
                    replies = value.optInt("replyCount").takeIf { it > 0 }?.toString(),
                    byCreator = value.optBoolean("authorIsChannelOwner"),
                )
                "continuationItemRenderer" -> findFirst(value, "continuationCommand")?.optString("token")?.let { continuation = it }
            }
        }
        fun action(surface: JSONObject?, command: String) = surface?.optJSONObject(command)
            ?.let { findFirst(it, "performCommentActionEndpoint") }?.optString("action")?.takeIf { it.isNotBlank() }
        val list = entities.mapNotNull { value ->
            val props = value.optJSONObject("properties") ?: return@mapNotNull null
            if ((props.optInt("replyLevel") > 0) != replies) return@mapNotNull null
            val id = props.optString("commentId")
            val author = value.optJSONObject("author")
            val toolbar = value.optJSONObject("toolbar")
            val surface = surfaceKeys[id]?.let(surfaces::get)
            val state = (stateKeys[id] ?: props.optString("toolbarStateKey")).let(states::get)
            val likeState = state?.optString("likeState").orEmpty()
            Comment(
                id = id,
                author = author?.optString("displayName").orEmpty(),
                avatar = author?.optString("avatarThumbnailUrl")?.takeIf { it.isNotBlank() }?.let(::fixUrl),
                text = props.optJSONObject("content")?.optString("content").orEmpty(),
                time = props.optString("publishedTime").takeIf { it.isNotBlank() },
                likes = toolbar?.optString(if (likeState == "TOOLBAR_LIKE_STATE_LIKED") "likeCountLiked" else "likeCountNotliked")
                    ?.takeIf { it.isNotBlank() && it != "0" },
                replies = toolbar?.optString("replyCount")?.takeIf { it.isNotBlank() && it != "0" },
                byCreator = author?.optBoolean("isCreator") == true,
                verified = author?.optBoolean("isVerified") == true,
                repliesToken = replyTokens[id],
                liked = likeState == "TOOLBAR_LIKE_STATE_LIKED",
                disliked = likeState == "TOOLBAR_LIKE_STATE_DISLIKED",
                likeAction = action(surface, "likeCommand"),
                unlikeAction = action(surface, "unlikeCommand"),
                dislikeAction = action(surface, "dislikeCommand"),
                undislikeAction = action(surface, "undislikeCommand"),
                hearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
                runs = runs(props.optJSONObject("content")),
                replyParams = surface?.optJSONObject("replyCommand")
                    ?.let { findFirst(it, "createCommentReplyEndpoint") }?.optString("createReplyParams")?.takeIf { it.isNotBlank() },
            )
        }
        val all = (list + old.map { it.copy(repliesToken = replyTokens[it.id]) }).distinctBy { it.id }
        val createParams = findFirst(root, "createCommentEndpoint")?.optString("createCommentParams")?.takeIf { it.isNotBlank() }
        return CommentsPage(count, all, continuation, createParams)
    }

    /** A comment's links (commandRuns) and inline images (attachmentRuns). */
    private fun runs(content: JSONObject?): List<TextRun> {
        content ?: return emptyList()
        val runs = mutableListOf<TextRun>()
        content.optJSONArray("commandRuns")?.let { list ->
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                val seek = findFirst(r, "watchEndpoint")?.takeIf { it.has("startTimeSeconds") }?.optInt("startTimeSeconds")
                val url = findFirst(r, "urlEndpoint")?.optString("url")?.takeIf { it.isNotBlank() }
                runs += LinkRun(r.optInt("startIndex"), r.optInt("length"), seek, url)
            }
        }
        content.optJSONArray("attachmentRuns")?.let { list ->
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                val element = r.optJSONObject("element") ?: continue
                val image = findFirst(element.optJSONObject("type"), "image") ?: continue
                val sources = image.optJSONArray("sources") ?: continue
                val url = largest(sources)?.let(::fixUrl) ?: continue
                val layout = element.optJSONObject("properties")?.optJSONObject("layoutProperties")
                val w = layout?.optJSONObject("width")?.optInt("value") ?: 16
                val h = layout?.optJSONObject("height")?.optInt("value") ?: 16
                val label = element.optJSONObject("properties")?.optJSONObject("accessibilityProperties")?.optString("label")
                runs += ImageRun(r.optInt("startIndex"), r.optInt("length"), url, w, h, label)
            }
        }
        return runs.sortedBy { it.start }
    }

    /** Caption tracks from a player response. */
    fun captionTracks(json: String): List<CaptionTrack> {
        val tracks = JSONObject(json).optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks") ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { i ->
            val t = tracks.optJSONObject(i) ?: return@mapNotNull null
            val url = t.optString("baseUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CaptionTrack(
                name = text(t.optJSONObject("name")) ?: t.optString("languageCode"),
                language = t.optString("languageCode"),
                url = url,
                auto = t.optString("kind") == "asr",
            )
        }
    }

    /** Caption lines from a timedtext json3 document. */
    fun cues(json: String): List<Cue> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { i ->
            val e = events.optJSONObject(i) ?: return@mapNotNull null
            val segs = e.optJSONArray("segs") ?: return@mapNotNull null
            val text = buildString { for (j in 0 until segs.length()) append(segs.optJSONObject(j)?.optString("utf8").orEmpty()) }.trim()
            if (text.isBlank()) return@mapNotNull null
            val start = e.optLong("tStartMs")
            Cue(start, start + e.optLong("dDurationMs", 2000), text)
        }
    }

    /** Your playlists in the "Save to" list (playlist/get_add_to_playlist). */
    fun playlistOptions(json: String): List<PlaylistOption> {
        val list = mutableListOf<PlaylistOption>()
        walk(JSONObject(json)) { key, value ->
            if (key == "playlistAddToOptionRenderer" && value is JSONObject) {
                val id = value.optString("playlistId").takeIf { it.isNotBlank() } ?: return@walk
                list += PlaylistOption(
                    id = id,
                    title = text(value.optJSONObject("title")) ?: id,
                    contains = value.optString("containsSelectedVideos") == "ALL",
                    private = "PRIVACY_PRIVATE" in value.optString("privacy") || "LOCK" in value.optJSONObject("privacyIcon")?.toString().orEmpty(),
                )
            }
        }
        return list.distinctBy { it.id }
    }

    /** Playlists, albums, courses and podcasts on a library page, in the order YouTube lists them. */
    fun playlists(json: String): Pair<List<Playlist>, String?> {
        val found = LinkedHashMap<String, Playlist>()
        var continuation: String? = null
        walk(JSONObject(json)) { key, value ->
            if (value !is JSONObject) return@walk
            when (key) {
                "lockupViewModel" -> playlistLockup(value)?.let { found.putIfAbsent(it.id, it) }
                "gridPlaylistRenderer", "playlistRenderer" -> {
                    val id = value.optString("playlistId").takeIf { it.isNotBlank() } ?: return@walk
                    val title = text(value.optJSONObject("title")) ?: return@walk
                    val count = text(value.optJSONObject("videoCountShortText")) ?: text(value.optJSONObject("videoCountText"))
                    val thumb = bestThumbnail(value.optJSONObject("thumbnail"))
                        ?: value.optJSONArray("thumbnails")?.optJSONObject(0)?.let(::bestThumbnail)
                    val byline = text(value.optJSONObject("shortBylineText"))
                    found.putIfAbsent(id, Playlist(id, title, thumb, byline, count, kindOf(id, byline.orEmpty(), title)))
                }
                "continuationCommand" -> value.optString("token").takeIf { it.isNotBlank() }?.let { continuation = it }
            }
        }
        return found.values.toList() to continuation
    }

    private fun playlistLockup(l: JSONObject): Playlist? {
        val type = l.optString("contentType")
        if (type == "LOCKUP_CONTENT_TYPE_VIDEO" || type.isBlank()) return null
        val id = l.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = meta.optJSONObject("title")?.optString("content")?.takeIf { it.isNotBlank() } ?: return null
        val rows = findFirst(meta.optJSONObject("metadata"), "contentMetadataViewModel")?.optJSONArray("metadataRows")
        val parts = mutableListOf<String>()
        if (rows != null) for (r in 0 until rows.length()) {
            val ps = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
            for (i in 0 until ps.length()) {
                ps.optJSONObject(i)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
        val image = l.optJSONObject("contentImage")
        val count = findFirst(image, "thumbnailBadgeViewModel")?.optString("text")?.takeIf { it.isNotBlank() }
        val subtitle = parts.filterNot { it.startsWith("View full") }.joinToString(" · ").takeIf { it.isNotBlank() }
        return Playlist(id, title, findFirst(image, "image")?.let(::bestSource), subtitle, count, kindOf(id, type + " " + parts.joinToString(" "), title))
    }

    private fun kindOf(id: String, info: String, title: String): Playlist.Kind {
        val text = "$info $title".lowercase()
        return when {
            "podcast" in text -> Playlist.Kind.Podcast
            "course" in text -> Playlist.Kind.Course
            id.startsWith("OLAK5uy_") || id.startsWith("RDCLAK") || "album" in text || "youtube music" in text ||
                "single" in text -> Playlist.Kind.Music
            else -> Playlist.Kind.Playlist
        }
    }

    /** The signed-in account from account/account_menu. */
    fun account(json: String): Account {
        val header = findFirst(JSONObject(json), "activeAccountHeaderRenderer")
        return Account(
            name = text(header?.optJSONObject("accountName")),
            handle = text(header?.optJSONObject("channelHandle")),
            photo = bestThumbnail(header?.optJSONObject("accountPhoto")),
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
                    videoId = notificationVideo(value),
                    read = value.optBoolean("read"),
                )
            }
        }
        return list
    }

    /**
     * The video a notification is about. Its tap action varies (a watch endpoint, a command
     * wrapping one, a URL), so fall back to any video ID in it, then the thumbnail's.
     */
    private fun notificationVideo(n: JSONObject): String? {
        findFirst(n.optJSONObject("navigationEndpoint"), "watchEndpoint")?.optString("videoId")?.takeIf { it.length == 11 }?.let { return it }
        val raw = n.toString()
        Regex("\"videoId\":\"([\\w-]{11})\"").find(raw)?.let { return it.groupValues[1] }
        Regex("watch\\?v=([\\w-]{11})").find(raw)?.let { return it.groupValues[1] }
        Regex("/vi(?:_webp)?/([\\w-]{11})/").find(raw)?.let { return it.groupValues[1] }
        return null
    }

    /** The bell menu: old-style menuServiceItemRenderers or new listItemViewModels. */
    private fun bellOptions(node: JSONObject?): List<BellOption> {
        node ?: return emptyList()
        val options = mutableListOf<BellOption>()
        fun add(title: String?, holder: JSONObject, selected: Boolean) {
            val params = findFirst(holder, "modifyChannelNotificationPreferenceEndpoint")?.optString("params")?.takeIf { it.isNotBlank() } ?: return
            val raw = holder.toString()
            val kind = when {
                "NOTIFICATIONS_ACTIVE" in raw -> BellOption.Kind.All
                "NOTIFICATIONS_OFF" in raw -> BellOption.Kind.None
                else -> BellOption.Kind.Personalized
            }
            options += BellOption(title ?: kind.name, params, selected, kind)
        }
        walk(node) { key, value ->
            if (value !is JSONObject) return@walk
            when (key) {
                "menuServiceItemRenderer" -> add(text(value.optJSONObject("text")), value, value.optBoolean("isSelected"))
                "listItemViewModel" -> add(value.optJSONObject("title")?.optString("content"), value, value.optBoolean("isSelected"))
            }
        }
        return options.distinctBy { it.params }
    }

    private fun heatmap(root: JSONObject): List<Float> {
        var markers: JSONArray? = null
        walk(root) { key, value ->
            if (markers == null && key == "macroMarkersListEntity" && value is JSONObject) {
                val list = value.optJSONObject("markersList")
                if (list?.optString("markerType")?.contains("HEATMAP") != false) markers = list?.optJSONArray("markers")
            }
        }
        val m = markers ?: return emptyList()
        return (0 until m.length()).mapNotNull { m.optJSONObject(it)?.optDouble("intensityScoreNormalized")?.toFloat()?.takeIf { v -> !v.isNaN() } }
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
