// Counstar: video stream extraction on top of InnerTubeX's building blocks.
package com.metrolist.innertube

import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ExtractionCipherService
import com.metrolist.innertubex.extraction.PlayerConfig
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.selectBestAudioFormat
import com.metrolist.innertubex.extraction.selectBestVideoFormat
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.PlayerResponse
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException

/**
 * Picture and sound for one video, as two direct googlevideo links.
 *
 * InnerTubeX's own extractor (v0.8.4) drops video links once it has solved their "n" challenge,
 * so for video Counstar asks for the player response itself, picks the formats, and lets
 * InnerTubeX's cipher service do the deciphering.
 */
object VideoStreams {
    class Stream(
        val videoUrl: String,
        val audioUrl: String,
        val videoLength: Long,
        val audioLength: Long,
        val height: Int?,
        val headers: Map<String, String>,
        val client: String,
    )

    /** Clients to try, in order. The embedded player needs no PO token; the others do. */
    private val clients = listOf(YouTubeClient.WEB_EMBEDDED_PLAYER, YouTubeClient.TVHTML5_SIMPLY, YouTubeClient.WEB)

    suspend fun resolve(
        videoId: String,
        maxHeight: Int,
        /** Whether the phone decodes VP9 in hardware; if not, H.264 is used. */
        allowVp9: Boolean,
        signedIn: Boolean,
        configParser: YtConfigParser,
        cipher: ExtractionCipherService,
        poToken: suspend (videoId: String, visitorData: String) -> PoTokenResult?,
        log: (String) -> Unit = {},
    ): Stream {
        val innerTube = YouTube.extractionTransport().innerTube
        // The embed page's player script is much smaller than the watch page's, so deciphering
        // with it is far quicker, and the embedded client needs its host flags.
        val config: PlayerConfig = try {
            configParser.fetchEmbeddedConfig(videoId, useLoginCookies = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            configParser.fetchConfig(videoId, useLoginCookies = signedIn)
        }
        val visitorData = config.visitorData ?: innerTube.visitorData.orEmpty()
        var lastProblem = "No playable stream"
        for (client in clients) {
            try {
                val token = if (client.useWebPoTokens || client.requirePoToken) {
                    runCatching { poToken(videoId, visitorData) }.getOrNull()
                } else {
                    null
                }
                if (client.requirePoToken && token == null) {
                    log("skip ${client.clientName}: no PO token")
                    continue
                }
                val response = innerTube.player(
                    client = client,
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = config.signatureTimestamp,
                    poToken = token?.playerRequestToken,
                    requestVisitorData = visitorData.ifBlank { null },
                    encryptedHostFlags = config.encryptedHostFlags.takeIf { client.isEmbedded },
                ).body<PlayerResponse>()
                if (response.playabilityStatus.status != "OK") {
                    lastProblem = response.playabilityStatus.reason ?: response.playabilityStatus.status
                    log("${client.clientName}: ${response.playabilityStatus.status}")
                    continue
                }
                val formats = response.streamingData?.adaptiveFormats.orEmpty()
                // AV1 isn't decoded in hardware on many phones. VP9 is on most, H.264 on all.
                val videoFormats = formats.filter { !it.isAudio && "av01" !in it.mimeType && (allowVp9 || "vp9" !in it.mimeType) }
                val video = selectBestVideoFormat(videoFormats, requireUrl = false, maxHeight = maxHeight)
                val audio = selectBestAudioFormat(formats.filter { it.isAudio }, AudioQuality.HIGH, requireUrl = false)
                if (video == null || audio == null) {
                    log("${client.clientName}: no formats")
                    continue
                }
                val processed = cipher.processFormats(config.playerUrl, listOf(video, audio))
                val cpn = generateClientPlaybackNonce()
                fun finish(url: String?) = url
                    ?.takeIf { it.isNotBlank() }
                    ?.withParam("pot", token?.streamingDataToken)
                    ?.withParam("cpn", cpn)
                val videoUrl = finish(processed.getOrNull(0)?.url)
                val audioUrl = finish(processed.getOrNull(1)?.url)
                if (videoUrl == null || audioUrl == null) {
                    log("${client.clientName}: decipher failed")
                    continue
                }
                log("${client.clientName}: ${video.height}p ${video.mimeType.substringBefore(';')}")
                return Stream(
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    videoLength = video.contentLength ?: -1L,
                    audioLength = audio.contentLength ?: -1L,
                    height = video.height,
                    headers = headersFor(client),
                    client = client.clientName,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastProblem = e.message ?: e::class.simpleName.orEmpty()
                log("${client.clientName} failed: ${e::class.simpleName}")
            }
        }
        error(lastProblem)
    }

    private fun headersFor(client: YouTubeClient): Map<String, String> = buildMap {
        put("User-Agent", client.userAgent)
        if (client.clientName.startsWith("WEB")) {
            put("Origin", "https://www.youtube.com")
            put("Referer", "https://www.youtube.com/")
        }
    }

    private fun String.withParam(name: String, value: String?): String {
        if (value.isNullOrBlank() || contains("&$name=") || contains("?$name=")) return this
        return this + (if (contains('?')) "&" else "?") + name + "=" + value
    }
}
