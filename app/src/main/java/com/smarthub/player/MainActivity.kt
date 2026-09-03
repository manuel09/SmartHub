package com.smarthub.player

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.smarthub.player.data.model.Movie
import com.smarthub.player.data.model.Episode
import com.smarthub.player.data.samples.SampleData
import com.smarthub.player.ui.components.*
import com.smarthub.player.ui.screens.*
import com.smarthub.player.ui.theme.VixStreamTheme

import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthub.player.ui.MovieViewModel
import com.smarthub.player.ui.theme.VixRed
import com.smarthub.player.ui.WindowSize
import com.smarthub.player.ui.LocalWindowSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.smarthub.player.ui.components.MovieCard
import com.smarthub.player.data.proxy.ProxyService
import com.smarthub.player.ui.components.SourcePickerDialog
import com.smarthub.player.data.model.StreamItem
import android.util.Log
import androidx.compose.animation.Crossfade

enum class Screen {
    Home, Search, TV, Movies, Series, Discover, Profile, Detail, PLAYER
}


class MainActivity : ComponentActivity() {

    companion object {
        var onKeyEventListener: ((android.view.KeyEvent) -> Boolean)? = null
        var onPiPModeChangedListener: ((Boolean) -> Unit)? = null
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        onPiPModeChangedListener?.invoke(isInPictureInPictureMode)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val handled = onKeyEventListener?.invoke(event) ?: false
        return if (handled) true else super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val serviceIntent = Intent(context, ProxyService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("MainActivity", "ProxyService started successfully.")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start ProxyService: ${e.message}", e)
        }

        // Verify proxy binding after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val checkClient = okhttp3.OkHttpClient.Builder()
                    .proxy(java.net.Proxy.NO_PROXY)
                    .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(com.smarthub.player.data.api.ProxyConfig.EASY_PROXY_URL)
                    .build()
                val response = checkClient.newCall(request).execute()
                Log.d("MainActivity", "Proxy health check: ${response.code} - ${response.body?.string()}")
                response.close()
            } catch (e: Exception) {
                Log.e("MainActivity", "Proxy health check FAILED: ${e.message}")
            }
        }, 3000)

        setContent {
            VixStreamTheme {
                VixApp()
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VixApp(viewModel: MovieViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var previousScreen by remember { mutableStateOf(Screen.Home) }
    var currentEpisode by remember { mutableStateOf<Episode?>(null) }
    var currentEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var playSessionKey by remember { mutableStateOf(0) }
    var pendingResumePos by remember { mutableStateOf(0L) }

    // Stack di navigazione: l'ultimo elemento è lo schermo corrente.
    var navStack by remember { mutableStateOf(listOf(Screen.Home)) }

    val streamUrl by viewModel.streamUrl.collectAsState()
    val streamError by viewModel.streamError.collectAsState()
    val availableStreams by viewModel.availableStreams.collectAsState()

    // Controllo aggiornamenti all'avvio
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    // Show source picker when streams are loaded
    var showPicker by remember { mutableStateOf(false) }
    LaunchedEffect(availableStreams) {
        if (availableStreams.isNotEmpty() && streamUrl == null) {
            showPicker = true
        }
    }
    LaunchedEffect(streamUrl) {
        if (streamUrl != null) showPicker = false
    }

    val config = LocalConfiguration.current
    val context = LocalContext.current
    val screenWidthDp = config.screenWidthDp
    val isTv = com.smarthub.player.ui.TvUtils.isTelevision(context)
    val windowSize = WindowSize(
        widthClass = when {
            isTv -> WindowWidthSizeClass.Expanded
            screenWidthDp < 600 -> WindowWidthSizeClass.Compact
            screenWidthDp < 840 -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        },
        heightClass = WindowHeightSizeClass.Compact
    )

    // Navigate to player when stream URL is ready
    LaunchedEffect(streamUrl) {
        if (streamUrl != null) {
            if (currentScreen != Screen.PLAYER) {
                previousScreen = currentScreen
                navStack = navStack + Screen.PLAYER
            }
            currentScreen = Screen.PLAYER
        } else if (currentScreen == Screen.PLAYER) {
            currentScreen = previousScreen
            navStack = navStack.dropLast(1)
        }
    }

    // Update currentEpisodes when viewModel.episodes changes
    val episodes by viewModel.episodes.collectAsState()
    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty()) {
            currentEpisodes = episodes
        }
    }

    // Back gesture handling
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BackHandler(enabled = currentScreen != Screen.Home) {
        // Clear focus before navigating to prevent IME crash on TV/Firestick
        focusManager.clearFocus()
        keyboardController?.hide()
        when (currentScreen) {
            Screen.Detail -> {
                viewModel.clearStream()
                val target = if (navStack.size > 1) navStack[navStack.size - 2] else Screen.Home
                navStack = navStack.dropLast(1)
                previousScreen = target
                currentScreen = target
            }
            Screen.PLAYER -> {
                viewModel.clearStream()
                val target = if (navStack.size > 1) navStack[navStack.size - 2] else Screen.Home
                navStack = navStack.dropLast(1)
                previousScreen = target
                currentScreen = target
            }
            else -> {
                currentScreen = Screen.Home
                navStack = listOf(Screen.Home)
            }
        }
    }

    if (BuildConfig.TMDB_API_KEY.isEmpty() || BuildConfig.TMDB_API_KEY == "YOUR_API_KEY_HERE") {
        ApiKeyWarning()
        return
    }

    CompositionLocalProvider(LocalWindowSize provides windowSize) {
        // Update dialog (shown over any screen)
        val updateInfo by viewModel.updateInfo.collectAsState()
        val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
        updateInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdate() },
                containerColor = Color(0xFF1F1F1F),
                title = { Text("Aggiornamento disponibile", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Versione ${info.versionName}",
                            color = VixRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (info.changelog.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                info.changelog,
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        if (isDownloadingUpdate) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = VixRed,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Scaricamento in corso...", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.downloadUpdate() }, enabled = !isDownloadingUpdate) {
                        Text(if (isDownloadingUpdate) "Scaricamento..." else "Scarica", color = VixRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdate() }, enabled = !isDownloadingUpdate) {
                        Text("Più tardi", color = Color.Gray)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Error dialog shown over any screen
        streamError?.let { error ->
            val expired by viewModel.altadefinizioneExpiredError.collectAsState()
            AlertDialog(
                onDismissRequest = { viewModel.clearStreamError() },
                containerColor = Color(0xFF1F1F1F),
                title = { Text(if (expired) "Sessione scaduta" else "Impossibile riprodurre", color = Color.White) },
                text = { Text(error, color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearStreamError() }) {
                        Text(if (expired) "Dopo" else "OK", color = if (expired) Color.Gray else VixRed)
                    }
                },
                dismissButton = if (expired) {
                    {
                        TextButton(onClick = {
                            viewModel.clearStreamError()
                            currentScreen = Screen.Profile
                            previousScreen = Screen.Profile
                            navStack = listOf(Screen.Profile)
                        }) {
                            Text("Vai al Profilo", color = VixRed)
                        }
                    }
                } else null
            )
        }

        // Source picker dialog
        if (showPicker && availableStreams.isNotEmpty() && streamError == null) {
            SourcePickerDialog(
                streams = availableStreams,
                onSelect = {
                    viewModel.selectStream(it)
                    showPicker = false
                },
                onDismiss = {
                    viewModel.clearStream()
                    showPicker = false
                }
            )
        }

        val showNav = currentScreen != Screen.Detail && currentScreen != Screen.PLAYER
        val selectedNavIndex = when (currentScreen) {
            Screen.Home -> 0
            Screen.Search -> 1
            Screen.Movies -> 2
            Screen.Series -> 3
            Screen.Discover -> 5
            Screen.Profile -> 4
            else -> 0
        }
        val onNavSelected: (Int) -> Unit = { index: Int ->
            val target = when (index) {
                0 -> Screen.Home
                1 -> Screen.Search
                2 -> Screen.Movies
                3 -> Screen.Series
                4 -> Screen.Profile
                5 -> Screen.Discover
                else -> Screen.Home
            }
            viewModel.clearStream()
            currentScreen = target
            previousScreen = target
            navStack = listOf(target)
        }

        Scaffold(
            containerColor = Color(0xFF0F0F0F),
            bottomBar = {
                if (windowSize.isCompact && showNav) {
                    BottomNavBar(
                        selectedItem = selectedNavIndex,
                        onItemSelected = onNavSelected
                    )
                }
            }
        ) { paddingValues ->
            if (windowSize.isCompact) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    AppContent(
                        currentScreen = currentScreen,
                        searchQuery = searchQuery,
                        selectedMovie = selectedMovie,
                        currentEpisode = currentEpisode,
                        currentEpisodes = currentEpisodes,
                        playSessionKey = playSessionKey,
                        viewModel = viewModel,
                        onMovieSelect = { movie ->
                            selectedMovie = movie
                            viewModel.fetchCast(movie.id)
                            if (movie.title == null) viewModel.fetchTvDetails(movie.id)
                            previousScreen = currentScreen
                            navStack = navStack + Screen.Detail
                            currentScreen = Screen.Detail
                        },
                        onSearchQueryChange = {
                            searchQuery = it
                            if (it.length > 2) viewModel.search(it) else viewModel.clearSearch()
                        },
                        onPlay = { m, e ->
                            currentEpisode = e
                            if (e != null) viewModel.fetchSeasonEpisodes(m.id, e.seasonNumber)
                            viewModel.fetchStreamUrl(m.id, (m.title ?: m.name ?: ""), e)
                        },
                        onBackToHome = {
                            viewModel.clearStream()
                            viewModel.reloadLocalData()
                            currentScreen = Screen.Home
                            navStack = listOf(Screen.Home)
                        },
                        onClosePlayer = { viewModel.clearStream() },
                        onEpisodeSelected = { episode ->
                            currentEpisode = episode
                            playSessionKey++
                            val name = selectedMovie?.title ?: selectedMovie?.name ?: ""
                            selectedMovie?.let { m ->
                                viewModel.fetchStreamUrl(m.id, name, episode)
                            }
                        },
                        onBackToDetail = { currentScreen = Screen.Detail }
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    if (showNav) {
                        TabletNavRail(
                            selectedItem = selectedNavIndex,
                            onItemSelected = onNavSelected
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppContent(
                            currentScreen = currentScreen,
                            searchQuery = searchQuery,
                            selectedMovie = selectedMovie,
                            currentEpisode = currentEpisode,
                            currentEpisodes = currentEpisodes,
                            playSessionKey = playSessionKey,
                            viewModel = viewModel,
                            onMovieSelect = { movie ->
                                selectedMovie = movie
                                viewModel.fetchCast(movie.id)
                                if (movie.title == null) viewModel.fetchTvDetails(movie.id)
                                previousScreen = currentScreen
                                currentScreen = Screen.Detail
                            },
                            onSearchQueryChange = {
                                searchQuery = it
                                if (it.length > 2) viewModel.search(it) else viewModel.clearSearch()
                            },
                            onPlay = { m, e ->
                                currentEpisode = e
                                if (e != null) viewModel.fetchSeasonEpisodes(m.id, e.seasonNumber)
                                viewModel.fetchStreamUrl(m.id, (m.title ?: m.name ?: ""), e)
                            },
                            onBackToHome = {
                                viewModel.clearStream()
                                viewModel.reloadLocalData()
                                currentScreen = Screen.Home
                            },
                            onClosePlayer = { viewModel.clearStream() },
                            onEpisodeSelected = { episode ->
                                currentEpisode = episode
                                playSessionKey++
                                val name = selectedMovie?.title ?: selectedMovie?.name ?: ""
                                selectedMovie?.let { m ->
                                    viewModel.fetchStreamUrl(m.id, name, episode)
                                }
                            },
                            onBackToDetail = { currentScreen = Screen.Detail }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeyWarning() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Configurazione Necessaria",
                style = MaterialTheme.typography.displayMedium,
                color = VixRed
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Inserisci la tua API Key di TMDB nel file local.properties per far funzionare l'app.",
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "tmdb.api.key=latuachiave",
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun HomeScreenContent(
    sections: List<Pair<String, List<Movie>>>,
    viewModel: MovieViewModel,
    onMovieClick: (Movie) -> Unit,
    isLoading: Boolean = false
) {
    val sectionMap = linkedMapOf<String, List<Movie>>()
    sections.forEach { (name, list) ->
        if (list.isNotEmpty()) sectionMap[name] = list
    }
    val trendingMovies = sections.firstOrNull()?.second ?: emptyList()
    val featuredMovie = trendingMovies.firstOrNull() ?: SampleData.featuredMovie

    HomeScreen(
        featuredMovie = featuredMovie,
        sections = sectionMap,
        onMovieClick = onMovieClick,
        viewModel = viewModel,
        isLoading = isLoading
    )
}

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    searchHistory: List<com.smarthub.player.data.local.SearchHistoryItem> = emptyList(),
    onSearchSubmit: (String) -> Unit = {},
    onRemoveHistoryItem: (String) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val showHistory = isFocused && query.isEmpty() && searchHistory.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .onFocusChanged { isFocused = it.isFocused },
            placeholder = { Text("Cerca film o serie...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        isFocused = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancella", tint = Color.Gray)
                    }
                } else {
                    // empty - no icon
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VixRed,
                unfocusedBorderColor = Color.Gray,
                cursorColor = VixRed,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        if (showHistory) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ricerche recenti", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearHistory) {
                    Text("Cancella", color = VixRed, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            searchHistory.take(10).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, null, tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.query,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemoveHistoryItem(item.query) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Rimuovi", tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Spacer(Modifier.height(8.dp))
        }

        if (!showHistory || results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { movie ->
                    MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}

@Composable
fun TabletNavRail(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 8.dp),
        containerColor = Color(0xFF0F0F0F),
        header = {
            FloatingActionButton(
                onClick = { onItemSelected(0) },
                modifier = Modifier.padding(vertical = 12.dp),
                containerColor = VixRed,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Home", tint = Color.White)
            }
        }
    ) {
        val items = listOf(
            Triple("Home", Icons.Default.Home, 0),
            Triple("Cerca", Icons.Default.Search, 1),
            Triple("Film", Icons.Default.PlayArrow, 2),
            Triple("Serie", Icons.Default.List, 3),
            Triple("Profilo", Icons.Default.Person, 4)
        )
        Spacer(modifier = Modifier.weight(1f))
        items.forEach { (label, icon, index) ->
            NavigationRailItem(
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 10.sp) },
                selected = selectedItem == index,
                onClick = { onItemSelected(index) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = VixRed,
                    unselectedIconColor = Color.Gray.copy(alpha = 0.6f),
                    selectedTextColor = VixRed,
                    unselectedTextColor = Color.Gray.copy(alpha = 0.6f),
                    indicatorColor = Color.Transparent
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppContent(
    currentScreen: Screen,
    searchQuery: String,
    selectedMovie: Movie?,
    currentEpisodes: List<Episode>,
    currentEpisode: Episode?,
    playSessionKey: Int,
    viewModel: MovieViewModel,
    onMovieSelect: (Movie) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlay: (Movie, Episode?) -> Unit,
    onBackToHome: () -> Unit,
    onClosePlayer: () -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onBackToDetail: () -> Unit
) {
    Crossfade(targetState = currentScreen) { screen ->
    when (screen) {
        Screen.Home -> {
            val trendingMovies by viewModel.trendingMovies.collectAsState()
            val popularMovies by viewModel.popularMovies.collectAsState()
            val popularTvShows by viewModel.popularTvShows.collectAsState()
            val nowPlayingMovies by viewModel.nowPlayingMovies.collectAsState()
            val topRatedMovies by viewModel.topRatedMovies.collectAsState()
            val upcomingMovies by viewModel.upcomingMovies.collectAsState()
            val onTheAirTv by viewModel.onTheAirTv.collectAsState()
            val topRatedTv by viewModel.topRatedTv.collectAsState()
            val isLoadingHome by viewModel.isLoadingHome.collectAsState()
            HomeScreenContent(
                sections = listOf(
                    "In Tendenza" to trendingMovies,
                    "Film Popolari" to popularMovies,
                    "Al Cinema" to nowPlayingMovies,
                    "I Più Votati" to topRatedMovies,
                    "In Arrivo" to upcomingMovies,
                    "Serie TV più viste" to popularTvShows,
                    "Serie in Onda" to onTheAirTv,
                    "Migliori Serie" to topRatedTv
                ),
                viewModel = viewModel,
                onMovieClick = onMovieSelect,
                isLoading = isLoadingHome
            )
        }
        Screen.Search -> {
            val searchResults by viewModel.searchResults.collectAsState()
            val searchHistory by viewModel.searchHistory.collectAsState()
            SearchScreen(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                results = searchResults,
                onMovieClick = onMovieSelect,
                searchHistory = searchHistory,
                onSearchSubmit = { query ->
                    if (query.isNotBlank()) viewModel.saveSearchQuery(query)
                },
                onRemoveHistoryItem = { viewModel.removeSearchQuery(it) },
                onClearHistory = { viewModel.clearSearchHistory() }
            )
        }
        Screen.TV -> {
            val popularTvShows by viewModel.popularTvShows.collectAsState()
            val onTheAirTv by viewModel.onTheAirTv.collectAsState()
            val topRatedTv by viewModel.topRatedTv.collectAsState()
            val isLoadingHome by viewModel.isLoadingHome.collectAsState()
            HomeScreenContent(
                sections = listOf(
                    "Serie TV più viste" to popularTvShows,
                    "Serie in Onda" to onTheAirTv,
                    "Migliori Serie" to topRatedTv
                ),
                viewModel = viewModel,
                onMovieClick = onMovieSelect,
                isLoading = isLoadingHome
            )
        }
        Screen.Movies -> {
            val nowPlayingMovies by viewModel.nowPlayingMovies.collectAsState()
            val topRatedMovies by viewModel.topRatedMovies.collectAsState()
            val upcomingMovies by viewModel.upcomingMovies.collectAsState()
            val isLoadingHome by viewModel.isLoadingHome.collectAsState()
            HomeScreenContent(
                sections = listOf(
                    "Al Cinema" to nowPlayingMovies,
                    "I Più Votati" to topRatedMovies,
                    "In Arrivo" to upcomingMovies
                ),
                viewModel = viewModel,
                onMovieClick = onMovieSelect,
                isLoading = isLoadingHome
            )
        }
        Screen.Series -> {
            val onTheAirTv by viewModel.onTheAirTv.collectAsState()
            val topRatedTv by viewModel.topRatedTv.collectAsState()
            val popularTvShows by viewModel.popularTvShows.collectAsState()
            val isLoadingHome by viewModel.isLoadingHome.collectAsState()
            HomeScreenContent(
                sections = listOf(
                    "Serie in Onda" to onTheAirTv,
                    "Migliori Serie" to topRatedTv,
                    "Più Popolari" to popularTvShows
                ),
                viewModel = viewModel,
                onMovieClick = onMovieSelect,
                isLoading = isLoadingHome
            )
        }
        Screen.Discover -> {
            DiscoverScreen(
                viewModel = viewModel,
                onMovieClick = onMovieSelect
            )
        }
        Screen.Detail -> {
            val tvDetails by viewModel.tvDetails.collectAsState()
            val favorites by viewModel.favorites.collectAsState()
            val cast by viewModel.cast.collectAsState()
            val episodes by viewModel.episodes.collectAsState()
            val isLoadingStream by viewModel.isLoadingStream.collectAsState()
            val isLoadingCast by viewModel.isLoadingCast.collectAsState()

            selectedMovie?.let { movie ->
                val type = if (movie.title != null) "movie" else "tv"
                val isFav = favorites.any { it.movieId == movie.id && it.type == type }
                DetailScreen(
                    movie = movie,
                    cast = cast,
                    tvDetails = if (movie.title == null) tvDetails else null,
                    episodes = episodes,
                    isFavorite = isFav,
                    onToggleFavorite = { viewModel.toggleFavorite(movie) },
                    isLoadingStream = isLoadingStream,
                    isLoadingCast = isLoadingCast,
                    onSeasonSelected = { seasonNumber ->
                        viewModel.fetchSeasonEpisodes(movie.id, seasonNumber)
                    },
                    onBackClick = onBackToHome,
                    onPlayClick = onPlay
                )
            }
        }
        Screen.PLAYER -> {
            val streamUrl by viewModel.streamUrl.collectAsState()
            streamUrl?.let { url ->
                val resumePos = selectedMovie?.let { m ->
                    val type = if (m.title != null) "movie" else "tv"
                    viewModel.continueWatching.value
                        .firstOrNull { it.movieId == m.id && it.type == type }
                        ?.lastPosition ?: 0L
                } ?: 0L
                key(playSessionKey) {
                    val autoPlayNext by viewModel.autoPlayNextEpisode.collectAsState()
                    PlayerScreen(
                        url = url,
                        onClose = onClosePlayer,
                        movie = selectedMovie,
                        currentEpisode = currentEpisode,
                        episodes = currentEpisodes,
                        isLoadingStream = false,
                        onEpisodeSelected = onEpisodeSelected,
                        onSaveProgress = { m, sn, en, pos, dur ->
                            viewModel.saveWatchProgress(m, sn, en, pos, dur)
                        },
                        resumePosition = resumePos,
                        autoPlayNext = autoPlayNext
                    )
                }
            } ?: run {
                LaunchedEffect(Unit) { onBackToDetail() }
            }
        }
        Screen.Profile -> {
            ProfileScreen(viewModel = viewModel)
        }
    }
    }
}
