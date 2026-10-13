// Counstar: YouTube (WEB client) calls that InnerTubeX doesn't make, signed in with the session cookie.
package com.metrolist.innertube

import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.json.JSONObject
import java.security.MessageDigest

object WebApi {
    private const val ORIGIN = "https://www.youtube.com"

    /** POSTs [body] (plus the client context) to youtubei/v1/[endpoint] and returns the JSON text. */
    suspend fun post(endpoint: String, body: JSONObject = JSONObject()): Result<String> = runCatching {
        val client = YouTubeClient.WEB
        body.put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", client.clientName)
                    .put("clientVersion", client.clientVersion)
                    .put("hl", "en")
                    .put("visitorData", YouTube.visitorData.orEmpty()),
            ),
        )
        val cookie = YouTube.cookie?.takeIf { it.isNotBlank() }
        val sapisid = cookie?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter('=')
        val response = YouTube.extractionTransport().httpClient.post("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("X-Origin", ORIGIN)
            header("Origin", ORIGIN)
            header("User-Agent", client.userAgent)
            YouTube.visitorData?.let { header("X-Goog-Visitor-Id", it) }
            if (cookie != null && sapisid != null) {
                val now = System.currentTimeMillis() / 1000
                val hash = MessageDigest.getInstance("SHA-1").digest("$now $sapisid $ORIGIN".toByteArray())
                    .joinToString("") { "%02x".format(it) }
                header("Cookie", cookie)
                header("Authorization", "SAPISIDHASH ${now}_$hash")
                header("X-Goog-AuthUser", YouTube.authUser)
            }
            setBody(body.toString())
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value} from $endpoint" }
        response.bodyAsText()
    }

    suspend fun browseContinuation(token: String) = post("browse", JSONObject().put("continuation", token))
    suspend fun searchContinuation(token: String) = post("search", JSONObject().put("continuation", token))
    suspend fun nextContinuation(token: String) = post("next", JSONObject().put("continuation", token))

    suspend fun notifications() = post("notification/get_notification_menu", JSONObject().put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX"))

    private fun target(videoId: String) = JSONObject().put("target", JSONObject().put("videoId", videoId))

    suspend fun like(videoId: String) = post("like/like", target(videoId))
    suspend fun dislike(videoId: String) = post("like/dislike", target(videoId))
    suspend fun removeLike(videoId: String) = post("like/removelike", target(videoId))

    suspend fun subscribe(channelId: String) = post("subscription/subscribe", JSONObject().put("channelIds", org.json.JSONArray().put(channelId)))
    suspend fun unsubscribe(channelId: String) = post("subscription/unsubscribe", JSONObject().put("channelIds", org.json.JSONArray().put(channelId)))

    /** Sets a channel's bell (all / personalized / none), using the params from that menu item. */
    suspend fun setChannelNotifications(params: String) = post("notification/modify_channel_preference", JSONObject().put("params", params))

    /** YouTube's report form for a video (the "PAabuse_report" panel). */
    suspend fun reportForm(params: String) = post("get_panel", JSONObject().put("panelId", "PAabuse_report").put("params", params))

    /** Sends a report for the reason whose params came from [reportForm]. */
    suspend fun report(params: String) = post("flag/flag", JSONObject().put("params", params))

    /** Adds to or removes from Watch later. */
    suspend fun watchLater(videoId: String, add: Boolean) = post(
        "browse/edit_playlist",
        JSONObject()
            .put("playlistId", "WL")
            .put(
                "actions",
                org.json.JSONArray().put(
                    if (add) JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
                    else JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId),
                ),
            ),
    )
}
