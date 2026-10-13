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
    /** Channels in the first page (search results). */
    var channels by mutableStateOf<List<ChannelItem>>(emptyList())
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
                        channels = page.channels
                        loadedOnce = true
                    }
                    .onFailure { error = "Couldn't load videos. Check your connection and try again." }
            } finally {
                loading = false
            }
        }
    }

    /**
     * Pull to refresh. YouTube hands back mostly the same first page for a while, so what you
     * haven't seen yet goes to the top (another page is fetched if there's too little new), and
     * what you've already seen follows.
     */
    suspend fun refresh() {
        val seen = items.mapTo(HashSet()) { it.id }
        job?.cancel()
        loading = true
        error = null
        try {
            val first = parse { first() }.getOrNull() ?: return
            var fresh = first.items.filter { it.id !in seen }
            var next = first.continuation
            var all = first.items
            if (fresh.size < 8 && next != null) {
                parse { more(next!!) }.getOrNull()?.let { page ->
                    all = (all + page.items).distinctBy { it.id }
                    fresh = all.filter { it.id !in seen }
                    next = page.continuation
                }
            }
            items = (fresh + all.filter { it.id in seen }).distinctBy { it.id }
            continuation = next
            if (first.chips.isNotEmpty()) chips = first.chips
            channels = first.channels
            loadedOnce = true
        } finally {
            loading = false
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

        /** A channel tab (Home, Videos, Live, Playlists) by its params. */
        fun channelTab(channelId: String, params: String) = Feed({ YouTube.rawBrowse(browseId = channelId, params = params) }, { WebApi.browseContinuation(it) })

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

    /** Debug preview (not signed in): YouTube has no home feed for guests, so show a search. */
    fun usePreviewHome() {
        home = search("popular videos this week")
    }

    fun search(query: String) = Feed({ YouTube.rawSearch(query = query) }, { WebApi.searchContinuation(it) })

    /** Forget everything (signing out). */
    fun clear() {
        home = Feed.browse("FEwhat_to_watch")
        subscriptions = Feed.browse("FEsubscriptions")
        history = Feed.browse("FEhistory")
        homeChip = null
        homeChipFeed = null
        Library.clear()
    }

    /** A playlist's videos ("WL" is Watch later, "LL" Liked videos). */
    fun playlist(id: String) = Feed.browse("VL$id")
}

/**
 * Your library: playlists, music, courses and podcasts you've made or saved (YouTube's
 * "Playlists" page), and who you're signed in as.
 */
object Library {
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var account by mutableStateOf<Account?>(null)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun load(force: Boolean = false) {
        if ((loaded || loading) && !force) return
        loading = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { WebApi.accountMenu().mapCatching(YouTubeParser::account) }
                    .onSuccess { account = it }
                // The Playlists page first; older accounts' library page if that comes back empty.
                var found = emptyList<Playlist>()
                for (page in listOf("FEplaylist_aggregation", "FElibrary", "FEyou")) {
                    found = withContext(Dispatchers.IO) { WebApi.browse(page).mapCatching { YouTubeParser.playlists(it).first } }
                        .getOrDefault(emptyList())
                    if (found.isNotEmpty()) break
                }
                playlists = found
                loaded = true
            } finally {
                loading = false
            }
        }
    }

    fun clear() {
        playlists = emptyList()
        account = null
        loaded = false
    }
}

/** A channel page: its header, then the tabs' feeds as they're opened. */
@androidx.compose.runtime.Stable
class ChannelPage(val id: String) {
    var info by mutableStateOf<ChannelInfo?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var about by mutableStateOf<ChannelAbout?>(null)
        private set

    fun loadAbout() {
        val token = info?.aboutToken ?: return
        if (about != null) return
        scope.launch {
            withContext(Dispatchers.IO) { WebApi.browseContinuation(token).mapCatching(YouTubeParser::about) }
                .onSuccess { about = it }
        }
    }

    /** Playlists on the Playlists tab. */
    var playlists by mutableStateOf<List<Playlist>?>(null)
        private set
    val home = Feed.channelTab(id, "EghmZWF0dXJlZPIGBAoCMgA=")
    val videos = Feed.channelVideos(id)
    val live = Feed.channelTab(id, "EgdzdHJlYW1z8gYECgJ6AA==")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun load() {
        if (info != null) return
        scope.launch {
            withContext(Dispatchers.IO) { YouTube.rawBrowse(browseId = id).mapCatching { YouTubeParser.channel(id, it) } }
                .onSuccess { info = it; Channels.remember(it) }
                .onFailure { failed = true }
        }
    }

    fun loadPlaylists() {
        if (playlists != null) return
        scope.launch {
            playlists = withContext(Dispatchers.IO) {
                YouTube.rawBrowse(browseId = id, params = "EglwbGF5bGlzdHPyBgQKAkIA").mapCatching { YouTubeParser.playlists(it).first }
            }.getOrDefault(emptyList())
        }
    }
}

/** Channel headers already fetched (search cards show banners without asking twice). */
object Channels {
    private val known = androidx.compose.runtime.mutableStateMapOf<String, ChannelInfo>()
    private val pages = HashMap<String, ChannelPage>()

    fun remember(info: ChannelInfo) {
        known[info.id] = info
    }

    fun info(id: String): ChannelInfo? = known[id]

    /** The page for a channel, kept so going back and forth doesn't reload it. */
    fun page(id: String): ChannelPage = pages.getOrPut(id) { ChannelPage(id) }

    /** Your subscriptions, all of them (YouTube's channel list). */
    var subscriptions by mutableStateOf<List<ChannelItem>?>(null)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadSubscriptions(force: Boolean = false) {
        if (subscriptions != null && !force) return
        scope.launch {
            subscriptions = withContext(Dispatchers.IO) { WebApi.browse("FEchannels").mapCatching { YouTubeParser.feed(it).channels } }
                .getOrDefault(emptyList())
        }
    }
}
