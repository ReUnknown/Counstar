package app.counstar.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.WebApi
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A list of videos that loads a page at a time: the home feed, subscriptions, history, search,
 * Up next. Loading runs in the feed's own scope, so scrolling (or leaving the screen) never
 * cancels a page halfway and leaves it stuck "loading".
 */
class Feed(
    private val first: suspend () -> Result<String>,
    private val more: suspend (token: String) -> Result<String>,
) {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadedOnce by mutableStateOf(false)
        private set
    /** The filter chips YouTube offers above this feed. */
    var chips by mutableStateOf<List<Chip>>(emptyList())
        private set
    private var continuation: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    val canLoadMore: Boolean get() = continuation != null && !loading && error == null

    /** Loads the first page, unless it's already here (or [force]). */
    fun load(force: Boolean = false) {
        if ((loadedOnce || loading) && !force) return
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            try {
                parse { first() }
                    .onSuccess { page ->
                        items = page.items
                        continuation = page.continuation
                        if (page.chips.isNotEmpty()) chips = page.chips
                        loadedOnce = true
                    }
                    .onFailure { error = "Couldn't load videos. Check your connection and try again." }
            } finally {
                loading = false
            }
        }
    }

    /** Loads the next page, if there is one and nothing is loading. */
    fun loadMore() {
        val token = continuation ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            try {
                parse { more(token) }
                    .onSuccess { page ->
                        val seen = items.mapTo(HashSet()) { it.id }
                        val fresh = page.items.filter { it.id !in seen }
                        items = items + fresh
                        continuation = page.continuation?.takeIf { it != token && fresh.isNotEmpty() }
                    }
                    .onFailure { continuation = null }
            } finally {
                loading = false
            }
        }
    }

    private suspend fun parse(fetch: suspend () -> Result<String>): Result<FeedPage> = withContext(Dispatchers.IO) {
        fetch().mapCatching { YouTubeParser.feed(it) }
    }

    companion object {
        fun browse(id: String) = Feed({ YouTube.rawBrowse(browseId = id) }, { WebApi.browseContinuation(it) })

        /** A feed that starts from a continuation token (a chip's), e.g. a home or Up next filter. */
        fun browseFrom(token: String) = Feed({ WebApi.browseContinuation(token) }, { WebApi.browseContinuation(it) })
        fun nextFrom(token: String) = Feed({ WebApi.nextContinuation(token) }, { WebApi.nextContinuation(it) })

        /** A channel's videos tab, newest first. */
        fun channelVideos(channelId: String) = Feed({ YouTube.rawBrowse(browseId = channelId, params = "EgZ2aWRlb3PyBgQKAjoA") }, { WebApi.browseContinuation(it) })

        /** Up next for a video, starting from the watch page JSON already fetched. */
        fun upNext(watchJson: String) = Feed({ Result.success(watchJson) }, { WebApi.nextContinuation(it) })
    }
}

/** The account's feeds, kept while the app runs so switching tabs doesn't reload them. */
object Feeds {
    var home = Feed.browse("FEwhat_to_watch"); private set
    var subscriptions = Feed.browse("FEsubscriptions"); private set
    var history = Feed.browse("FEhistory"); private set

    /** The home chip that's selected (null: the first one, "All"), and its feed. */
    var homeChip by mutableStateOf<Chip?>(null); private set
    var homeChipFeed by mutableStateOf<Feed?>(null); private set

    fun selectHomeChip(chip: Chip?) {
        val token = chip?.token
        if (chip == null || token == null || chip == home.chips.firstOrNull()) {
            homeChip = null
            homeChipFeed = null
            return
        }
        homeChip = chip
        homeChipFeed = Feed.browseFrom(token)
    }

    fun search(query: String) = Feed({ YouTube.rawSearch(query = query) }, { WebApi.searchContinuation(it) })

    /** Forget everything (signing out). */
    fun clear() {
        home = Feed.browse("FEwhat_to_watch")
        subscriptions = Feed.browse("FEsubscriptions")
        history = Feed.browse("FEhistory")
        homeChip = null
        homeChipFeed = null
    }
}
