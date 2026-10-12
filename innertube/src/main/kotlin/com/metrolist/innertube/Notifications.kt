// Counstar: the notifications inbox, which InnerTubeX has no call for.
package com.metrolist.innertube

import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONObject
import java.security.MessageDigest

object Notifications {
    private const val ORIGIN = "https://www.youtube.com"

    /** The signed-in account's notifications, as JSON text. */
    suspend fun raw(): Result<String> = runCatching {
        val cookie = YouTube.cookie ?: error("Not signed in")
        val sapisid = cookie.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter('=')
            ?: error("No SAPISID cookie")
        val now = System.currentTimeMillis() / 1000
        val hash = MessageDigest.getInstance("SHA-1").digest("$now $sapisid $ORIGIN".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val client = YouTubeClient.WEB
        val body = JSONObject()
            .put(
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
            .put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
        YouTube.extractionTransport().httpClient.post("$ORIGIN/youtubei/v1/notification/get_notification_menu?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("Cookie", cookie)
            header("Authorization", "SAPISIDHASH ${now}_$hash")
            header("X-Goog-AuthUser", YouTube.authUser)
            header("X-Origin", ORIGIN)
            header("Origin", ORIGIN)
            header("User-Agent", client.userAgent)
            YouTube.visitorData?.let { header("X-Goog-Visitor-Id", it) }
            setBody(body.toString())
        }.bodyAsText()
    }
}
