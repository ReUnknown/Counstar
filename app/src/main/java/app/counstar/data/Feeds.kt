package app.counstar.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A list of videos that loads a page at a time: the home feed, subscriptions, history, search.
 * [fetch] gets one page's JSON: the first page for a null token, else the page for that token.
 */
class Feed(private val fetch: suspend (continuation: String?) -> Result<String>) {
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
    private val lock = Mutex()

    val canLoadMore: Boolean get() = continuation != null && !loading && error == null

    /** Loads the first page, unless it's already here (or [force]). */
    suspend fun load(force: Boolean = false) = lock.withLock {
        if (loadedOnce && !force) return@withLock
        loading = true
        error = null
        fetchPage(null)
            .onSuccess { page ->
                items = page.items
                continuation = page.continuation
                if (page.chips.isNotEmpty()) chips = page.chips
                loadedOnce = true
            }
            .onFailure { error = "Couldn't load videos. Check your connection and try again." }
        loading = false
    }

    suspend fun loadMore() = lock.withLock {
        val token = continuation ?: return@withLock
        if (loading) return@withLock
        loading = true
        fetchPage(token)
            .onSuccess { page ->
                val seen = items.mapTo(HashSet()) { it.id }
                items = items + page.items.filter { it.id !in seen }
                continuation = page.continuation?.takeIf { it != token && page.items.isNotEmpty() }
            }
            .onFailure { continuation = null }
        loading = false
    }

    private suspend fun fetchPage(token: String?): Result<FeedPage> = withContext(Dispatchers.IO) {
        fetch(token).mapCatching { YouTubeParser.feed(it) }
    }
}

/** The account's feeds, kept while the app runs so switching tabs doesn't reload them. */
object Feeds {
    var home = homeFeed(); private set
    var subscriptions = subscriptionsFeed(); private set
    var history = historyFeed(); private set

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
        homeChipFeed = Feed { next -> YouTube.rawBrowse(continuation = next ?: token) }
    }

    fun search(query: String) = Feed { token ->
        if (token == null) YouTube.rawSearch(query = query) else YouTube.rawSearch(continuation = token)
    }

    /** Forget everything (signing out). */
    fun clear() {
        home = homeFeed()
        subscriptions = subscriptionsFeed()
        history = historyFeed()
        homeChip = null
        homeChipFeed = null
    }

    private fun browse(id: String) = Feed { token ->
        if (token == null) YouTube.rawBrowse(browseId = id) else YouTube.rawBrowse(continuation = token)
    }

    private fun homeFeed() = browse("FEwhat_to_watch")
    private fun subscriptionsFeed() = browse("FEsubscriptions")
    private fun historyFeed() = browse("FEhistory")
}
