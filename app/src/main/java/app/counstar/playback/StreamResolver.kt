// Adapted from Metrolist (GPL-3.0) by way of Wave: https://github.com/MetrolistGroup/Metrolist
package app.counstar.playback

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import app.counstar.playback.potoken.PoTokenGenerator
import com.metrolist.innertube.VideoStreams
import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** A video ready to play: separate video and audio streams, which the player plays together. */
class VideoStream(
    val videoId: String,
    val videoUrl: String,
    val audioUrl: String,
    val headers: Map<String, String>,
    val videoLength: Long,
    val audioLength: Long,
    /** Bytes per request when the stream only answers bounded ranges, else 0. */
    val chunkSize: Long,
    val height: Int?,
    val storyboardSpec: String? = null,
    val statsUrl: String? = null,
    val heights: List<Int> = emptyList(),
    val audioTracks: List<VideoStreams.AudioTrack> = emptyList(),
    val audioTrack: String? = null,
) {
    /** An HLS manifest: picture and sound together, which the player streams as one. */
    val isHls: Boolean get() = videoUrl == audioUrl || "/manifest/hls" in videoUrl || ".m3u8" in videoUrl
}

/** The only way Counstar turns a video ID into something playable, through InnerTubeX. */
object StreamResolver {
    private const val TAG = "CounstarStreams"
    /** The highest quality to stream, from Settings. */
    private val MAX_HEIGHT get() = heightOverride ?: app.counstar.data.Settings.maxHeight

    /** A quality picked for the current video, over the setting. */
    @Volatile var heightOverride: Int? = null
    /** An audio track picked for the current video (null: its original). */
    @Volatile var audioTrackOverride: String? = null

    @Volatile private var applicationContext: Context? = null
    @Volatile private var currentBundle: Bundle? = null
    private val bundleMutex = Mutex()

    /** Whether this phone has a hardware VP9 decoder (software VP9 at 1080p stutters or fails). */
    private val hardwareVp9: Boolean by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.isHardwareAccelerated && "goldfish" !in info.name &&
                info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) }
        }
    }

    /** Set when this phone's VP9 decoder fails on a real video: H.264 from then on. */
    @Volatile var vp9Failed = false

    fun initialize(context: Context) {
        if (applicationContext == null) applicationContext = context.applicationContext
    }

    suspend fun resolve(videoId: String, live: Boolean = false): Result<VideoStream> =
        try {
            Result.success(direct(videoId, live))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w("direct video failed (%s), trying InnerTubeX", error.message)
            viaExtractor(videoId)
        }

    /** Counstar's own path: separate video and audio links from one player response. */
    private suspend fun direct(videoId: String, live: Boolean): VideoStream {
        val bundle = bundle()
        val stream = VideoStreams.resolve(
            videoId = videoId,
            maxHeight = MAX_HEIGHT,
            allowVp9 = hardwareVp9 && !vp9Failed,
            signedIn = !YouTube.cookie.isNullOrBlank(),
            configParser = bundle.configParser,
            cipher = bundle.cipherService,
            poToken = { id, visitorData -> tokenProvider.getPoToken(id, visitorData, YouTube.cookie) },
            log = { Timber.tag(TAG).d(it) },
            live = live,
            audioTrack = audioTrackOverride,
        )
        return VideoStream(
            videoId = videoId,
            videoUrl = stream.videoUrl,
            audioUrl = stream.audioUrl,
            headers = stream.headers,
            videoLength = stream.videoLength,
            audioLength = stream.audioLength,
            chunkSize = 0L,
            height = stream.height,
            storyboardSpec = stream.storyboardSpec,
            statsUrl = stream.statsUrl,
            heights = stream.heights,
            audioTracks = stream.audioTracks,
            audioTrack = stream.audioTrack,
        )
    }

    /** InnerTubeX's extractor: used for what the direct path can't do, like live HLS streams. */
    private suspend fun viaExtractor(videoId: String): Result<VideoStream> =
        try {
            val hints = ContentHints(wantVideo = true, maxVideoHeight = MAX_HEIGHT)
                .withStreamCapabilities(allowHls = true, allowSabr = false, allowBoundedRange = true)
            val stream = requireNotNull(
                bundle().extractor.extract(
                    videoId = videoId,
                    hints = hints,
                    excludedClients = emptySet(),
                    audioQuality = AudioQuality.HIGH,
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                ),
            ) { "No playable stream" }
            check(stream.sabrBootstrap == null && stream.sabrVideoBootstrap == null) { "SABR streams aren't supported yet" }
            val videoUrl = checkNotNull(stream.videoUrl) { "This video has no video stream" }
            Result.success(
                VideoStream(
                    videoId = videoId,
                    videoUrl = videoUrl,
                    audioUrl = stream.audioUrl,
                    headers = stream.headers,
                    videoLength = stream.videoContentLengthBytes ?: -1L,
                    audioLength = stream.contentLengthBytes ?: -1L,
                    chunkSize = if (stream.useRangeChunks || stream.requireBoundedRange) stream.rangeChunkSizeBytes else 0L,
                    height = stream.videoHeight,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            Result.failure(error.cause?.takeIf { error.reason == StreamResolveException.Reason.NETWORK } ?: error)
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun bundle(): Bundle {
        val transport = YouTube.extractionTransport()
        currentBundle?.takeIf { it.generation == transport.generation }?.let { return it }
        return bundleMutex.withLock {
            val latest = YouTube.extractionTransport()
            currentBundle?.takeIf { it.generation == latest.generation }?.let { return@withLock it }
            runCatching { currentBundle?.cipherService?.dispose() }
            val remoteStore = RemotePlayerConfigStore(latest.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latest.httpClient, remoteStore, logger)
            val configParser = YtConfigParserImpl(latest.httpClient, latest.innerTube, remoteStore, logger).withEmbeddedConfigFallback()
            val extractor = InnerTubeExtractor(
                configParser = configParser,
                cipherService = cipherService,
                innerTube = latest.innerTube,
                tokenProvider = tokenProvider,
                logger = logger,
            )
            Bundle(latest.generation, cipherService, configParser, extractor).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        AndroidPlayerConfigRepository(requireNotNull(applicationContext) { "StreamResolver is not initialized" })
    }

    private val poTokenGenerator: PoTokenGenerator by lazy {
        PoTokenGenerator(requireNotNull(applicationContext) { "StreamResolver is not initialized" })
    }

    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true,
        )

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
            poTokenGenerator.getWebClientPoToken(videoId, visitorData)?.let { token ->
                PoTokenResult(
                    playerRequestToken = token.playerRequestPoToken,
                    streamingDataToken = token.streamingDataPoToken,
                    visitorData = visitorData,
                )
            }

        override suspend fun close() {
            poTokenGenerator.close()
        }
    }

    private val logger = InnerTubeLogger { event: InnerTubeLogEvent ->
        val details = event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        val message = event.message + details.takeUnless { event.details.isEmpty() }.orEmpty()
        when (event.level) {
            InnerTubeLogLevel.DEBUG -> Timber.tag(TAG).d(message)
            InnerTubeLogLevel.INFO -> Timber.tag(TAG).i(message)
            InnerTubeLogLevel.WARN -> Timber.tag(TAG).w(message)
            InnerTubeLogLevel.ERROR -> Timber.tag(TAG).e(message)
        }
    }

    private data class Bundle(
        val generation: Long,
        val cipherService: YouTubeCipherService,
        val configParser: YtConfigParser,
        val extractor: InnerTubeExtractor,
    )

    private class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) = preferences.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) = preferences.edit().putString("source_url", value).apply()
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) = preferences.edit().putString("etag", value).apply()

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }

    private fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
                try {
                    this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }
}
