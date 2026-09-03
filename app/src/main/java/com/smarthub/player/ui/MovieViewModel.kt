package com.smarthub.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smarthub.player.BuildConfig
import com.smarthub.player.data.api.RetrofitClient
import com.smarthub.player.data.model.Cast
import com.smarthub.player.data.model.Movie
import com.smarthub.player.data.model.TvShowDetails
import com.smarthub.player.data.model.Episode
import com.smarthub.player.data.api.ProxyConfig
import com.smarthub.player.data.model.StreamItem
import com.smarthub.player.data.local.ContinueWatchingItem
import com.smarthub.player.data.local.FavoriteItem
import com.smarthub.player.data.local.LocalStorage
import com.smarthub.player.data.local.SearchHistoryItem
import com.smarthub.player.data.local.AppSettings
import com.smarthub.player.data.local.ProfilesManager
import com.smarthub.player.data.local.UserProfile
import com.smarthub.player.data.model.Genre
import com.smarthub.player.data.update.UpdateChecker
import com.smarthub.player.data.update.UpdateInfo
import com.smarthub.player.data.update.UpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import android.util.Log
import okhttp3.Request
import okhttp3.OkHttpClient

class MovieViewModel(application: Application) : AndroidViewModel(application) {
    private val _trendingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val trendingMovies: StateFlow<List<Movie>> = _trendingMovies

    private val _popularMovies = MutableStateFlow<List<Movie>>(emptyList())
    val popularMovies: StateFlow<List<Movie>> = _popularMovies

    private val _nowPlayingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val nowPlayingMovies: StateFlow<List<Movie>> = _nowPlayingMovies

    private val _topRatedMovies = MutableStateFlow<List<Movie>>(emptyList())
    val topRatedMovies: StateFlow<List<Movie>> = _topRatedMovies

    private val _upcomingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val upcomingMovies: StateFlow<List<Movie>> = _upcomingMovies

    private val _popularTvShows = MutableStateFlow<List<Movie>>(emptyList())
    val popularTvShows: StateFlow<List<Movie>> = _popularTvShows

    private val _onTheAirTv = MutableStateFlow<List<Movie>>(emptyList())
    val onTheAirTv: StateFlow<List<Movie>> = _onTheAirTv

    private val _topRatedTv = MutableStateFlow<List<Movie>>(emptyList())
    val topRatedTv: StateFlow<List<Movie>> = _topRatedTv

    private val _cast = MutableStateFlow<List<Cast>>(emptyList())
    val cast: StateFlow<List<Cast>> = _cast

    private val _searchResults = MutableStateFlow<List<Movie>>(emptyList())
    val searchResults: StateFlow<List<Movie>> = _searchResults

    private val _tvDetails = MutableStateFlow<TvShowDetails?>(null)
    val tvDetails: StateFlow<TvShowDetails?> = _tvDetails

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl

    private val _availableStreams = MutableStateFlow<List<StreamItem>>(emptyList())
    val availableStreams: StateFlow<List<StreamItem>> = _availableStreams

    private val _isLoadingStream = MutableStateFlow(false)
    val isLoadingStream: StateFlow<Boolean> = _isLoadingStream

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError

    private val _altadefinizioneExpiredError = MutableStateFlow(false)
    val altadefinizioneExpiredError: StateFlow<Boolean> = _altadefinizioneExpiredError

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError

    private val _proxyActive = MutableStateFlow(false)
    val proxyActive: StateFlow<Boolean> = _proxyActive

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching

    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres

    private val _genreMovies = MutableStateFlow<List<Movie>>(emptyList())
    val genreMovies: StateFlow<List<Movie>> = _genreMovies

    private val _genreTvShows = MutableStateFlow<List<Movie>>(emptyList())
    val genreTvShows: StateFlow<List<Movie>> = _genreTvShows

    private val _selectedGenreId = MutableStateFlow<Int?>(null)
    val selectedGenreId: StateFlow<Int?> = _selectedGenreId

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryItem>> = _searchHistory

    private val _isLoadingGenre = MutableStateFlow(false)
    val isLoadingGenre: StateFlow<Boolean> = _isLoadingGenre

    private val _autoPlayNextEpisode = MutableStateFlow(true)
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode

    private val _isLoadingHome = MutableStateFlow(true)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome

    private val _isLoadingCast = MutableStateFlow(false)
    val isLoadingCast: StateFlow<Boolean> = _isLoadingCast

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles

    private var resumePositionMillis = 0L

    fun setResumePosition(pos: Long) {
        resumePositionMillis = pos
    }

    fun consumeResumePosition(): Long {
        val pos = resumePositionMillis
        resumePositionMillis = 0L
        return pos
    }

    init {
        val app = getApplication<Application>()
        _profiles.value = ProfilesManager.getAllProfiles(app)
        val activeId = ProfilesManager.getActiveProfileId(app)
        if (activeId == null && _profiles.value.isNotEmpty()) {
            ProfilesManager.setActiveProfile(app, _profiles.value.first().id)
        }
        _activeProfileId.value = ProfilesManager.getActiveProfileId(app)
        fetchHomeData()
        fetchGenres()
        checkProxyStatus()
        loadLocalData()
    }

    fun setActiveProfile(profileId: String) {
        ProfilesManager.setActiveProfile(getApplication(), profileId)
        _activeProfileId.value = profileId
        loadLocalData()
    }

    fun refreshProfiles() {
        _profiles.value = ProfilesManager.getAllProfiles(getApplication())
    }

    fun createProfile(name: String, email: String): UserProfile {
        val profile = UserProfile(name = name, email = email)
        ProfilesManager.saveProfile(getApplication(), profile)
        _profiles.value = ProfilesManager.getAllProfiles(getApplication())
        return profile
    }

    fun updateProfile(profile: UserProfile) {
        ProfilesManager.saveProfile(getApplication(), profile)
        _profiles.value = ProfilesManager.getAllProfiles(getApplication())
    }

    fun deleteProfile(profileId: String) {
        ProfilesManager.deleteProfile(getApplication(), profileId)
        _profiles.value = ProfilesManager.getAllProfiles(getApplication())
        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = ProfilesManager.getActiveProfileId(getApplication())
            loadLocalData()
        }
    }

    private fun loadLocalData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _continueWatching.value = LocalStorage.getContinueWatching(getApplication())
            _favorites.value = LocalStorage.getFavorites(getApplication())
            _searchHistory.value = LocalStorage.getSearchHistory(getApplication())
            _autoPlayNextEpisode.value = AppSettings.getAutoPlayNextEpisode(getApplication())
        }
    }

    fun reloadLocalData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _continueWatching.value = LocalStorage.getContinueWatching(getApplication())
            _favorites.value = LocalStorage.getFavorites(getApplication())
            _searchHistory.value = LocalStorage.getSearchHistory(getApplication())
            _autoPlayNextEpisode.value = AppSettings.getAutoPlayNextEpisode(getApplication())
            _profiles.value = ProfilesManager.getAllProfiles(getApplication())
        }
    }

    // ─── Continue Watching ────────────────────────────────────────

    fun saveWatchProgress(movie: Movie, seasonNumber: Int, episodeNumber: Int, position: Long, duration: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val type = if (movie.title != null) "movie" else "tv"
            val item = ContinueWatchingItem(
                movieId = movie.id,
                type = type,
                title = movie.title ?: movie.name ?: "",
                posterPath = movie.posterPath,
                backdropPath = movie.backdropPath,
                voteAverage = movie.voteAverage,
                releaseDate = movie.releaseDate,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                lastPosition = position,
                duration = duration
            )
            _continueWatching.value = LocalStorage.saveContinueWatching(getApplication(), item)
        }
    }

    fun removeContinueWatching(movieId: Int, type: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _continueWatching.value = LocalStorage.removeContinueWatching(getApplication(), movieId, type)
        }
    }

    // ─── Favorites ─────────────────────────────────────────────────

    fun toggleFavorite(movie: Movie): Boolean {
        val type = if (movie.title != null) "movie" else "tv"
        val item = FavoriteItem(
            movieId = movie.id,
            type = type,
            title = movie.title ?: movie.name ?: "",
            posterPath = movie.posterPath,
            backdropPath = movie.backdropPath,
            voteAverage = movie.voteAverage,
            releaseDate = movie.releaseDate,
            overview = movie.overview
        )
        var added = false
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = LocalStorage.toggleFavorite(getApplication(), item)
            _favorites.value = result.favorites
            added = result.added
        }
        return added
    }

    fun clearAllContinueWatching() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            LocalStorage.clearAllContinueWatching(getApplication())
            _continueWatching.value = emptyList()
        }
    }

    fun clearAllFavorites() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            LocalStorage.clearAllFavorites(getApplication())
            _favorites.value = emptyList()
        }
    }

    fun isFavorite(movieId: Int, type: String): Boolean {
        return _favorites.value.any { it.movieId == movieId && it.type == type }
    }

    private fun checkProxyStatus() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val maxRetries = 5
            val initialDelayMillis = 1000L // 1 secondo
            var currentDelay = initialDelayMillis

            for (i in 0 until maxRetries) {
                try {
                    val client = OkHttpClient.Builder()
                        .proxy(java.net.Proxy.NO_PROXY)
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(ProxyConfig.EASY_PROXY_URL).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            _proxyActive.value = true
                            return@launch // Esci se il proxy è attivo
                        }
                    }
                } catch (e: Exception) {
                    // Logga l'eccezione se necessario, ma non la mostrare all'utente qui
                }
                _proxyActive.value = false
                delay(currentDelay) // Attendi prima del prossimo tentativo
                currentDelay *= 2 // Aumenta il ritardo (backoff esponenziale)
            }
        }
    }

    fun fetchStreamUrl(movieId: Int, title: String, episode: Episode?) {
        val proxyUrl = ProxyConfig.STREAMVIX_URL
        if (proxyUrl.contains("-1")) {
            _streamError.value = "Servizio proxy non ancora avviato. Riprova."
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingStream.value = true
            _streamError.value = null
            _altadefinizioneExpiredError.value = false
            _streamUrl.value = null
            _availableStreams.value = emptyList()
            try {
                val type = if (episode != null) "series" else "movie"
                val addonId = if (episode != null) {
                    "tmdb:$movieId:${episode.seasonNumber}:${episode.episodeNumber}"
                } else {
                    "tmdb:$movieId"
                }

                Log.d("MovieViewModel", "Fetching streams: type=$type id=$addonId proxyUrl=$proxyUrl")

                val response = RetrofitClient.streamViXApi.getStreams(type, addonId, title)
                val validStreams = response.streams.filter { it.url != null && it.url.isNotBlank() }
                _availableStreams.value = validStreams

                if (validStreams.isEmpty()) {
                    if (response.altadefinizioneExpired) {
                        _altadefinizioneExpiredError.value = true
                        _streamError.value = "La sessione Altadefinizione è scaduta. Accedi di nuovo con Telegram per continuare a guardare."
                    } else {
                        _streamError.value = "Nessuno stream disponibile per questo contenuto"
                    }
                }
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Stream fetch error: ${e.message}", e)
                _streamError.value = "Errore di connessione: ${e.message?.take(80)}"
            } finally {
                _isLoadingStream.value = false
            }
        }
    }

    fun selectStream(stream: StreamItem) {
        val streamUrl = stream.url
        if (streamUrl != null) {
            _streamUrl.value = if (streamUrl.startsWith("http")) streamUrl
                              else ProxyConfig.getProxiedUrl(streamUrl)
        }
    }

    fun clearStream() {
        _streamUrl.value = null
        _streamError.value = null
        _altadefinizioneExpiredError.value = false
        _isLoadingStream.value = false
        _availableStreams.value = emptyList()
    }

    fun clearStreamError() {
        _streamError.value = null
        _altadefinizioneExpiredError.value = false
    }

    fun checkForUpdate() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateError.value = null
            try {
                val current = AppSettings.getAppVersionCode(getApplication())
                val info = UpdateChecker.check(current)
                _updateInfo.value = info
            } catch (e: Exception) {
                _updateError.value = "Controllo aggiornamenti non riuscito"
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun downloadUpdate() {
        val info = _updateInfo.value ?: return
        if (_isDownloadingUpdate.value) return
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _updateError.value = null
            val result = UpdateInstaller.downloadAndInstall(getApplication(), info)
            if (result.isFailure) {
                _updateError.value = "Download aggiornamento non riuscito"
            }
            _isDownloadingUpdate.value = false
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun search(query: String) {
        val apiKey = BuildConfig.TMDB_API_KEY
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(300)
                val response = RetrofitClient.tmdbApi.searchMulti(query, apiKey)
                _searchResults.value = response.results
                if (query.isNotBlank() && response.results.isNotEmpty()) {
                    _searchHistory.value = LocalStorage.saveSearchQuery(getApplication(), query)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ignored
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchTvDetails(tvId: Int) {
        val apiKey = BuildConfig.TMDB_API_KEY
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.tmdbApi.getTvShowDetails(tvId, apiKey)
                _tvDetails.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchSeasonEpisodes(tvId: Int, seasonNumber: Int) {
        val apiKey = BuildConfig.TMDB_API_KEY
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.tmdbApi.getSeasonDetails(tvId, seasonNumber, apiKey)
                _episodes.value = response.episodes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchHomeData() {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val trendingD = async { RetrofitClient.tmdbApi.getTrending(apiKey) }
                    val popularD = async { RetrofitClient.tmdbApi.getPopularMovies(apiKey) }
                    val tvShowsD = async { RetrofitClient.tmdbApi.getPopularTvShows(apiKey) }
                    val nowPlayingD = async { RetrofitClient.tmdbApi.getNowPlayingMovies(apiKey) }
                    val topRatedD = async { RetrofitClient.tmdbApi.getTopRatedMovies(apiKey) }
                    val upcomingD = async { RetrofitClient.tmdbApi.getUpcomingMovies(apiKey) }
                    val onAirD = async { RetrofitClient.tmdbApi.getOnTheAirTv(apiKey) }
                    val topRatedTvD = async { RetrofitClient.tmdbApi.getTopRatedTv(apiKey) }

                    val trending = trendingD.await()
                    val popular = popularD.await()
                    val tvShows = tvShowsD.await()
                    val nowPlaying = nowPlayingD.await()
                    val topRated = topRatedD.await()
                    val upcoming = upcomingD.await()
                    val onAir = onAirD.await()
                    val topRatedTv = topRatedTvD.await()

                    _trendingMovies.value = trending.results
                    _popularMovies.value = popular.results
                    _popularTvShows.value = tvShows.results
                    _nowPlayingMovies.value = nowPlaying.results
                    _topRatedMovies.value = topRated.results
                    _upcomingMovies.value = upcoming.results
                    _onTheAirTv.value = onAir.results
                    _topRatedTv.value = topRatedTv.results
                }
                _isLoadingHome.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoadingHome.value = false
            }
        }
    }

    fun fetchCast(movieId: Int) {
        val apiKey = BuildConfig.TMDB_API_KEY
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingCast.value = true
            try {
                val response = RetrofitClient.tmdbApi.getMovieCredits(movieId, apiKey)
                _cast.value = response.cast
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingCast.value = false
            }
        }
    }

    // ─── Genres ──────────────────────────────────────────────────

    fun fetchGenres() {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val movieGenres = RetrofitClient.tmdbApi.getMovieGenres(apiKey)
                _genres.value = movieGenres.genres
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectGenre(genreId: Int?) {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") return
        _selectedGenreId.value = genreId
        if (genreId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoadingGenre.value = true
                try {
                    coroutineScope {
                        val moviesD = async { RetrofitClient.tmdbApi.discoverMovies(apiKey, genreId) }
                        val tvD = async { RetrofitClient.tmdbApi.discoverTv(apiKey, genreId) }
                        _genreMovies.value = moviesD.await().results
                        _genreTvShows.value = tvD.await().results
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isLoadingGenre.value = false
                }
            }
        } else {
            _genreMovies.value = emptyList()
            _genreTvShows.value = emptyList()
        }
    }

    // ─── Search History ───────────────────────────────────────────

    fun saveSearchQuery(query: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _searchHistory.value = LocalStorage.saveSearchQuery(getApplication(), query)
        }
    }

    fun removeSearchQuery(query: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _searchHistory.value = LocalStorage.removeSearchQuery(getApplication(), query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            LocalStorage.clearSearchHistory(getApplication())
            _searchHistory.value = emptyList()
        }
    }

    fun toggleAutoPlayNextEpisode() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newValue = !_autoPlayNextEpisode.value
            AppSettings.setAutoPlayNextEpisode(getApplication(), newValue)
            _autoPlayNextEpisode.value = newValue
        }
    }
}
