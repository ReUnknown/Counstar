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
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import org.json.JSONObject
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
        /** YouTube's storyboard spec: the little preview frames shown while scrubbing. */
        val storyboardSpec: String?,
        /** Where to report a playback, so the video lands in watch history. */
        val statsUrl: String? = null,
        /** Every height the video comes in (for this phone's codecs), tallest first. */
        val heights: List<Int> = emptyList(),
        /** The video's audio languages (dubbed videos have several), and the one playing. */
        val audioTracks: List<AudioTrack> = emptyList(),
        val audioTrack: String? = null,
    )

    /** One of a video's audio tracks: "English (original)", "Spanish", ... */
    data class AudioTrack(val id: String, val name: String, val original: Boolean)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

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
        /** Known to be live (from the card): go straight to the live stream. */
        live: Boolean = false,
        /** An audio track picked by its id (null: the video's original). */
        audioTrack: String? = null,
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
        if (live) liveStream(videoId, config, visitorData, log)?.let { return it }
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
                val text = innerTube.player(
                    client = client,
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = config.signatureTimestamp,
                    poToken = token?.playerRequestToken,
                    requestVisitorData = visitorData.ifBlank { null },
                    encryptedHostFlags = config.encryptedHostFlags.takeIf { client.isEmbedded },
                ).bodyAsText()
                val storyboard = runCatching {
                    JSONObject(text).optJSONObject("storyboards")
                        ?.optJSONObject("playerStoryboardSpecRenderer")
                        ?.optString("spec")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                val statsUrl = runCatching {
                    JSONObject(text).optJSONObject("playbackTracking")
                        ?.optJSONObject("videostatsPlaybackUrl")
                        ?.optString("baseUrl")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                // A live stream has no files to pick, only an HLS playlist.
                if (JSONObject(text).optJSONObject("videoDetails")?.optBoolean("isLive") == true) {
                    liveStream(videoId, config, visitorData, log)?.let { return it }
                    lastProblem = "Couldn't load this live stream"
                    continue
                }
                val response = json.decodeFromString<PlayerResponse>(text)
                if (response.playabilityStatus.status != "OK") {
                    lastProblem = response.playabilityStatus.reason ?: response.playabilityStatus.status
                    log("${client.clientName}: ${response.playabilityStatus.status}")
                    continue
                }
                // Dubbed videos have an audio track per language: play the picked one, else the
                // original (otherwise the best-sounding stream could be any dub).
                val tracksByItag = HashMap<Int, AudioTrack>()
                JSONObject(text).optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")?.let { list ->
                    for (i in 0 until list.length()) {
                        val f = list.optJSONObject(i) ?: continue
                        val t = f.optJSONObject("audioTrack") ?: continue
                        val id = t.optString("id").takeIf { it.isNotBlank() } ?: continue
                        tracksByItag[f.optInt("itag")] = AudioTrack(id, t.optString("displayName").ifBlank { id }, t.optBoolean("audioIsDefault"))
                    }
                }
                val tracks = tracksByItag.values.distinctBy { it.id }.sortedByDescending { it.original }
                val wanted = tracks.firstOrNull { it.id == audioTrack } ?: tracks.firstOrNull { it.original } ?: tracks.firstOrNull()
                val formats = response.streamingData?.adaptiveFormats.orEmpty().filter { f ->
                    wanted == null || !f.isAudio || tracksByItag[f.itag]?.id.let { it == null || it == wanted.id }
                }
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
                    storyboardSpec = storyboard,
                    statsUrl = statsUrl,
                    heights = videoFormats.mapNotNull { it.height }.filter { it >= 144 }.distinct().sortedDescending(),
                    audioTracks = tracks,
                    audioTrack = wanted?.id,
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

    /** Clients whose live playlists play without a PO token, best first. */
    private val liveClients = listOf(YouTubeClient.IOS, YouTubeClient.TVHTML5, YouTubeClient.WEB)

    /**
     * A live stream: its HLS playlist (picture and sound together, which the player follows as
     * the stream goes on), from the first client that gives one.
     */
    private suspend fun liveStream(videoId: String, config: PlayerConfig, visitorData: String, log: (String) -> Unit): Stream? {
        val innerTube = YouTube.extractionTransport().innerTube
        for (client in liveClients) {
            try {
                val text = innerTube.player(
                    client = client,
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = config.signatureTimestamp,
                    poToken = null,
                    requestVisitorData = visitorData.ifBlank { null },
                    encryptedHostFlags = null,
                ).bodyAsText()
                val root = JSONObject(text)
                val hls = root.optJSONObject("streamingData")?.optString("hlsManifestUrl")?.takeIf { it.isNotBlank() }
                if (hls == null) {
                    log("${client.clientName}: no live playlist (${root.optJSONObject("playabilityStatus")?.optString("status")})")
                    continue
                }
                log("${client.clientName}: live HLS")
                return Stream(
                    videoUrl = hls,
                    audioUrl = hls,
                    videoLength = -1L,
                    audioLength = -1L,
                    height = null,
                    headers = headersFor(client),
                    client = client.clientName,
                    storyboardSpec = null,
                    statsUrl = root.optJSONObject("playbackTracking")?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.takeIf { it.isNotBlank() },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("${client.clientName} live failed: ${e::class.simpleName}")
            }
        }
        return null
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
