package com.sami.livetv.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.sami.livetv.R
import com.sami.livetv.data.channels.ChannelRepository
import com.sami.livetv.data.cloud.CloudAccountRepository
import com.sami.livetv.data.cloud.CloudLibrary
import com.sami.livetv.data.cloud.synchronizeAccountLibrary
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.data.preferences.SettingsStore
import com.sami.livetv.domain.model.ChannelCategory
import com.sami.livetv.domain.model.ChannelCatalog
import com.sami.livetv.domain.model.StreamSource
import com.sami.livetv.domain.model.TvChannel
import com.sami.livetv.playback.PlaybackService
import com.sami.livetv.ui.account.AccountSheet
import com.sami.livetv.ui.localization.localizedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private sealed interface BrowserFilter {
    data object Home : BrowserFilter
    data object All : BrowserFilter
    data object Favorites : BrowserFilter
    data object History : BrowserFilter
    data class Category(val categoryId: String) : BrowserFilter
}

private data class CatalogEntry(
    val category: ChannelCategory,
    val channel: TvChannel,
)

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ChannelBrowserScreen(
    repository: ChannelRepository,
    settingsStore: SettingsStore,
    cloudAccountRepository: CloudAccountRepository,
    playbackController: MediaController?,
    darkTheme: Boolean,
    language: AppLanguage,
    onPlayStream: (TvChannel, StreamSource, String, String) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
) {
    val favoriteIds by settingsStore.favoriteIds.collectAsStateWithLifecycle(
        initialValue = emptySet(),
    )
    val watchHistory by settingsStore.watchHistory.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val cloudUser by cloudAccountRepository.currentUser.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<ChannelCatalog?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<BrowserFilter>(BrowserFilter.Home) }
    var selectedEntry by remember { mutableStateOf<CatalogEntry?>(null) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var playingChannelId by remember(playbackController) {
        mutableStateOf(
            playbackController?.mediaMetadata?.extras
                ?.getString(PlaybackService.EXTRA_CHANNEL_ID),
        )
    }
    var playingStreamId by remember(playbackController) {
        mutableStateOf(playbackController?.currentMediaItem?.mediaId)
    }
    var isPlaying by remember(playbackController) {
        mutableStateOf(playbackController?.isPlaying == true)
    }

    androidx.compose.runtime.DisposableEffect(playbackController) {
        val listener = object : Player.Listener {
            private fun refresh() {
                playingChannelId = playbackController?.mediaMetadata?.extras
                    ?.getString(PlaybackService.EXTRA_CHANNEL_ID)
                playingStreamId = playbackController?.currentMediaItem?.mediaId
                isPlaying = playbackController?.isPlaying == true
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) = refresh()

            override fun onMediaMetadataChanged(
                mediaMetadata: androidx.media3.common.MediaMetadata,
            ) = refresh()

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                refresh()
            }

            override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        }
        playbackController?.addListener(listener)
        onDispose {
            playbackController?.removeListener(listener)
        }
    }

    LaunchedEffect(repository, reloadToken) {
        isLoading = true
        loadFailed = false
        try {
            catalog = repository.loadBundledCatalog()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            catalog = null
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    val cloudSyncErrorMessage = localizedString(R.string.account_action_failed, language)
    LaunchedEffect(cloudAccountRepository, settingsStore, cloudUser?.id) {
        val accountId = cloudUser?.id ?: return@LaunchedEffect
        try {
            synchronizeAccountLibrary(settingsStore, cloudAccountRepository)
            combine(
                settingsStore.favoriteIds.distinctUntilChanged(),
                settingsStore.watchHistory.distinctUntilChanged(),
            ) { localFavorites, localHistory ->
                CloudLibrary(
                    favoriteIds = localFavorites,
                    watchHistory = localHistory,
                )
            }.drop(1).collectLatest { library ->
                try {
                    cloudAccountRepository.saveLibrary(library)
                    settingsStore.saveSyncedLibraryBaseline(
                        accountId = accountId,
                        favoriteIds = library.favoriteIds,
                        watchHistory = library.watchHistory,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar(cloudSyncErrorMessage)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            snackbarHostState.showSnackbar(cloudSyncErrorMessage)
        }
    }

    val catalogEntries = remember(catalog) {
        catalog?.categories.orEmpty().flatMap { category ->
            category.channels.map { channel -> CatalogEntry(category, channel) }
        }
    }
    val visibleEntries = remember(
        catalogEntries,
        selectedFilter,
        favoriteIds,
        watchHistory,
        searchQuery,
    ) {
        val scopedEntries = when (val filter = selectedFilter) {
            BrowserFilter.Home, BrowserFilter.All -> catalogEntries
            BrowserFilter.Favorites -> catalogEntries.filter { it.channel.id in favoriteIds }
            BrowserFilter.History -> watchHistory.mapNotNull { channelId ->
                catalogEntries.firstOrNull { it.channel.id == channelId }
            }
            is BrowserFilter.Category -> catalogEntries.filter {
                it.category.id == filter.categoryId
            }
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            scopedEntries
        } else {
            scopedEntries.filter { entry ->
                entry.channel.name.arabic.contains(query, ignoreCase = true) ||
                    entry.channel.name.english.contains(query, ignoreCase = true) ||
                    entry.category.name.arabic.contains(query, ignoreCase = true) ||
                    entry.category.name.english.contains(query, ignoreCase = true)
            }
        }
    }
    val saveErrorMessage = localizedString(R.string.save_error, language)
    val themeContentDescription = localizedString(
        if (darkTheme) R.string.toggle_theme_dark else R.string.toggle_theme_light,
        language,
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = localizedString(R.string.app_name, language),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = localizedString(R.string.tagline, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAccountSheet = true },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = localizedString(R.string.account_title, language),
                        )
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.semantics {
                            contentDescription = themeContentDescription
                        },
                    ) {
                        Text(
                            text = if (darkTheme) "☀" else "☾",
                            fontSize = 21.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onToggleLanguage) {
                        Text(localizedString(R.string.toggle_language, language))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(localizedString(R.string.search_hint, language))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = localizedString(R.string.search_hint, language),
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        TextButton(onClick = { searchQuery = "" }) {
                            Text(localizedString(R.string.clear_search, language))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            if (catalog != null) {
                FilterRow(
                    categories = catalog!!.categories,
                    selectedFilter = selectedFilter,
                    language = language,
                    onSelect = { selectedFilter = it },
                )
            }

            when {
                isLoading -> LoadingState(language)
                loadFailed || catalog == null -> ErrorState(
                    language = language,
                    onRetry = { reloadToken++ },
                )
                selectedFilter == BrowserFilter.Home && searchQuery.isBlank() -> CategoryGrid(
                    categories = catalog!!.categories,
                    darkTheme = darkTheme,
                    language = language,
                    onOpen = { categoryId -> selectedFilter = BrowserFilter.Category(categoryId) },
                )
                visibleEntries.isEmpty() -> EmptyState(
                    filter = selectedFilter,
                    searchQuery = searchQuery,
                    language = language,
                )
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedFilter is BrowserFilter.Category) {
                            TextButton(onClick = { selectedFilter = BrowserFilter.Home }) {
                                Text(localizedString(R.string.back_to_categories, language))
                            }
                        }
                        Text(
                            text = filterTitle(
                                filter = selectedFilter,
                                categories = catalog!!.categories,
                                language = language,
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = localizedString(
                                R.string.channel_count,
                                language,
                                visibleEntries.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 20.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        gridItems(
                            items = visibleEntries,
                            key = { entry -> entry.channel.id },
                        ) { entry ->
                            ChannelCard(
                                category = entry.category,
                                channel = entry.channel,
                                darkTheme = darkTheme,
                                language = language,
                                isFavorite = entry.channel.id in favoriteIds,
                                onOpen = {
                                    selectedEntry = entry
                                },
                                onToggleFavorite = {
                                    val shouldFavorite = entry.channel.id !in favoriteIds
                                    coroutineScope.launch {
                                        try {
                                            settingsStore.setFavorite(
                                                channelId = entry.channel.id,
                                                favorite = shouldFavorite,
                                            )
                                            try {
                                                cloudAccountRepository.recordFavoriteChanged()
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                // The local change is saved; library sync can retry later.
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(saveErrorMessage)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        selectedEntry?.let { entry ->
            ChannelDetailsSheet(
                category = entry.category,
                channel = entry.channel,
                language = language,
                darkTheme = darkTheme,
                cloudAccountRepository = cloudAccountRepository,
                playbackController = playbackController,
                isCurrentChannel = playingChannelId == entry.channel.id,
                currentStreamId = playingStreamId,
                isPlaying = isPlaying,
                onPlayStream = { source ->
                    val languageTag = if (language == AppLanguage.English) "en" else "ar"
                    onPlayStream(
                        entry.channel,
                        source,
                        entry.channel.name.resolve(languageTag),
                        languageTag,
                    )
                },
                onEnterPictureInPicture = onEnterPictureInPicture,
                onOpenAccount = {
                    selectedEntry = null
                    showAccountSheet = true
                },
                onDismiss = { selectedEntry = null },
            )
        }

        if (showAccountSheet) {
            AccountSheet(
                repository = cloudAccountRepository,
                settingsStore = settingsStore,
                language = language,
                onDismiss = { showAccountSheet = false },
            )
        }
    }
}

@Composable
private fun FilterRow(
    categories: List<ChannelCategory>,
    selectedFilter: BrowserFilter,
    language: AppLanguage,
    onSelect: (BrowserFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "home") {
            FilterChip(
                selected = selectedFilter == BrowserFilter.Home,
                onClick = { onSelect(BrowserFilter.Home) },
                label = { Text(localizedString(R.string.section_home, language)) },
            )
        }
        item(key = "all") {
            FilterChip(
                selected = selectedFilter == BrowserFilter.All,
                onClick = { onSelect(BrowserFilter.All) },
                label = { Text(localizedString(R.string.section_all, language)) },
            )
        }
        item(key = "favorites") {
            FilterChip(
                selected = selectedFilter == BrowserFilter.Favorites,
                onClick = { onSelect(BrowserFilter.Favorites) },
                label = { Text(localizedString(R.string.section_favorites, language)) },
            )
        }
        item(key = "history") {
            FilterChip(
                selected = selectedFilter == BrowserFilter.History,
                onClick = { onSelect(BrowserFilter.History) },
                label = { Text(localizedString(R.string.section_history, language)) },
            )
        }
        lazyRowItems(categories, key = { category -> category.id }) { category ->
            val languageTag = if (language == AppLanguage.English) "en" else "ar"
            val label = buildString {
                category.icon?.let { append("$it ") }
                append(category.name.resolve(languageTag))
            }
            val categoryFilter = BrowserFilter.Category(category.id)
            FilterChip(
                selected = selectedFilter == categoryFilter,
                onClick = { onSelect(categoryFilter) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun LoadingState(language: AppLanguage) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Text(
                text = localizedString(R.string.catalog_loading, language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(
    language: AppLanguage,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = localizedString(R.string.catalog_load_error, language),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(localizedString(R.string.retry, language))
            }
        }
    }
}

@Composable
private fun EmptyState(
    filter: BrowserFilter,
    searchQuery: String,
    language: AppLanguage,
) {
    val (titleId, bodyId) = when {
        searchQuery.isNotBlank() -> R.string.no_search_results to R.string.no_search_results_body
        filter == BrowserFilter.Favorites -> R.string.empty_favorites to R.string.empty_favorites_body
        filter == BrowserFilter.History -> R.string.empty_history to R.string.empty_history_body
        else -> R.string.empty_category to R.string.empty_category
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = localizedString(titleId, language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (bodyId != titleId) {
                Text(
                    text = localizedString(bodyId, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun filterTitle(
    filter: BrowserFilter,
    categories: List<ChannelCategory>,
    language: AppLanguage,
): String {
    val languageTag = if (language == AppLanguage.English) "en" else "ar"
    return when (filter) {
        BrowserFilter.Home -> localizedString(R.string.section_home, language)
        BrowserFilter.All -> localizedString(R.string.section_all, language)
        BrowserFilter.Favorites -> localizedString(R.string.section_favorites, language)
        BrowserFilter.History -> localizedString(R.string.section_history, language)
        is BrowserFilter.Category -> categories
            .firstOrNull { it.id == filter.categoryId }
            ?.name
            ?.resolve(languageTag)
            ?: localizedString(R.string.section_all, language)
    }
}
