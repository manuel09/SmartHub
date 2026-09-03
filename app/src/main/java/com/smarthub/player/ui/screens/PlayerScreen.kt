package com.smarthub.player.ui.screens

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.smarthub.player.data.model.Episode
import com.smarthub.player.data.model.Movie
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

class TrackOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

private fun epLabel(ep: Episode): String = "${ep.seasonNumber}x${"%02d".format(ep.episodeNumber)} \u2022 ${ep.name}"

private data class SeekFeedback(val isForward: Boolean, val x: Float, val y: Float)

private fun Modifier.focusRing(focused: Boolean): Modifier = if (focused)
    this.border(2.dp, com.smarthub.player.ui.theme.VixRed, RoundedCornerShape(8.dp))
else this

private fun moveFocus(cur: Int, direction: Int, episode: Episode?, eps: List<Episode>): Int {
    val hasSeries = episode != null && eps.isNotEmpty()
    val range = if (hasSeries) 8 else 7
    var next = cur
    for (i in 1..range) {
        next = (next + direction + range) % range
        if (next == 0 && !hasSeries) continue
        if (next == 7 && !hasSeries) continue
        return next
    }
    return cur
}

private fun triggerFocusedAction(
    idx: Int, player: ExoPlayer, episode: Episode?, eps: List<Episode>,
    onQuality: () -> Unit, onAudio: () -> Unit, onSubtitles: () -> Unit, onEpisodes: () -> Unit,
    onSkipNext: () -> Unit
) {
    when (idx) {
        0 -> {
            val eIdx = eps.indexOfFirst { it.id == episode?.id }
            if (eps.isNotEmpty() && eIdx >= 0 && eIdx < eps.lastIndex) onSkipNext()
        }
        1 -> player.seekTo(maxOf(0, player.currentPosition - 10_000))
        2 -> if (player.isPlaying) player.pause() else player.play()
        3 -> player.seekTo(minOf(player.duration, player.currentPosition + 10_000))
        4 -> onQuality()
        5 -> onAudio()
        6 -> onSubtitles()
        7 -> if (eps.isNotEmpty()) onEpisodes()
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    url: String,
    onClose: () -> Unit,
    movie: Movie? = null,
    currentEpisode: Episode? = null,
    episodes: List<Episode> = emptyList(),
    isLoadingStream: Boolean = false,
    onEpisodeSelected: (Episode) -> Unit = {},
    onSaveProgress: ((movie: Movie, season: Int, episode: Int, position: Long, duration: Long) -> Unit)? = null,
    resumePosition: Long = 0L,
    autoPlayNext: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val isAltadefinizione = remember(url) { url.contains("h_Cookie=", ignoreCase = true) }
    val playerRef = remember { androidx.compose.runtime.mutableStateOf<ExoPlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var videoTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showEpisodeList by remember { mutableStateOf(false) }
    var hasAutoAdvanced by remember { mutableStateOf(false) }
    var focusIndex by remember { mutableStateOf(-1) }
    var isInPiP by remember { mutableStateOf(false) }

    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700)
            seekFeedback = null
        }
    }

    val onEpisodeSelectedRef by rememberUpdatedState(onEpisodeSelected)
    val episodesRef by rememberUpdatedState(episodes)
    val currentEpisodeRef by rememberUpdatedState(currentEpisode)
    val resumePosRef by rememberUpdatedState(resumePosition)
    val autoPlayRef by rememberUpdatedState(autoPlayNext)
    var hasResumed by remember { mutableStateOf(false) }

    LaunchedEffect(showControls, isPlaying, showAudioDialog, showSubtitleDialog, showQualityDialog, showEpisodeList) {
        if (showControls && isPlaying && errorMessage == null && !showAudioDialog && !showSubtitleDialog && !showQualityDialog && !showEpisodeList && !isInPiP) {
            delay(4000)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.decorView?.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        val pipListener = { pip: Boolean ->
            isInPiP = pip
            if (pip) {
                showControls = false
                showAudioDialog = false
                showSubtitleDialog = false
                showQualityDialog = false
                showEpisodeList = false
            }
        }
        com.smarthub.player.MainActivity.onPiPModeChangedListener = pipListener

        val handler: (android.view.KeyEvent) -> Boolean = handler@{ ke ->
            val player = playerRef.value ?: return@handler false
            if (ke.action != android.view.KeyEvent.ACTION_DOWN) return@handler false
            showControls = true
            when (ke.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER -> {
                    if (focusIndex >= 0) {
                        triggerFocusedAction(focusIndex, player, currentEpisode, episodes,
                            { showQualityDialog = true }, { showAudioDialog = true },
                            { showSubtitleDialog = true }, { showEpisodeList = true }, {
                                val eps = episodesRef; val cur = currentEpisodeRef
                                if (!hasAutoAdvanced && eps.isNotEmpty() && cur != null) {
                                    val idx = eps.indexOfFirst { it.id == cur.id }
                                    if (idx >= 0 && idx < eps.lastIndex) {
                                        hasAutoAdvanced = true
                                        onEpisodeSelectedRef(eps[idx + 1])
                                    }
                                }
                            })
                        true
                    } else {
                        if (isPlaying) player.pause() else player.play()
                        true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    focusIndex = -1
                    if (isPlaying) player.pause() else player.play()
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    focusIndex = -1
                    player.play()
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    focusIndex = -1
                    player.pause()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focusIndex >= 0) {
                        focusIndex = moveFocus(focusIndex, -1, currentEpisode, episodes)
                        true
                    } else {
                        player.seekTo(maxOf(0, player.currentPosition - 10_000))
                        true
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focusIndex >= 0) {
                        focusIndex = moveFocus(focusIndex, 1, currentEpisode, episodes)
                        true
                    } else {
                        player.seekTo(minOf(player.duration, player.currentPosition + 10_000))
                        true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    focusIndex = -1
                    player.seekTo(maxOf(0, player.currentPosition - 10_000))
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    focusIndex = -1
                    player.seekTo(minOf(player.duration, player.currentPosition + 10_000))
                    true
                }
                android.view.KeyEvent.KEYCODE_BACK -> {
                    onClose()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (focusIndex < 0) {
                        focusIndex = 2
                    } else {
                        focusIndex = -1
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    focusIndex = -1
                    true
                }
                else -> false
            }
        }
        com.smarthub.player.MainActivity.onKeyEventListener = handler

        onDispose {
            movie?.let { m ->
                val p = playerRef.value
                if (p != null) {
                    val pos = p.currentPosition
                    val dur = p.duration
                    if (pos > 2000) {
                        onSaveProgress?.invoke(m,
                            currentEpisode?.seasonNumber ?: 0,
                            currentEpisode?.episodeNumber ?: 0, pos, dur)
                    }
                }
            }
            playerRef.value?.release()
            com.smarthub.player.MainActivity.onKeyEventListener = null
            com.smarthub.player.MainActivity.onPiPModeChangedListener = null
            playerRef.value = null
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun refreshTracks(player: ExoPlayer) {
        val tracks = player.currentTracks
        val audio = mutableListOf<TrackOption>()
        val subtitles = mutableListOf<TrackOption>()
        val video = mutableListOf<TrackOption>()
        for (group in tracks.groups) {
            if (!group.isSupported) continue
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val lang = fmt.language ?: ""
                        val label = fmt.label ?: ""
                        val display = when {
                            label.isNotBlank() -> label
                            lang.isNotBlank() -> lang.uppercase()
                            else -> "Audio ${i + 1}"
                        }
                        audio.add(TrackOption(group, i, display, group.isTrackSelected(i)))
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val lang = fmt.language ?: ""
                        val label = fmt.label ?: ""
                        val display = when {
                            label.isNotBlank() -> label
                            lang.isNotBlank() -> lang.uppercase()
                            else -> "Sottotitoli ${i + 1}"
                        }
                        subtitles.add(TrackOption(group, i, display, group.isTrackSelected(i)))
                    }
                }
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val height = fmt.height
                        val bitrate = fmt.bitrate
                        val label = fmt.label ?: ""
                        val display = when {
                            isAltadefinizione -> "720p"
                            label.isNotBlank() -> label
                            height != Format.NO_VALUE -> "${height}p"
                            bitrate != Format.NO_VALUE -> {
                                val mbps = bitrate / 1_000_000
                                if (mbps > 0) "~${mbps} Mbps" else "Auto"
                            }
                            else -> "Qualit\u00E0 ${i + 1}"
                        }
                        video.add(TrackOption(group, i, display, group.isTrackSelected(i)))
                    }
                }
            }
        }
        audioTracks = audio
        subtitleTracks = subtitles
        videoTracks = video
    }

    fun selectTrack(player: ExoPlayer, option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
            .setTrackTypeDisabled(option.group.type, false)
            .build()
    }

    fun disableSubtitles(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun autoAdvanceToNext() {
        if (hasAutoAdvanced || !autoPlayRef) return
        val eps = episodesRef
        val cur = currentEpisodeRef
        if (eps.isEmpty() || cur == null) return
        val idx = eps.indexOfFirst { it.id == cur.id }
        if (idx >= 0 && idx < eps.lastIndex) {
            hasAutoAdvanced = true
            onEpisodeSelectedRef(eps[idx + 1])
        }
    }

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            playerRef.value = this
            if (isAltadefinizione) {
                setTrackSelectionParameters(
                    trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(1280, 720)
                        .build()
                )
            }
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isLoading = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        isLoading = false
                        if (resumePosRef > 0 && !hasResumed) {
                            hasResumed = true
                            seekTo(resumePosRef)
                        }
                    }
                    if (state == Player.STATE_ENDED) autoAdvanceToNext()
                }
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val codeName = androidx.media3.common.PlaybackException
                        .getErrorCodeName(error.errorCode)
                        .removePrefix("ERROR_CODE_")
                    Log.e("PlayerScreen", "Player error $codeName (${error.errorCode}): ${error.message}", error)
                    errorMessage = when (error.errorCode) {
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Stream non disponibile o scaduto"
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Errore di rete: controlla la connessione"
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT -> "Timeout: il server non risponde"
                        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "Contenuto non valido (il link non è un video)"
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> "Impossibile decodificare il video"
                        else -> "Errore di riproduzione"
                    } + " [$codeName]"
                    isLoading = false
                }
                override fun onTracksChanged(tracks: Tracks) {
                    refreshTracks(this@apply)
                }
            })

            val okHttpClient = OkHttpClient.Builder()
                .proxy(java.net.Proxy.NO_PROXY)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
                setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }
            val mediaItem = MediaItem.fromUri(url)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(Unit) {
        var lastSaved = 0L
        while (true) {
            delay(16)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            currentPosition = pos
            duration = dur
            if (exoPlayer.isPlaying && pos > 5000 && (pos - lastSaved) >= 30000) {
                lastSaved = pos
                movie?.let { m ->
                    onSaveProgress?.invoke(m, currentEpisode?.seasonNumber ?: 0, currentEpisode?.episodeNumber ?: 0, pos, dur)
                }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onKeyEvent { ke ->
            val nke = ke.nativeKeyEvent ?: return@onKeyEvent false
            if (nke.action == android.view.KeyEvent.ACTION_DOWN) {
                showControls = true
                when (nke.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        exoPlayer.play()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        exoPlayer.pause()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000))
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10_000))
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10_000))
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000))
                        true
                    }
                    else -> false
                }
            } else false
        }
    ) {
        AndroidView(
            factory = { ctx ->
                val playerView = PlayerView(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = false
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                playerView
            },
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> {
                                exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10_000))
                                seekFeedback = SeekFeedback(isForward = false, x = offset.x, y = offset.y)
                            }
                            offset.x > third * 2 -> {
                                exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000))
                                seekFeedback = SeekFeedback(isForward = true, x = offset.x, y = offset.y)
                            }
                            else -> {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    }
                )
            }
        )

        seekFeedback?.let { fb ->
            val icon = if (fb.isForward) Icons.Default.Forward10 else Icons.Default.Replay10
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("10 s", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = com.smarthub.player.ui.theme.VixRed, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Caricamento...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }

        AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
            errorMessage?.let { msg ->
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(msg, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = com.smarthub.player.ui.theme.VixRed), shape = RoundedCornerShape(12.dp)) {
                            Text("Torna Indietro", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showControls && !isInPiP, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = movie?.title ?: movie?.name ?: "",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = {
                                try {
                                    activity?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
                                } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Default.PictureInPicture, "Picture-in-Picture", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(24.dp))
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showControls && !isInPiP, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (currentEpisode != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                epLabel(currentEpisode),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val idx = episodes.indexOfFirst { it.id == currentEpisode.id }
                            val hasNext = episodes.isNotEmpty() && idx >= 0 && idx < episodes.lastIndex
                            IconButton(
                                onClick = { if (hasNext) autoAdvanceToNext() },
                                modifier = Modifier.size(36.dp).focusRing(focusIndex == 0)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    "Prossimo episodio",
                                    tint = if (hasNext) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else if (movie != null) {
                        Text(
                            movie.title ?: movie.name ?: "",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    val progress by remember {
                        derivedStateOf { if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f }
                    }
                    val posFormatted by remember {
                        derivedStateOf {
                            val pm = (currentPosition / 60000).toInt()
                            val ps = ((currentPosition % 60000) / 1000).toInt()
                            "%d:%02d".format(pm, ps)
                        }
                    }
                    val durFormatted by remember {
                        derivedStateOf {
                            val dm = (duration / 60000).toInt()
                            val ds = ((duration % 60000) / 1000).toInt()
                            "%d:%02d".format(dm, ds)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(posFormatted, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text(durFormatted, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    Slider(
                        value = progress,
                        onValueChange = { exoPlayer.seekTo((it * duration).roundToLong()) },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = com.smarthub.player.ui.theme.VixRed, inactiveTrackColor = Color.White.copy(alpha = 0.25f))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10_000)) },
                            modifier = Modifier.size(32.dp).focusRing(focusIndex == 1)
                        ) {
                            Icon(Icons.Default.Replay10, "Indietro 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoading) {
                                CircularProgressIndicator(color = com.smarthub.player.ui.theme.VixRed, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(
                                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier.size(52.dp).focusRing(focusIndex == 2)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { exoPlayer.seekTo(minOf(duration, exoPlayer.currentPosition + 10_000)) },
                            modifier = Modifier.size(32.dp).focusRing(focusIndex == 3)
                        ) {
                            Icon(Icons.Default.Forward10, "Avanti 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showAudioDialog = true },
                            enabled = audioTracks.isNotEmpty(),
                            modifier = Modifier.focusRing(focusIndex == 5)
                        ) {
                            Icon(Icons.Default.Language, null,
                                tint = if (audioTracks.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (audioTracks.isEmpty()) "\u2014" else audioTracks.firstOrNull { it.isSelected }?.label ?: "Audio",
                                color = if (audioTracks.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        TextButton(
                            onClick = { showSubtitleDialog = true },
                            enabled = subtitleTracks.isNotEmpty(),
                            modifier = Modifier.focusRing(focusIndex == 6)
                        ) {
                            Icon(Icons.Default.ClosedCaption, null,
                                tint = if (subtitleTracks.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (subtitleTracks.isEmpty()) "\u2014" else subtitleTracks.firstOrNull { it.isSelected }?.label ?: "Nessuno",
                                color = if (subtitleTracks.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        TextButton(
                            onClick = { showQualityDialog = true },
                            enabled = videoTracks.isNotEmpty(),
                            modifier = Modifier.focusRing(focusIndex == 4)
                        ) {
                            Icon(Icons.Default.HighQuality, null,
                                tint = if (videoTracks.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (videoTracks.isEmpty()) "Auto" else videoTracks.firstOrNull { it.isSelected }?.label ?: "Auto",
                                color = if (videoTracks.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        TextButton(
                            onClick = { showEpisodeList = true },
                            enabled = episodes.isNotEmpty(),
                            modifier = Modifier.focusRing(focusIndex == 7)
                        ) {
                            Icon(Icons.Default.List, null,
                                tint = if (episodes.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (currentEpisode != null) "${currentEpisode.seasonNumber}x${"%02d".format(currentEpisode.episodeNumber)}" else if (episodes.isNotEmpty()) "Episodi" else "\u2014",
                                color = if (episodes.isNotEmpty()) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (!showControls && !isLoading && errorMessage == null && !isPlaying && !isInPiP) {
            Icon(Icons.Default.Pause, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.align(Alignment.Center).size(72.dp))
        }
    }

    if (showAudioDialog && audioTracks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Audio", color = Color.White) },
            text = {
                Column {
                    audioTracks.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option.isSelected, onClick = { selectTrack(exoPlayer, option); showAudioDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = com.smarthub.player.ui.theme.VixRed))
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("Sottotitoli", color = Color.White) },
            text = {
                Column {
                    val noneSelected = subtitleTracks.none { it.isSelected }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = noneSelected, onClick = { disableSubtitles(exoPlayer); showSubtitleDialog = false },
                            colors = RadioButtonDefaults.colors(selectedColor = com.smarthub.player.ui.theme.VixRed))
                        Spacer(Modifier.width(8.dp))
                        Text("Nessuno", color = Color.White, fontSize = 14.sp)
                    }
                    subtitleTracks.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option.isSelected, onClick = { selectTrack(exoPlayer, option); showSubtitleDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = com.smarthub.player.ui.theme.VixRed))
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                    if (subtitleTracks.isEmpty()) {
                        Text("Nessun sottotitolo disponibile", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showQualityDialog && videoTracks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Qualit\u00E0 video", color = Color.White) },
            text = {
                Column {
                    videoTracks.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = option.isSelected, onClick = { selectTrack(exoPlayer, option); showQualityDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = com.smarthub.player.ui.theme.VixRed))
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showEpisodeList && episodes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showEpisodeList = false },
            title = { Text(movie?.title ?: movie?.name ?: "Episodi", color = Color.White, fontSize = 16.sp) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(episodes, key = { _, ep -> ep.id }) { _, ep ->
                        val isCurrent = currentEpisode?.id == ep.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .background(
                                    if (isCurrent) com.smarthub.player.ui.theme.VixRed.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                epLabel(ep),
                                color = if (isCurrent) com.smarthub.player.ui.theme.VixRed else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Text("IN ONDA", color = com.smarthub.player.ui.theme.VixRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        onEpisodeSelected(ep)
                                        showEpisodeList = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, "Riproduci", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
