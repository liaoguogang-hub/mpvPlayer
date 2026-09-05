package live.mehiz.mpvkt.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.MpvKtDatabase
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.ui.custombuttons.CustomButtonsUiState
import live.mehiz.mpvkt.ui.custombuttons.getButtons
import org.koin.java.KoinJavaComponent.inject
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PlayerViewModelProviderFactory(
  private val activity: PlayerActivity,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
    return PlayerViewModel(activity) as T
  }
}

@Suppress("TooManyFunctions")
class PlayerViewModel(
  private val activity: PlayerActivity,
) : ViewModel() {
  private val playerPreferences: PlayerPreferences by inject(PlayerPreferences::class.java)
  private val gesturePreferences: GesturePreferences by inject(GesturePreferences::class.java)
  private val audioPreferences: AudioPreferences by inject(AudioPreferences::class.java)
  private val mpvKtDatabase: MpvKtDatabase by inject(MpvKtDatabase::class.java)
  private val json: Json by inject(Json::class.java)

  init {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val buttons = mpvKtDatabase.customButtonDao().getCustomButtons().first()
        buttons.firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }?.let {
          _primaryButton.update { _ -> it }
          // If the button text is not empty, it has been set buy a lua script in which
          // case we don't want to override it
          if (_primaryButtonTitle.value.isEmpty()) setPrimaryCustomButtonTitle(it)
        }
        activity.setupCustomButtons(buttons)
        _customButtons.update { _ -> CustomButtonsUiState.Success(buttons) }
      } catch (e: Exception) {
        Log.e(TAG, e.message ?: "Unable to fetch buttons")
        _customButtons.update { _ -> CustomButtonsUiState.Error(e.message ?: "Unable to fetch buttons") }
      }
    }
  }

  private val _customButtons = MutableStateFlow<CustomButtonsUiState>(CustomButtonsUiState.Loading)
  val customButtons = _customButtons.asStateFlow()

  private val _primaryButton = MutableStateFlow<CustomButtonEntity?>(null)
  val primaryButton = _primaryButton.asStateFlow()

  private val _primaryButtonTitle = MutableStateFlow("")
  val primaryButtonTitle = _primaryButtonTitle.asStateFlow()

  val paused by MPVLib.propBoolean["pause"].collectAsState(viewModelScope)
  val pos by MPVLib.propInt["time-pos"].collectAsState(viewModelScope)
  val duration by MPVLib.propInt["duration"].collectAsState(viewModelScope)
  private val currentMPVVolume by MPVLib.propInt["volume"].collectAsState(viewModelScope)

  val currentVolume = MutableStateFlow(activity.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
  private val volumeBoostCap by MPVLib.propInt["volume-max"].collectAsState(viewModelScope)

  val subtitleTracks = MPVLib.propNode["track-list"]
    .map { (it?.toObject<List<TrackNode>>(json)?.filter { it.isSubtitle } ?: persistentListOf()).toImmutableList() }

  val audioTracks = MPVLib.propNode["track-list"]
    .map { (it?.toObject<List<TrackNode>>(json)?.filter { it.isAudio } ?: persistentListOf()).toImmutableList() }

  val chapters = MPVLib.propNode["chapter-list"]
    .map { (it?.toObject<List<ChapterNode>>(json) ?: persistentListOf()).map { it.toSegment() }.toImmutableList() }

  // ===== Audio / video mode integration =====================================
  //
  // Whether the loaded file has any video track. Used together with [PlayerPreferences.audioMode]
  // and the per-session [_audioOnlyOverride] to drive the [AudioPlayerOverlay].
  private val hasVideoTrack: Flow<Boolean> = MPVLib.propNode["track-list"]
    .map { (it?.toObject<List<TrackNode>>(json)?.any { track -> track.isVideo && track.albumArt != true } ?: false) }

  /**
   * Per-session override that wins over [PlayerPreferences.audioMode]. `null` follows the
   * preference, `true` forces the audio UI for the current playback, `false` forces the video UI.
   *
   * The "Switch to audio/video" button in the player toggles this.
   */
  private val _audioOnlyOverride = MutableStateFlow<Boolean?>(null)
  val audioOnlyOverride = _audioOnlyOverride.asStateFlow()

  val isAudioOnly: StateFlow<Boolean> = combine(hasVideoTrack, _audioOnlyOverride) { hasVideo, override ->
    when (override) {
      null -> when (playerPreferences.audioMode.get()) {
        AudioMode.Auto -> !hasVideo
        AudioMode.AudioOnly -> true
        AudioMode.VideoUiOnly -> false
      }
      else -> override
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

  /** Snapshot of audio-only playback metadata for the AudioPlayerOverlay. */
  data class AudioTrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val codec: String,
  )

  private val mediaTitleFlow = MPVLib.propString["media-title"]
  private val mediaArtistFlow = MPVLib.propString["metadata/artist"]
  private val mediaAlbumFlow = MPVLib.propString["metadata/album"]
  val audioMetadata: StateFlow<AudioTrackInfo> = combine(
    mediaTitleFlow,
    mediaArtistFlow,
    mediaAlbumFlow,
  ) { title: String?, artist: String?, album: String? ->
    AudioTrackInfo(
      title = title.orEmpty(),
      artist = artist.orEmpty(),
      album = album.orEmpty(),
      codec = "",
    )
  }.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    initialValue = AudioTrackInfo("", "", "", ""),
  )

  private val _albumArt = MutableStateFlow<Bitmap?>(null)
  val albumArt = _albumArt.asStateFlow()
  private val _albumPalette = MutableStateFlow(AlbumPalette.Default)
  val albumPalette = _albumPalette.asStateFlow()
  val lyricsTextScale: StateFlow<Float> = playerPreferences.lyricsTextScale.changes()
    .stateIn(viewModelScope, SharingStarted.Eagerly, playerPreferences.lyricsTextScale.get())
  fun setLyricsTextScale(value: Float) {
    val clamped = value.coerceIn(0.85f, 1.4f)
    playerPreferences.lyricsTextScale.set(clamped)
  }

  // Album-art is pushed from PlayerActivity via setAlbumArt(), which extracts the
  // embedded picture with MediaMetadataRetriever (an Android API independent of mpv's
  // video pipeline). We deliberately DON'T poll MPVLib.grabThumbnail(): forcing mpv to
  // render audio cover art requires --audio-display=attachment which, once the surface
  // is hidden, strands mpv's video output and kills audio on some devices.

  fun setAlbumArt(bitmap: Bitmap?) {
    _albumArt.value = bitmap
    viewModelScope.launch {
      _albumPalette.value = AlbumPaletteExtractor.fromBitmap(bitmap)
    }
  }

  // Kept as no-op stubs so PlayerActivity's mode observer keeps compiling; cover-art
  // capture is now event-driven (setAlbumArt) instead of a periodic poll.
  fun startAlbumArtCapture() = Unit
  fun stopAlbumArtCapture() = Unit

  /** Flip the per-session override. Returns the new effective value for UI feedback. */
  fun toggleAudioOnlyMode(): Boolean {
    val current = isAudioOnly.value
    val newValue = !current
    _audioOnlyOverride.value = newValue
    return newValue
  }

  fun resetAudioOnlyOverride() {
    _audioOnlyOverride.value = null
  }

  // ===== Audio page extras: source identity, lyrics, pitch, sleep timer =====
  private val _audioFileName = MutableStateFlow("")
  val audioFileName = _audioFileName.asStateFlow()
  private val _currentAudioUri = MutableStateFlow<String?>(null)
  val currentAudioUri = _currentAudioUri.asStateFlow()

  fun setAudioSource(fileName: String, uri: String?) {
    _audioFileName.value = fileName
    _currentAudioUri.value = uri
  }

  // --- mpv playlist awareness (next/prev buttons) --------------------------
  val playlistCount by MPVLib.propInt["playlist-count"].collectAsState(viewModelScope)
  val playlistPosition by MPVLib.propInt["playlist-pos"].collectAsState(viewModelScope)

  fun playNextFile() {
    MPVLib.command("playlist-next")
  }

  fun playPreviousFile() {
    MPVLib.command("playlist-prev")
  }

  // --- Lyrics (LRC) --------------------------------------------------------
  private val _lyricState = MutableStateFlow(LyricDoc(emptyList()))
  val lyricState = _lyricState.asStateFlow()
  private val _lyricLineIndex = MutableStateFlow(-1)
  val lyricLineIndex = _lyricLineIndex.asStateFlow()
  private var lyricTickJob: Job? = null

  /** Feed LRC text (already decoded). Invalid/blank input clears the lyrics. */
  fun setLyricText(text: String?, sourceName: String = "") {
    val doc = LrcParser.parse(text)
    if (doc.isEmpty) {
      clearLyrics()
      return
    }
    _lyricState.value = doc.copy(sourceName = sourceName.ifBlank { doc.title })
    _lyricLineIndex.value = -1
    restartLyricTicker()
  }

  fun clearLyrics() {
    lyricTickJob?.cancel()
    lyricTickJob = null
    _lyricState.value = LyricDoc(emptyList())
    _lyricLineIndex.value = -1
  }

  private fun restartLyricTicker() {
    lyricTickJob?.cancel()
    lyricTickJob = viewModelScope.launch {
      while (isActive) {
        if (!_lyricState.value.isEmpty) {
          val posMs = (MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000.0
          val lines = _lyricState.value.lines
          var idx = -1
          for (i in lines.indices) {
            if (posMs >= lines[i].timeMs - 60L) idx = i else break
          }
          if (idx != _lyricLineIndex.value) _lyricLineIndex.value = idx
        }
        delay(200)
      }
    }
  }

  // --- Pitch shift (independent from speed) --------------------------------
  private val _pitchSemitones = MutableStateFlow(0)
  val pitchSemitones = _pitchSemitones.asStateFlow()

  fun setPitch(semitones: Int): Boolean {
    val ok = applyPitchFilter(semitones)
    if (ok) _pitchSemitones.value = semitones
    return ok
  }

  fun resetPitch() {
    applyPitchFilter(0)
    _pitchSemitones.value = 0
  }

  /** asetrate/aresample lavfi chain: shifts pitch without touching playback speed. */
  private fun applyPitchFilter(semitones: Int): Boolean {
    if (semitones == 0) {
      runCatching { MPVLib.setPropertyString("af", "") }
      return true
    }
    val sr = MPVLib.getPropertyInt("audio-params/samplerate") ?: 44100
    if (sr < 8000) return false
    val ratio = Math.pow(2.0, semitones / 12.0)
    val newRate = (sr * ratio).toInt().coerceIn(8000, 192000)
    val filter = "lavfi=[asetrate=$newRate,aresample=$sr]"
    val applied = runCatching {
      MPVLib.setPropertyString("af", filter)
      MPVLib.getPropertyString("af")?.contains("lavfi") == true
    }.getOrDefault(false)
    if (!applied) runCatching { MPVLib.setPropertyString("af", "") }
    return applied
  }

  // --- Audiobook-style sleep timer -----------------------------------------
  enum class AudioTimerMode { Off, Countdown, EndOfCurrent, AfterTracks }

  data class AudioTimerState(
    val mode: AudioTimerMode = AudioTimerMode.Off,
    val secondsLeft: Int = 0,
    val tracksLeft: Int = 0,
    val finishCurrent: Boolean = false,
  )

  private val _audioTimer = MutableStateFlow(AudioTimerState())
  val audioTimer = _audioTimer.asStateFlow()
  private var audioTimerJob: Job? = null
  private var audioTimerArmedUri: String? = null
  private var audioTimerLastUri: String? = null

  private fun toast(msgRes: Int, vararg fmt: Any) {
    Toast.makeText(activity, activity.getString(msgRes, *fmt), Toast.LENGTH_SHORT).show()
  }

  /** Countdown that pauses when it reaches zero (optionally after the current track ends). */
  fun armCountdown(seconds: Int, finishCurrent: Boolean) {
    audioTimerJob?.cancel()
    if (seconds < 1) return
    audioTimerArmedUri = _currentAudioUri.value
    _audioTimer.value = AudioTimerState(
      mode = AudioTimerMode.Countdown,
      secondsLeft = seconds,
      finishCurrent = finishCurrent,
    )
    toast(R.string.audio_timer_toast_on)
    audioTimerJob = viewModelScope.launch {
      while (isActive) {
        delay(1000)
        val cur = _audioTimer.value
        if (cur.mode != AudioTimerMode.Countdown) break
        if (cur.secondsLeft <= 1) {
          if (cur.finishCurrent) {
            // Wait for the end of the current track instead of pausing right away.
            audioTimerArmedUri = _currentAudioUri.value
            _audioTimer.value = AudioTimerState(mode = AudioTimerMode.EndOfCurrent, finishCurrent = true)
            toast(R.string.audio_timer_toast_wait_eof)
          } else {
            MPVLib.setPropertyBoolean("pause", true)
            _audioTimer.value = AudioTimerState()
            toast(R.string.toast_sleep_timer_ended)
          }
          break
        }
        _audioTimer.update { it.copy(secondsLeft = it.secondsLeft - 1) }
      }
    }
  }

  /** Pause when the currently playing track finishes. */
  fun armEndOfCurrent() {
    audioTimerJob?.cancel()
    audioTimerArmedUri = _currentAudioUri.value
    _audioTimer.value = AudioTimerState(mode = AudioTimerMode.EndOfCurrent)
    toast(R.string.audio_timer_toast_wait_eof)
  }

  /** Stop after [tracks] more tracks have been started (current one does not count). */
  fun armAfterTracks(tracks: Int) {
    audioTimerJob?.cancel()
    if (tracks < 1) return
    audioTimerLastUri = _currentAudioUri.value
    _audioTimer.value = AudioTimerState(mode = AudioTimerMode.AfterTracks, tracksLeft = tracks)
    toast(R.string.audio_timer_toast_on)
  }

  fun cancelAudioTimer() {
    audioTimerJob?.cancel()
    val wasActive = _audioTimer.value.mode != AudioTimerMode.Off
    _audioTimer.value = AudioTimerState()
    audioTimerArmedUri = null
    audioTimerLastUri = null
    if (wasActive) toast(R.string.audio_timer_toast_off)
  }

  private var lastLyricResetUri: String? = null

  /** Called from PlayerActivity on every MPV_EVENT_FILE_LOADED. */
  fun notifyAudioFileLoaded(uri: String?) {
    // Lyrics belong to a specific track: drop them as soon as a new file loads.
    if (uri != lastLyricResetUri) {
      lastLyricResetUri = uri
      clearLyrics()
    }
    // Re-apply pitch for the new file's sample rate if a pitch is still selected.
    val pitch = _pitchSemitones.value
    if (pitch != 0) {
      viewModelScope.launch {
        delay(700)
        applyPitchFilter(pitch)
      }
    }
    when (_audioTimer.value.mode) {
      AudioTimerMode.AfterTracks -> {
        val last = audioTimerLastUri
        if (last != null && uri != null && uri != last) {
          audioTimerLastUri = uri
          val left = _audioTimer.value.tracksLeft
          if (left > 1) {
            _audioTimer.update { it.copy(tracksLeft = left - 1) }
          } else {
            audioTimerArmedUri = uri
            _audioTimer.value = AudioTimerState(mode = AudioTimerMode.AfterTracks, tracksLeft = 0)
            toast(R.string.audio_timer_toast_wait_eof)
          }
        } else if (last == null) {
          audioTimerLastUri = uri
        }
      }
      AudioTimerMode.EndOfCurrent -> {
        val armed = audioTimerArmedUri
        if (armed != null && uri != null && uri != armed) {
          cancelAudioTimer()
          toast(R.string.audio_timer_toast_cancelled_track_changed)
        }
      }
      else -> Unit
    }
  }

  /**
   * Called from PlayerActivity when EOF is reached. Returns true when the sleep
   * timer consumed the event (i.e. it paused playback and reset itself).
   */
  fun notifyAudioEndOfFile(): Boolean {
    val cur = _audioTimer.value
    val consume = when (cur.mode) {
      AudioTimerMode.EndOfCurrent -> true
      AudioTimerMode.AfterTracks -> cur.tracksLeft == 0
      else -> false
    }
    if (!consume) return false
    MPVLib.setPropertyBoolean("pause", true)
    _audioTimer.value = AudioTimerState()
    audioTimerArmedUri = null
    audioTimerLastUri = null
    toast(R.string.toast_sleep_timer_ended)
    return true
  }

  // ===== Folder playlist + play modes ======================================
  enum class PlayMode { Sequence, RepeatOne, Shuffle }
  enum class QueueOrder { Name, TimeNewestFirst }

  data class QueueEntry(val title: String, val source: String, val modifiedMs: Long = 0L)

  data class QueueState(
    val entries: List<QueueEntry> = emptyList(),
    val index: Int = -1,
    val explicit: Boolean = false,
  )

  private val _queue = MutableStateFlow(QueueState())
  val queue = _queue.asStateFlow()
  private val _playMode = MutableStateFlow(PlayMode.Sequence)
  val playMode = _playMode.asStateFlow()
  private val _queueOrder = MutableStateFlow(QueueOrder.Name)
  val queueOrder = _queueOrder.asStateFlow()
  private var queueLoadPending = false

  /** Source currently playing because of a queue-driven load (consumed at FILE_LOADED). */
  fun takePendingQueueSource(): String? {
    if (!queueLoadPending) return null
    queueLoadPending = false
    return _queue.value.entries.getOrNull(_queue.value.index)?.source
  }

  fun requestQueuePlay(i: Int) {
    val q = _queue.value
    if (i < 0 || i >= q.entries.size) return
    val entry = q.entries[i]
    _queue.update { it.copy(index = i) }
    queueLoadPending = true
    val playable = resolveQueuePlayable(entry)
    if (playable != null) {
      // Keep mpv's media-title (top bar / MediaSession metadata) in sync with the
      // queued file even though it did not arrive through an Intent.
      runCatching { MPVLib.setOptionString("force-media-title", entry.title) }
      MPVLib.command("loadfile", playable)
    } else {
      queueLoadPending = false
    }
  }

  private fun resolveQueuePlayable(entry: QueueEntry): String? {
    return if (entry.source.startsWith("content://")) {
      runCatching { Uri.parse(entry.source).openContentFd(activity) }.getOrNull()
    } else {
      entry.source
    }
  }

  /** Called from PlayerActivity when EOF is reached; returns true when it started another track. */
  fun playAfterEndOfTrack(): Boolean {
    val q = _queue.value
    if (q.entries.isEmpty()) return false
    return when (_playMode.value) {
      PlayMode.RepeatOne -> { requestQueuePlay(q.index); true }
      PlayMode.Sequence -> if (q.index < q.entries.size - 1) { requestQueuePlay(q.index + 1); true } else false
      PlayMode.Shuffle -> if (q.entries.size > 1) {
        var r = (0 until q.entries.size).random()
        if (r == q.index) r = (q.index + 1) % q.entries.size
        requestQueuePlay(r); true
      } else false
    }
  }

  fun playUserNext(): Boolean {
    val q = _queue.value
    if (q.entries.isEmpty()) return false
    return when (_playMode.value) {
      PlayMode.Shuffle -> if (q.entries.size > 1) {
        requestQueuePlay((q.index + 1) % q.entries.size); true
      } else false
      else -> {
        // Nothing started yet (index -1 after picking a folder): "next" starts from 0.
        val next = if (q.index < 0) 0 else q.index + 1
        if (next < q.entries.size) { requestQueuePlay(next); true } else false
      }
    }
  }

  fun playUserPrev(): Boolean {
    val q = _queue.value
    if (q.entries.isEmpty() || q.index <= 0) return false
    requestQueuePlay(q.index - 1)
    return true
  }

  fun setPlayMode(mode: PlayMode) {
    _playMode.value = mode
  }

  fun cyclePlayMode() {
    _playMode.update {
      when (it) {
        PlayMode.Sequence -> PlayMode.RepeatOne
        PlayMode.RepeatOne -> PlayMode.Shuffle
        PlayMode.Shuffle -> PlayMode.Sequence
      }
    }
  }

  fun setQueueOrder(order: QueueOrder) {
    if (_queueOrder.value == order) return
    _queueOrder.value = order
    resortQueue()
  }

  private fun resortQueue() {
    val q = _queue.value
    if (q.entries.size < 2) return
    val cur = q.entries.getOrNull(q.index)?.source
    val sorted = sortQueueEntries(q.entries, _queueOrder.value)
    val ni = cur?.let { c -> sorted.indexOfFirst { it.source == c } } ?: -1
    _queue.value = q.copy(entries = sorted, index = ni)
  }

  /** Replace the queue with a folder-browser selection (recursive, may mix folders). */
  fun setExplicitQueue(entries: List<QueueEntry>) {
    _queue.value = QueueState(entries = sortQueueEntries(entries, _queueOrder.value), index = -1, explicit = true)
  }

  /**
   * Auto-build a playlist from the current local file's directory (plain file paths
   * only). Never overrides a folder-browser (explicit) queue.
   */
  fun maybeInitLocalQueue(path: String?) {
    if (path.isNullOrBlank() || path.startsWith("http") || path.startsWith("content://")) return
    if (_queue.value.explicit) return
    // intent.data keeps the "file://" prefix while mpv plays a plain path.
    val local = if (path.startsWith("file://")) path.removePrefix("file://") else path
    val file = File(local)
    if (!file.isFile) return
    val dir = file.parentFile ?: return
    val files = dir.listFiles()
      ?.filter { it.isFile && audioExtensions.contains(it.extension.lowercase()) }
      .orEmpty()
    if (files.isEmpty()) return
    val entries = files.map { QueueEntry(it.name, it.absolutePath, it.lastModified()) }
    val idx = entries.indexOfFirst { it.source == file.absolutePath }
    _queue.value = QueueState(entries = sortQueueEntries(entries, _queueOrder.value), index = idx.coerceAtLeast(0))
  }

  /** Recursively list audio files under a SAF tree (bounded), sorted by current order. */
  suspend fun scanTreeForAudio(treeUri: Uri): List<QueueEntry> {
    val out = ArrayList<QueueEntry>()
    val seen = HashSet<String>()
    val resolver = activity.contentResolver
    fun walk(docId: String, depth: Int) {
      if (depth > 8 || out.size >= 800) return
      val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
      try {
        resolver.query(
          children,
          arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
          ),
          null, null, null,
        )?.use { c ->
          while (c.moveToNext() && out.size < 800) {
            val id = c.getString(0) ?: continue
            if (!seen.add(id)) continue
            val name = c.getString(1) ?: continue
            val mime = c.getString(2) ?: ""
            val lastMod = c.getLong(3)
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
              walk(id, depth + 1)
            } else if (mime.startsWith("audio/") || audioExtensions.contains(name.substringAfterLast('.', "").lowercase())) {
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString()
              out.add(QueueEntry(title = name, source = docUri, modifiedMs = lastMod))
            }
          }
        }
      } catch (_: Exception) {
        // unreadable subtree – keep whatever was collected
      }
    }
    try {
      walk(DocumentsContract.getTreeDocumentId(treeUri), 0)
    } catch (_: Exception) {
      // invalid tree
    }
    return sortQueueEntries(out, _queueOrder.value)
  }

  private fun sortQueueEntries(entries: List<QueueEntry>, order: QueueOrder): List<QueueEntry> {
    return when (order) {
      QueueOrder.Name -> entries.sortedWith(Comparator { a, b -> naturalCompare(a.title, b.title) })
      QueueOrder.TimeNewestFirst -> entries.sortedByDescending { it.modifiedMs }
    }
  }

  private val _controlsShown = MutableStateFlow(true)
  val controlsShown = _controlsShown.asStateFlow()
  private val _seekBarShown = MutableStateFlow(true)
  val seekBarShown = _seekBarShown.asStateFlow()
  private val _areControlsLocked = MutableStateFlow(false)
  val areControlsLocked = _areControlsLocked.asStateFlow()

  val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
  val isBrightnessSliderShown = MutableStateFlow(false)
  val isVolumeSliderShown = MutableStateFlow(false)
  val currentBrightness = MutableStateFlow(
    runCatching {
      Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        .normalize(0f, 255f, 0f, 1f)
    }.getOrElse { 0f },
  )

  val sheetShown = MutableStateFlow(Sheets.None)
  val panelShown = MutableStateFlow(Panels.None)

  // Pair(startingPosition, seekAmount)
  val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

  private val _seekText = MutableStateFlow<String?>(null)
  val seekText = _seekText.asStateFlow()
  private val _doubleTapSeekAmount = MutableStateFlow(0)
  val doubleTapSeekAmount = _doubleTapSeekAmount.asStateFlow()
  private val _isSeekingForwards = MutableStateFlow(false)
  val isSeekingForwards = _isSeekingForwards.asStateFlow()

  private var timerJob: Job? = null
  private val _remainingTime = MutableStateFlow(0)
  val remainingTime = _remainingTime.asStateFlow()

  fun startTimer(seconds: Int) {
    timerJob?.cancel()
    _remainingTime.value = seconds
    if (seconds < 1) return
    timerJob = viewModelScope.launch {
      for (time in seconds downTo 0) {
        _remainingTime.value = time
        delay(1000)
      }
      MPVLib.setPropertyBoolean("pause", true)
      Toast.makeText(activity, activity.getString(R.string.toast_sleep_timer_ended), Toast.LENGTH_SHORT).show()
    }
  }

  fun cycleDecoders() {
    MPVLib.setPropertyString(
      "hwdec",
      when (Decoder.getDecoderFromValue(MPVLib.getPropertyString("hwdec-current") ?: return)) {
        Decoder.HWPlus -> Decoder.HW.value
        Decoder.HW -> Decoder.SW.value
        Decoder.SW -> Decoder.HWPlus.value
        Decoder.AutoCopy -> Decoder.SW.value
        Decoder.Auto -> Decoder.SW.value
      },
    )
  }

  fun addAudio(uri: Uri) {
    val url = uri.toString()
    val path = if (url.startsWith("content://")) url.toUri().openContentFd(activity) else url
    MPVLib.command("audio-add", path ?: return, "cached")
  }

  fun addSubtitle(uri: Uri) {
    val url = uri.toString()
    val path = if (url.startsWith("content://")) url.toUri().openContentFd(activity) else url
    MPVLib.command("sub-add", path ?: return, "cached")
  }

  fun selectSub(id: Int) {
    val selectedSubs = Pair(MPVLib.getPropertyInt("sid"), MPVLib.getPropertyInt("secondary-sid"))
    when (id) {
      selectedSubs.first -> Pair(selectedSubs.second, null)
      selectedSubs.second -> Pair(selectedSubs.first, null)
      else -> if (selectedSubs.first != null) Pair(selectedSubs.first, id) else Pair(id, null)
    }.let {
      it.second?.let { MPVLib.setPropertyInt("secondary-sid", it) } ?: MPVLib.setPropertyBoolean("secondary-sid", false)
      it.first?.let { MPVLib.setPropertyInt("sid", it) } ?: MPVLib.setPropertyBoolean("sid", false)
    }
  }

  fun pauseUnpause() = MPVLib.command("cycle", "pause")
  fun pause() = MPVLib.setPropertyBoolean("pause", true)
  fun unpause() = MPVLib.setPropertyBoolean("pause", false)

  private val showStatusBar = playerPreferences.showSystemStatusBar.get()
  fun showControls() {
    if (sheetShown.value != Sheets.None || panelShown.value != Panels.None) return
    if (showStatusBar) activity.windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
    _controlsShown.update { true }
  }

  fun hideControls() {
    activity.windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    _controlsShown.update { false }
  }

  fun hideSeekBar() {
    _seekBarShown.update { false }
  }

  fun showSeekBar() {
    if (sheetShown.value != Sheets.None) return
    _seekBarShown.update { true }
  }

  fun lockControls() {
    _areControlsLocked.update { true }
  }

  fun unlockControls() {
    _areControlsLocked.update { false }
  }

  fun seekBy(offset: Int, precise: Boolean = false) {
    MPVLib.command("seek", offset.toString(), if (precise) "relative+exact" else "relative")
  }

  fun seekTo(position: Int, precise: Boolean = true) {
    if (position !in 0..(MPVLib.getPropertyInt("duration") ?: 0)) return
    MPVLib.command("seek", position.toString(), if (precise) "absolute" else "absolute+keyframes")
  }

  fun changeBrightnessBy(change: Float) {
    changeBrightnessTo(currentBrightness.value + change)
  }

  fun changeBrightnessTo(
    brightness: Float,
  ) {
    activity.window.attributes = activity.window.attributes.apply {
      screenBrightness = brightness.coerceIn(0f, 1f).also {
        currentBrightness.update { _ -> it }
      }
    }
  }

  fun displayBrightnessSlider() {
    isBrightnessSliderShown.update { true }
  }

  val maxVolume = activity.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  fun changeVolumeBy(change: Int) {
    val mpvVolume = MPVLib.getPropertyInt("volume")
    if ((volumeBoostCap ?: audioPreferences.volumeBoostCap.get()) > 0 && currentVolume.value == maxVolume) {
      if (mpvVolume == 100 && change < 0) changeVolumeTo(currentVolume.value + change)
      val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100) ?: 100
      if (finalMPVVolume in 100..(volumeBoostCap ?: audioPreferences.volumeBoostCap.get()) + 100) {
        changeMPVVolumeTo(finalMPVVolume)
        return
      }
    }
    changeVolumeTo(currentVolume.value + change)
  }

  fun changeVolumeTo(volume: Int) {
    val newVolume = volume.coerceIn(0..maxVolume)
    activity.audioManager.setStreamVolume(
      AudioManager.STREAM_MUSIC,
      newVolume,
      0,
    )
    currentVolume.update { newVolume }
  }

  fun changeMPVVolumeTo(volume: Int) {
    MPVLib.setPropertyInt("volume", volume)
  }

  fun setMPVVolume(volume: Int) {
    if (volume != currentMPVVolume) displayVolumeSlider()
  }

  fun displayVolumeSlider() {
    isVolumeSliderShown.update { true }
  }

  fun changeVideoAspect(aspect: VideoAspect) {
    var ratio = -1.0
    var pan = 1.0
    when (aspect) {
      VideoAspect.Crop -> {
        pan = 1.0
      }

      VideoAspect.Fit -> {
        pan = 0.0
        MPVLib.setPropertyDouble("panscan", 0.0)
      }

      VideoAspect.Stretch -> {
        val dm = DisplayMetrics()
        activity.windowManager.defaultDisplay.getRealMetrics(dm)
        ratio = dm.widthPixels / dm.heightPixels.toDouble()
        pan = 0.0
      }
    }
    MPVLib.setPropertyDouble("panscan", pan)
    MPVLib.setPropertyDouble("video-aspect-override", ratio)
    playerPreferences.videoAspect.set(aspect)
    playerUpdate.update { PlayerUpdates.AspectRatio }
  }

  fun cycleScreenRotations() {
    activity.requestedOrientation = when (activity.requestedOrientation) {
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
      -> {
        playerPreferences.orientation.set(PlayerOrientation.SensorPortrait)
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
      }

      else -> {
        playerPreferences.orientation.set(PlayerOrientation.SensorLandscape)
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      }
    }
  }

  @Suppress("CyclomaticComplexMethod", "LongMethod")
  fun handleLuaInvocation(property: String, value: String) {
    val data = value
      .removePrefix("\"")
      .removeSuffix("\"")
      .ifEmpty { return }

    when (property.substringAfterLast("/")) {
      "show_text" -> playerUpdate.update { PlayerUpdates.ShowText(data) }
      "toggle_ui" -> {
        when (data) {
          "show" -> showControls()
          "toggle" -> {
            if (controlsShown.value) hideControls() else showControls()
          }

          "hide" -> {
            sheetShown.update { Sheets.None }
            panelShown.update { Panels.None }
            hideControls()
          }
        }
      }

      "show_panel" -> {
        when (data) {
          "subtitle_settings" -> panelShown.update { Panels.SubtitleSettings }
          "subtitle_delay" -> panelShown.update { Panels.SubtitleDelay }
          "audio_delay" -> panelShown.update { Panels.AudioDelay }
          "video_filters" -> panelShown.update { Panels.VideoFilters }
        }
      }

      "set_button_title" -> {
        _primaryButtonTitle.update { _ -> data }
      }

      "reset_button_title" -> {
        _customButtons.value.getButtons().firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }?.let {
          setPrimaryCustomButtonTitle(it)
        }
      }

      "seek_to_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekToWithText(seekValue.toInt(), text)
      }

      "seek_by_with_text" -> {
        val (seekValue, text) = data.split("|", limit = 2)
        seekByWithText(seekValue.toInt(), text)
      }

      "seek_by" -> seekByWithText(data.toInt(), null)
      "seek_to" -> seekToWithText(data.toInt(), null)
      "toggle_button" -> {
        fun showButton() {
          if (_primaryButton.value == null) {
            _primaryButton.update {
              customButtons.value.getButtons().firstOrNull { it.id == playerPreferences.primaryCustomButtonId.get() }
            }
          }
        }
        when (data) {
          "show" -> showButton()
          "toggle" if _primaryButton.value != null -> showButton()
          else -> _primaryButton.update { null }
        }
      }

      "software_keyboard" -> when (data) {
        "show" -> forceShowSoftwareKeyboard()
        "hide" -> forceHideSoftwareKeyboard()
        "toggle" if !inputMethodManager.isActive -> forceShowSoftwareKeyboard()
        else -> forceHideSoftwareKeyboard()
      }
    }

    MPVLib.setPropertyString(property, "")
  }

  private val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  private fun forceShowSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
  }

  private fun forceHideSoftwareKeyboard() {
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
  }

  private fun seekToWithText(seekValue: Int, text: String?) {
    _isSeekingForwards.value = seekValue > 0
    _doubleTapSeekAmount.value = seekValue - (pos ?: return)
    _seekText.update { text }
    seekTo(seekValue, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  private fun seekByWithText(value: Int, text: String?) {
    _doubleTapSeekAmount.update {
      if (value < 0 && it < 0 || (pos ?: return) + value > (duration ?: return)) 0 else it + value
    }
    _seekText.update { text }
    _isSeekingForwards.value = value > 0
    seekBy(value, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  private val doubleTapToSeekDuration = gesturePreferences.doubleTapToSeekDuration.get()

  fun updateSeekAmount(amount: Int) {
    _doubleTapSeekAmount.update { amount }
  }

  fun updateSeekText(text: String?) {
    _seekText.update { text }
  }

  fun leftSeek() {
    if ((pos ?: return) > 0) _doubleTapSeekAmount.value -= doubleTapToSeekDuration
    _isSeekingForwards.value = false
    seekBy(-doubleTapToSeekDuration, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun rightSeek() {
    if ((pos ?: return) < (duration ?: return)) {
      _doubleTapSeekAmount.value += doubleTapToSeekDuration
    }
    _isSeekingForwards.value = true
    seekBy(doubleTapToSeekDuration, playerPreferences.preciseSeeking.get())
    if (playerPreferences.showSeekBarWhenSeeking.get()) showSeekBar()
  }

  fun handleLeftDoubleTap() {
    when (gesturePreferences.leftSingleActionGesture.get()) {
      SingleActionGesture.Seek -> {
        leftSeek()
      }

      SingleActionGesture.PlayPause -> {
        pauseUnpause()
      }

      SingleActionGesture.Custom -> {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
      }

      SingleActionGesture.None -> {}
    }
  }

  fun handleCenterDoubleTap() {
    when (gesturePreferences.centerSingleActionGesture.get()) {
      SingleActionGesture.PlayPause -> {
        pauseUnpause()
      }

      SingleActionGesture.Custom -> {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
      }

      SingleActionGesture.Seek -> {}
      SingleActionGesture.None -> {}
    }
  }

  fun handleRightDoubleTap() {
    when (gesturePreferences.rightSingleActionGesture.get()) {
      SingleActionGesture.Seek -> {
        rightSeek()
      }

      SingleActionGesture.PlayPause -> {
        pauseUnpause()
      }

      SingleActionGesture.Custom -> {
        MPVLib.command("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
      }

      SingleActionGesture.None -> {}
    }
  }

  fun setPrimaryCustomButtonTitle(button: CustomButtonEntity) {
    _primaryButtonTitle.update { _ -> button.title }
  }
}

/** Natural (human) ordering: "2" sorts before "10", ignoring case. */
private fun naturalCompare(a: String, b: String): Int {
  val regex = Regex("(\\d+)|(\\D+)")
  val pa = regex.findAll(a).map { it.value }.toList()
  val pb = regex.findAll(b).map { it.value }.toList()
  val n = minOf(pa.size, pb.size)
  for (i in 0 until n) {
    val x = pa[i]; val y = pb[i]
    if (x.all { it.isDigit() } && y.all { it.isDigit() }) {
      val dx = x.trimStart('0').let { if (it.isEmpty()) 0 else it.toLong() }
      val dy = y.trimStart('0').let { if (it.isEmpty()) 0 else it.toLong() }
      if (dx != dy) return dx.compareTo(dy)
    } else {
      val cmp = x.compareTo(y, ignoreCase = true)
      if (cmp != 0) return cmp
    }
  }
  return pa.size.compareTo(pb.size)
}

fun Float.normalize(inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
  return (this - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
}

fun CustomButtonEntity.execute() {
  MPVLib.command("script-message", "call_button_$id")
}

fun CustomButtonEntity.executeLongClick() {
  MPVLib.command("script-message", "call_button_${id}_long")
}

fun <T> Flow<T>.collectAsState(scope: CoroutineScope, initialValue: T? = null) =
  object : ReadOnlyProperty<Any?, T?> {
    private var value: T? = initialValue
    init { scope.launch { collect { value = it } } }
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
  }
