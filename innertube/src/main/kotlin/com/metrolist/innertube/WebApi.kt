// Counstar: YouTube (WEB client) calls that InnerTubeX doesn't make, signed in with the session cookie.
package com.metrolist.innertube

import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.random.Random

object WebApi {
    private const val ORIGIN = "https://www.youtube.com"
    private val client = YouTubeClient.WEB

    private val signedIn get() = !YouTube.cookie.isNullOrBlank()

    /**
     * The same headers InnerTubeX sends for a signed-in WEB request: the cookie, its SAPISID
     * hash, and which signed-in account (X-Goog-AuthUser) to act as.
     */
    private fun HttpRequestBuilder.auth() {
        header("X-Goog-Api-Format-Version", "1")
        header("X-YouTube-Client-Name", client.clientId)
        header("X-YouTube-Client-Version", client.clientVersion)
        header("X-Origin", ORIGIN)
        header("Origin", ORIGIN)
        header("Referer", "$ORIGIN/")
        header("User-Agent", client.userAgent)
        header("Accept-Language", "en-US,en;q=0.9")
        YouTube.visitorData?.let { header("X-Goog-Visitor-Id", it) }
        val cookie = YouTube.cookie?.takeIf { it.isNotBlank() } ?: return
        val jar = cookie.split(';').map { it.trim() }
        val sapisid = sequenceOf("SAPISID=", "__Secure-3PAPISID=", "__Secure-1PAPISID=")
            .firstNotNullOfOrNull { name -> jar.firstOrNull { it.startsWith(name) }?.substringAfter('=') }
        header("Cookie", cookie)
        header("X-Goog-AuthUser", YouTube.authUser)
        if (sapisid != null) {
            val now = System.currentTimeMillis() / 1000
            val hash = MessageDigest.getInstance("SHA-1").digest("$now $sapisid $ORIGIN".toByteArray())
                .joinToString("") { "%02x".format(it) }
            header("Authorization", "SAPISIDHASH ${now}_$hash")
        }
    }

    /** POSTs [body] (plus the client context) to youtubei/v1/[endpoint] and returns the JSON text. */
    suspend fun post(endpoint: String, body: JSONObject = JSONObject()): Result<String> = runCatching {
        val context = JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", client.clientName)
                .put("clientVersion", client.clientVersion)
                .put("hl", "en")
                .put("gl", "US")
                .put("visitorData", YouTube.visitorData.orEmpty()),
        )
        // Which of the account's channels to act as. Without it, a like or a subscription made
        // while signed in to a brand channel lands on the Google account's main channel instead.
        val user = JSONObject().put("lockedSafetyMode", false)
        if (signedIn) YouTube.dataSyncId?.takeIf { it.isNotBlank() }?.let { user.put("onBehalfOfUser", it) }
        context.put("user", user)
        body.put("context", context)
        val response = YouTube.extractionTransport().httpClient.post("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            auth()
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "HTTP ${response.status.value} from $endpoint" }
        text
    }

    suspend fun browseContinuation(token: String) = post("browse", JSONObject().put("continuation", token))
    suspend fun searchContinuation(token: String) = post("search", JSONObject().put("continuation", token))
    suspend fun nextContinuation(token: String) = post("next", JSONObject().put("continuation", token))
    suspend fun browse(browseId: String, params: String? = null) =
        post("browse", JSONObject().put("browseId", browseId).apply { params?.let { put("params", it) } })

    /**
     * The Shorts that follow one, for swiping: [params] is a Short card's sequenceParams, the
     * previous page's token, or [shortSequence] of a video ID.
     */
    suspend fun reelSequence(params: String) = post("reel/reel_watch_sequence", JSONObject().put("sequenceParams", params))

    /** Sequence params for the Shorts after [videoId]: protobuf {1: videoId}, base64. */
    fun shortSequence(videoId: String): String {
        val raw = byteArrayOf(0x0A, videoId.length.toByte()) + videoId.toByteArray()
        return java.util.Base64.getUrlEncoder().encodeToString(raw)
    }

    suspend fun notifications() = post("notification/get_notification_menu", JSONObject().put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX"))

    /** The signed-in account's name, handle and photo. */
    suspend fun accountMenu() = post("account/account_menu")

    private fun target(videoId: String) = JSONObject().put("target", JSONObject().put("videoId", videoId))

    suspend fun like(videoId: String) = post("like/like", target(videoId)).checked()
    suspend fun dislike(videoId: String) = post("like/dislike", target(videoId)).checked()
    suspend fun removeLike(videoId: String) = post("like/removelike", target(videoId)).checked()

    suspend fun subscribe(channelId: String) = post("subscription/subscribe", JSONObject().put("channelIds", JSONArray().put(channelId))).checked()
    suspend fun unsubscribe(channelId: String) = post("subscription/unsubscribe", JSONObject().put("channelIds", JSONArray().put(channelId))).checked()

    /** Sets a channel's bell (all / personalized / none), using the params from that menu item. */
    suspend fun setChannelNotifications(params: String) = post("notification/modify_channel_preference", JSONObject().put("params", params)).checked()

    /** Likes, dislikes or undoes either on a comment, with the action YouTube gave for it. */
    suspend fun commentAction(action: String) =
        post("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action))).checked()

    /** Posts a comment on a video, with the params from its comments' header. */
    suspend fun createComment(params: String, text: String) =
        post("comment/create_comment", JSONObject().put("createCommentParams", params).put("commentText", text)).checked()

    /** Replies to a comment, with the params from its reply button. */
    suspend fun createReply(params: String, text: String) =
        post("comment/create_comment_reply", JSONObject().put("createReplyParams", params).put("commentText", text)).checked()

    /** Your playlists, and which already hold the video. */
    suspend fun playlistOptions(videoId: String) =
        post("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId)).put("excludeWatchLater", false))

    /**
     * A video's caption tracks. The Android app's player request lists tracks whose links work
     * without a PO token, unlike the web player's.
     */
    suspend fun captionsPlayer(videoId: String): Result<String> = runCatching {
        val body = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("context", JSONObject().put("client", JSONObject().put("clientName", "ANDROID").put("clientVersion", "20.10.38").put("hl", "en").put("gl", "US")))
        val response = YouTube.extractionTransport().httpClient.post("$ORIGIN/youtubei/v1/player?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip")
            header("X-YouTube-Client-Name", "3")
            header("X-YouTube-Client-Version", "20.10.38")
            setBody(body.toString())
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value} from player" }
        response.bodyAsText()
    }

    /** What YouTube suggests as you type [query] (its JSONP: suggestions, some with a channel). */
    suspend fun suggestions(query: String): Result<String> =
        fetch("https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=en&gl=us&q=" + java.net.URLEncoder.encode(query, "UTF-8"))

    /** Fetches a plain URL (caption text). */
    suspend fun fetch(url: String): Result<String> = runCatching {
        val response = YouTube.extractionTransport().httpClient.get(url) { header("User-Agent", client.userAgent) }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        response.bodyAsText()
    }

    /** Adds a video to, or removes it from, one of your playlists. */
    suspend fun editPlaylist(playlistId: String, videoId: String, add: Boolean) = post(
        "browse/edit_playlist",
        JSONObject()
            .put("playlistId", playlistId)
            .put(
                "actions",
                JSONArray().put(
                    if (add) JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
                    else JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId),
                ),
            ),
    ).checked()

    /** Adds to or removes from Watch later. */
    suspend fun watchLater(videoId: String, add: Boolean) = post(
        "browse/edit_playlist",
        JSONObject()
            .put("playlistId", "WL")
            .put(
                "actions",
                JSONArray().put(
                    if (add) JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
                    else JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId),
                ),
            ),
    ).checked()

    /**
     * Puts the video in your watch history, the way the YouTube website does: it asks the
     * (signed-in) player for the video's stats link and reports a playback on it. [fallbackUrl]
     * is a stats link from elsewhere, if the player doesn't give one.
     */
    suspend fun markWatched(videoId: String, fallbackUrl: String? = null): Result<Unit> = runCatching {
        check(signedIn) { "Not signed in" }
        val url = post("player", JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true))
            .mapCatching { JSONObject(it).optJSONObject("playbackTracking")?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackUrl
            ?: error("No stats link for $videoId")
        val cpn = (1..16).map { CPN_CHARS[Random.nextInt(CPN_CHARS.length)] }.joinToString("")
        val response = YouTube.extractionTransport().httpClient.get(url.replace("https://s.youtube.com", ORIGIN)) {
            auth()
            url {
                parameters.remove("cpn")
                parameters.append("ver", "2")
                parameters.append("c", client.clientName)
                parameters.append("cver", client.clientVersion)
                parameters.append("cpn", cpn)
            }
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value} from stats" }
    }

    private const val CPN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    /**
     * YouTube answers some refused actions with 200 and an error inside (signed out, or the
     * session expired): treat those as failures too.
     */
    private fun Result<String>.checked(): Result<String> = mapCatching { text ->
        val json = runCatching { JSONObject(text) }.getOrNull()
        val error = json?.optJSONObject("error")
        check(error == null) { error?.optString("message").orEmpty().ifBlank { "YouTube refused that" } }
        val actions = json?.optJSONArray("actions")?.toString().orEmpty()
        check("signInEndpoint" !in actions && "\"modalWithTitleAndButtonRenderer\"" !in actions) { "Sign in again to do that" }
        text
    }
}
