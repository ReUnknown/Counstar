package app.counstar.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.counstar.data.Feeds
import app.counstar.data.VideoItem
import com.metrolist.innertube.WebApi
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * The one video player. YouTube serves picture and sound as separate streams, so each video is
 * two downloads the player plays in sync.
 */
@OptIn(UnstableApi::class)
object CounstarPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null
    private var retried = false

    lateinit var player: ExoPlayer
        private set

    /** What's loaded (playing or paused), or null. */
    var current by mutableStateOf<VideoItem?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Preview frames for scrubbing, when YouTube has them. */
    var storyboard by mutableStateOf<Storyboard?>(null)
        private set

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun initialize(context: Context) {
        if (::player.isInitialized) return
        StreamResolver.initialize(context)
        // Emulators' "goldfish" decoders reject YouTube's streams; real phones don't have them.
        val renderers = DefaultRenderersFactory(context.applicationContext).setMediaCodecSelector { mime, secure, tunneling ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).filterNot { "goldfish" in it.name }
        }
        player = ExoPlayer.Builder(context.applicationContext, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                Timber.w(e, "Playback failed")
                val item = current ?: return
                // A decoder that claims VP9 but can't play it: switch to H.264 and reload.
                val decoderProblem = e.errorCode in setOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                )
                if (decoderProblem && !StreamResolver.vp9Failed) {
                    StreamResolver.vp9Failed = true
                    load(item, startAt = player.currentPosition, isRetry = true)
                    return
                }
                // Stream links expire and are sometimes refused: get a fresh one once.
                if (!retried) {
                    retried = true
                    val at = player.currentPosition
                    load(item, startAt = at, isRetry = true)
                } else {
                    error = "Couldn't play this video."
                }
            }
        })
    }

    fun play(item: VideoItem) {
        if (current?.id == item.id) {
            player.play()
            return
        }
        load(item, startAt = 0L, isRetry = false)
    }

    fun stop() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        current = null
        storyboard = null
        loading = false
        error = null
    }

    private fun load(item: VideoItem, startAt: Long, isRetry: Boolean) {
        loadJob?.cancel()
        if (!isRetry) retried = false
        if (current?.id != item.id) storyboard = null
        current = item
        error = null
        loading = true
        player.stop()
        loadJob = scope.launch {
            val stream = withContext(Dispatchers.IO) { StreamResolver.resolve(item.id) }
            stream
                .onSuccess {
                    storyboard = Storyboard.parse(it.storyboardSpec)
                    player.setMediaSource(mediaSource(it), startAt)
                    player.prepare()
                    player.play()
                    if (!isRetry) markWatched(item.id, it.statsUrl)
                }
                .onFailure {
                    Timber.w(it, "Couldn't resolve %s", item.id)
                    error = "Couldn't play this video."
                }
            loading = false
        }
    }

    /** Adds the video to your YouTube watch history (signed in), like watching it on YouTube. */
    private fun markWatched(videoId: String, statsUrl: String?) {
        if (YouTube.cookie.isNullOrBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) { WebApi.markWatched(videoId, statsUrl) }
                .onSuccess { Feeds.history.load(force = true) }
                .onFailure { Timber.w(it, "Couldn't add %s to history", videoId) }
        }
    }

    private fun mediaSource(stream: VideoStream): MediaSource {
        if (stream.isHls) {
            val upstream = OkHttpDataSource.Factory(http).setDefaultRequestProperties(stream.headers)
            return HlsMediaSource.Factory(upstream).createMediaSource(MediaItem.fromUri(stream.videoUrl))
        }
        fun part(url: String, length: Long): MediaSource {
            val headers = buildMap {
                putAll(stream.headers)
                // StreamDataSource reads these to fetch the whole stream, in chunks when required.
                if (stream.chunkSize > 0L) put(StreamDataSource.CHUNK, stream.chunkSize.toString())
                if (length > 0L) put(StreamDataSource.LENGTH, length.toString())
            }
            val upstream = OkHttpDataSource.Factory(http).setDefaultRequestProperties(headers)
            return ProgressiveMediaSource.Factory(StreamDataSource.Factory(upstream))
                .createMediaSource(MediaItem.fromUri(url))
        }
        return MergingMediaSource(
            true,
            true,
            part(stream.videoUrl, stream.videoLength),
            part(stream.audioUrl, stream.audioLength),
        )
    }
}
