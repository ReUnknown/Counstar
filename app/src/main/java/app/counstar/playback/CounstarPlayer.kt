package app.counstar.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import app.counstar.data.CaptionTrack
import app.counstar.data.Cue
import app.counstar.data.Feeds
import app.counstar.data.YouTubeParser
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
    /** Qualities this video comes in, tallest first, and the one playing. */
    var heights by mutableStateOf<List<Int>>(emptyList())
        private set
    var height by mutableStateOf<Int?>(null)
        private set
    /** Picked for this video (null: automatic, up to the setting). */
    var pickedHeight by mutableStateOf<Int?>(null)
        private set
    var speed by mutableStateOf(1f)
        private set
    var loop by mutableStateOf(false)
        private set
    /** The video's caption tracks, the one showing (null: off), and its lines. */
    var captionTracks by mutableStateOf<List<CaptionTrack>>(emptyList())
        private set
    var caption by mutableStateOf<CaptionTrack?>(null)
        private set
    var cues by mutableStateOf<List<Cue>>(emptyList())
        private set
    private var captionJob: Job? = null
    /** The language captions were last turned on in, to pick it again on the next video. */
    private var preferredLanguage: String? = null

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

    /** Plays [item], from [startAtMs] (a link's "t="), or carries on if it's already loaded. */
    fun play(item: VideoItem, startAtMs: Long = 0L) {
        if (current?.id == item.id) {
            if (startAtMs > 0L) player.seekTo(startAtMs)
            player.play()
            return
        }
        load(item, startAt = startAtMs, isRetry = false)
    }

    /** Plays this video at [h] (null: automatic), from where it is now. */
    fun pickQuality(h: Int?) {
        val item = current ?: return
        pickedHeight = h
        StreamResolver.heightOverride = h
        load(item, startAt = player.currentPosition, isRetry = true)
    }

    fun changeSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
    }

    /** Speeds up while a finger holds the video, then back to the chosen speed. */
    fun holdFast(on: Boolean) {
        player.setPlaybackSpeed(if (on) 2f else speed)
    }

    fun changeLoop(on: Boolean) {
        loop = on
        player.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Shows captions from [track] (null: off). */
    fun showCaptions(track: CaptionTrack?) {
        captionJob?.cancel()
        caption = track
        cues = emptyList()
        if (track == null) {
            preferredLanguage = null
            return
        }
        preferredLanguage = track.language
        captionJob = scope.launch {
            withContext(Dispatchers.IO) { WebApi.fetch(track.url.replace("&fmt=srv3", "") + "&fmt=json3").mapCatching(YouTubeParser::cues) }
                .onSuccess { cues = it }
                .onFailure { Timber.w(it, "Couldn't load captions") }
        }
    }

    /** The caption tracks for [videoId], and captions back on if they were on before. */
    private fun loadCaptionTracks(videoId: String) {
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { WebApi.captionsPlayer(videoId).mapCatching(YouTubeParser::captionTracks) }.getOrDefault(emptyList())
            if (current?.id != videoId) return@launch
            captionTracks = tracks
            val language = preferredLanguage ?: return@launch
            (tracks.firstOrNull { it.language == language && !it.auto } ?: tracks.firstOrNull { it.language == language })?.let(::showCaptions)
        }
    }

    fun stop() {
        loadJob?.cancel()
        captionJob?.cancel()
        captionTracks = emptyList()
        caption = null
        cues = emptyList()
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
        if (current?.id != item.id) {
            storyboard = null
            heights = emptyList()
            height = null
            pickedHeight = null
            StreamResolver.heightOverride = null
            captionTracks = emptyList()
            captionJob?.cancel()
            caption = null
            cues = emptyList()
            loadCaptionTracks(item.id)
        }
        current = item
        error = null
        loading = true
        player.stop()
        loadJob = scope.launch {
            val stream = withContext(Dispatchers.IO) { StreamResolver.resolve(item.id) }
            stream
                .onSuccess {
                    storyboard = Storyboard.parse(it.storyboardSpec)
                    if (it.heights.isNotEmpty()) heights = it.heights
                    height = it.height
                    player.setMediaSource(mediaSource(it, item), startAt)
                    player.setPlaybackSpeed(speed)
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

    private fun mediaSource(stream: VideoStream, item: VideoItem): MediaSource {
        // What the notification and lock screen show while it plays.
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.channelName)
            .setArtworkUri(item.thumbnail.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .build()
        fun mediaItem(url: String, described: Boolean) = MediaItem.Builder()
            .setUri(url)
            .setMediaId(item.id)
            .apply { if (described) setMediaMetadata(metadata) }
            .build()
        if (stream.isHls) {
            val upstream = OkHttpDataSource.Factory(http).setDefaultRequestProperties(stream.headers)
            return HlsMediaSource.Factory(upstream).createMediaSource(mediaItem(stream.videoUrl, described = true))
        }
        fun part(url: String, length: Long, described: Boolean): MediaSource {
            val headers = buildMap {
                putAll(stream.headers)
                // StreamDataSource reads these to fetch the whole stream, in chunks when required.
                if (stream.chunkSize > 0L) put(StreamDataSource.CHUNK, stream.chunkSize.toString())
                if (length > 0L) put(StreamDataSource.LENGTH, length.toString())
            }
            val upstream = OkHttpDataSource.Factory(http).setDefaultRequestProperties(headers)
            return ProgressiveMediaSource.Factory(StreamDataSource.Factory(upstream))
                .createMediaSource(mediaItem(url, described))
        }
        // The merged source takes its item (and so what the notification shows) from the first part.
        return MergingMediaSource(
            true,
            true,
            part(stream.videoUrl, stream.videoLength, described = true),
            part(stream.audioUrl, stream.audioLength, described = false),
        )
    }
}
