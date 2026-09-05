package live.mehiz.mpvkt.ui.player.controls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Movie
import `is`.xyz.mpv.MPVLib
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.ui.player.LyricDoc
import live.mehiz.mpvkt.ui.player.PlayerViewModel
import live.mehiz.mpvkt.ui.player.PlayerViewModel.AudioTimerMode
import live.mehiz.mpvkt.ui.player.PlayerViewModel.PlayMode
import live.mehiz.mpvkt.ui.player.PlayerViewModel.QueueOrder
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

// Design tokens - restrained dark palette, single blue accent.
private val Accent = Color(0xFF8AB4FF)
private val Ink = Color(0xFF0B0B10)
private val TextHi = Color.White
private val TextMid = Color.White.copy(alpha = 0.6f)
private val TextLow = Color.White.copy(alpha = 0.35f)
private val Hairline = Color.White.copy(alpha = 0.14f)
private val Danger = Color(0xFFFF8A80)
private val SheetBg = Color(0xFF16161C)
private val RowBg = Color(0xFF23232B)

private enum class AudioPanel { None, Speed, Pitch, Timer }

@Composable
fun AudioPlayerOverlay(viewModel: PlayerViewModel, onBackPress: () -> Unit, modifier: Modifier = Modifier) {
  val context: Context = LocalContext.current
  val metadata by viewModel.audioMetadata.collectAsState()
  val fileName by viewModel.audioFileName.collectAsState()
  val artwork by viewModel.albumArt.collectAsState()
  val speed by MPVLib.propFloat["speed"].collectAsState()
  val pitchSemitones by viewModel.pitchSemitones.collectAsState()
  val timerState by viewModel.audioTimer.collectAsState()
  val lyrics by viewModel.lyricState.collectAsState()
  val lyricIndex by viewModel.lyricLineIndex.collectAsState()
  val queueState by viewModel.queue.collectAsState()
  val playMode by viewModel.playMode.collectAsState()
  val queueOrder by viewModel.queueOrder.collectAsState()
  val mpvPaused by MPVLib.propBoolean["pause"].collectAsState()
  val scope = rememberCoroutineScope()
  val uxPrefs = remember { context.getSharedPreferences("audio_ux", Context.MODE_PRIVATE) }
  var restored by remember { mutableStateOf(false) }

  var showLyrics by remember { mutableStateOf(false) }
  var panel by remember { mutableStateOf(AudioPanel.None) }
  var showQueueSheet by remember { mutableStateOf(false) }
  var folderLoading by remember { mutableStateOf(false) }
  var dragPosition by remember { mutableStateOf<Float?>(null) }
  var controlsLocked by remember { mutableStateOf(false) }
  var hint by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(hint) { if (hint != null) { delay(900); hint = null } }

  BackHandler(enabled = panel != AudioPanel.None || showQueueSheet) {
    if (panel != AudioPanel.None) panel = AudioPanel.None else showQueueSheet = false
  }
  // Current file name (or queue entry title) is the primary heading; mpv metadata is the fallback.
  val displayTitle = fileName.ifBlank { metadata.title.ifBlank { stringResource(R.string.mini_player_unknown_title) } }

  val cancelText = stringResource(R.string.audio_picker_cancelled)
  val lyricPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) { Toast.makeText(context, cancelText, Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    scope.launch(Dispatchers.IO) {
      val text = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      }.getOrNull()
      viewModel.setLyricText(text, uri.lastPathSegment ?: "")
    }
  }
  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
    if (tree == null) { Toast.makeText(context, cancelText, Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
    runCatching {
      context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    scope.launch {
      folderLoading = true
      val entries = withContext(Dispatchers.IO) { viewModel.scanTreeForAudio(tree) }
      folderLoading = false
      if (entries.isNotEmpty()) {
        viewModel.setExplicitQueue(entries)
        uxPrefs.edit().putString("folder_tree", tree.toString()).apply()
        Toast.makeText(context, context.getString(R.string.audio_queue_added, entries.size), Toast.LENGTH_SHORT).show()
        showQueueSheet = false
        viewModel.requestQueuePlay(0)
      } else {
        Toast.makeText(context, context.getString(R.string.audio_queue_title), Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Restore the previously picked folder playlist (persisted tree grant) once.
  LaunchedEffect(Unit) {
    if (!restored) {
      restored = true
      val tree = uxPrefs.getString("folder_tree", null)
      if (tree != null && viewModel.queue.value.entries.isEmpty()) {
        val orderIdx = uxPrefs.getInt("folder_order", QueueOrder.Name.ordinal).coerceIn(0, QueueOrder.values().size - 1)
        viewModel.setQueueOrder(QueueOrder.values()[orderIdx])
        val modeIdx = uxPrefs.getInt("folder_mode", PlayMode.Sequence.ordinal).coerceIn(0, PlayMode.values().size - 1)
        viewModel.setPlayMode(PlayMode.values()[modeIdx])
        folderLoading = true
        val entries = withContext(Dispatchers.IO) { viewModel.scanTreeForAudio(Uri.parse(tree)) }
        folderLoading = false
        if (entries.isNotEmpty()) viewModel.setExplicitQueue(entries)
      }
    }
  }
  LaunchedEffect(queueOrder) { uxPrefs.edit().putInt("folder_order", queueOrder.ordinal).apply() }
  LaunchedEffect(playMode) { uxPrefs.edit().putInt("folder_mode", playMode.ordinal).apply() }
  LaunchedEffect(mpvPaused) { android.util.Log.i("AudioUX", "paused=" + mpvPaused) }

  Box(modifier = modifier.fillMaxSize().systemBarsPadding()) {
    // Full-bleed blurred artwork as the ambient backdrop (Spotify style).
    val art = artwork
    if (art != null) {
      Box(modifier = Modifier.fillMaxSize()) {
        Image(bitmap = art.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().blur(46.dp), contentScale = ContentScale.Crop)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0E).copy(alpha = 0.66f)))
        Box(
          modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
              listOf(
                Color.Black.copy(alpha = 0.35f),
                Color.Transparent,
                Color.Transparent,
                Color(0xEE060608),
              ),
            ),
          ),
        )
      }
    } else {
      Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15151B), Color(0xFF050508)))))
    }

    Column(modifier = Modifier.fillMaxSize()) {
      // ===== Minimal floating top bar =====
      Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackPress) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.mini_player_open), tint = TextMid) }
        Text(
          text = displayTitle,
          color = TextHi, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        IconButton(onClick = { controlsLocked = !controlsLocked }) {
          Icon(
            if (controlsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            stringResource(R.string.audio_player_switch_to_video),
            tint = if (controlsLocked) Accent else TextMid,
          )
        }
        IconButton(onClick = { viewModel.toggleAudioOnlyMode() }) { Icon(Icons.Outlined.Movie, stringResource(R.string.audio_player_switch_to_video), tint = TextMid) }
      }

      // ===== Main pane =====
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (showLyrics && !lyrics.isEmpty) {
          LyricsPane(viewModel, lyrics, lyricIndex)
        } else {
          NowPlayingPane(displayTitle, metadata.artist, metadata.album, artwork)
          if (!controlsLocked) {
            AudioGestureSurface(
              viewModel = viewModel,
              onHint = { hint = it },
              modifier = Modifier.matchParentSize(),
            )
          }
        }
        // Gesture feedback bubble
        hint?.let { h ->
          Box(
            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(h, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      // ===== Bottom cluster =====
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        if (!controlsLocked) {
        if (queueState.entries.isNotEmpty()) {
          ModeLine(
            mode = playMode, order = queueOrder,
            position = (queueState.index + 1).coerceAtLeast(1), total = queueState.entries.size,
            onCycleMode = { viewModel.cyclePlayMode() },
            onOpenSheet = { showQueueSheet = true },
            onCycleOrder = { viewModel.setQueueOrder(if (queueOrder == QueueOrder.Name) QueueOrder.TimeNewestFirst else QueueOrder.Name) },
          )
          Spacer(Modifier.height(4.dp))
        }
        val durationF = (viewModel.duration ?: 0).toFloat()
        val maxD = if (durationF > 0f) durationF else 1f
        val shownPos = dragPosition ?: (viewModel.pos?.toFloat() ?: 0f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          SeekPill("−10", onClick = { viewModel.seekBy(-10) })
          Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(formatTime(shownPos.toLong().coerceAtLeast(0L)), color = TextMid, fontSize = 11.sp)
              Text("−" + formatTime((durationF - shownPos).toLong().coerceAtLeast(0L)), color = TextMid, fontSize = 11.sp)
            }
            Slider(
              value = shownPos.coerceIn(0f, maxD),
              onValueChange = { dragPosition = it },
              onValueChangeFinished = { dragPosition?.let { viewModel.seekTo(it.toInt(), precise = true) }; dragPosition = null },
              valueRange = 0f..maxD,
              enabled = durationF > 0f,
              colors = SliderDefaults.colors(thumbColor = TextHi, activeTrackColor = TextHi, inactiveTrackColor = Hairline.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          SeekPill("+10", onClick = { viewModel.seekBy(10) })
        }
        Spacer(Modifier.height(2.dp))
        // Main transport: only three keys
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
          val canPrev = queueState.entries.isNotEmpty() && queueState.index > 0
          val canNext = queueState.entries.isNotEmpty() && queueState.index < queueState.entries.size - 1
          IconButton(onClick = { viewModel.playUserPrev() }, enabled = canPrev, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.audio_player_seek_back), tint = if (canPrev) TextHi else TextLow, modifier = Modifier.size(40.dp))
          }
          Box(
            modifier = Modifier.size(76.dp).clip(CircleShape).background(TextHi.copy(alpha = 0.14f)).border(1.dp, TextHi.copy(alpha = 0.18f), CircleShape).clickable {
              android.util.Log.i("AudioUX", "play button tapped, paused=" + mpvPaused)
              if (mpvPaused != false) {
                // At the end of a track (or an idle player) mpv cannot resume forward;
                // restart from the beginning so the play button always responds audibly.
                val d = viewModel.duration ?: 0
                val p = viewModel.pos ?: 0
                if (d <= 0 || p >= d - 2) viewModel.seekTo(0, precise = true)
                viewModel.unpause()
              } else {
                viewModel.pause()
              }
            },
            contentAlignment = Alignment.Center,
          ) {
            Icon(if (mpvPaused != false) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = TextHi, modifier = Modifier.size(42.dp))
          }
          IconButton(onClick = { viewModel.playUserNext() }, enabled = canNext, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Filled.SkipNext, stringResource(R.string.audio_player_seek_forward), tint = if (canNext) TextHi else TextLow, modifier = Modifier.size(40.dp))
          }
        }
        Spacer(Modifier.height(4.dp))
        // Icon dock: icons + tiny active dot
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
          DockIcon(Icons.Filled.QueueMusic, !lyrics.isEmpty, showLyrics, stringResource(R.string.audio_player_lyrics), onClick = {
            if (lyrics.isEmpty) lyricPicker.launch(arrayOf("*/*")) else showLyrics = !showLyrics
          })
          DockIcon(Icons.Filled.Speed, false, false, stringResource(R.string.audio_player_speed), onClick = { panel = if (panel == AudioPanel.Speed) AudioPanel.None else AudioPanel.Speed })
          DockIcon(Icons.Filled.GraphicEq, pitchSemitones != 0, false, stringResource(R.string.audio_player_pitch), onClick = { panel = if (panel == AudioPanel.Pitch) AudioPanel.None else AudioPanel.Pitch })
          DockIcon(Icons.Filled.Timer, timerState.mode != AudioTimerMode.Off, false, stringResource(R.string.audio_player_timer), onClick = { panel = if (panel == AudioPanel.Timer) AudioPanel.None else AudioPanel.Timer })
          DockIcon(Icons.Filled.FolderOpen, false, false, stringResource(R.string.audio_chip_folder), onClick = { showQueueSheet = true })
        }
        Spacer(Modifier.height(8.dp))
        }
      }
    }

    // ===== Sheets layer =====
    if (panel != AudioPanel.None || showQueueSheet) {
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable {
        panel = AudioPanel.None
        showQueueSheet = false
      })
      val sheetMod = Modifier.align(Alignment.BottomCenter)
      when {
        panel == AudioPanel.Speed -> SpeedSheet(currentSpeed = speed ?: 1f, onClose = { panel = AudioPanel.None }, modifier = sheetMod)
        panel == AudioPanel.Pitch -> PitchSheet(viewModel, semitones = pitchSemitones, onClose = { panel = AudioPanel.None }, modifier = sheetMod)
        panel == AudioPanel.Timer -> TimerSheet(viewModel, timerState, onClose = { panel = AudioPanel.None }, modifier = sheetMod)
        showQueueSheet -> QueueSheet(viewModel, queueState, playMode, queueOrder, folderLoading, { folderPicker.launch(null) }, { i -> viewModel.requestQueuePlay(i); showQueueSheet = false }, { viewModel.cyclePlayMode() }, { o -> viewModel.setQueueOrder(o) }, { showQueueSheet = false }, sheetMod)
        else -> Unit
      }
    }

  }
}

// ===== Now playing pane =====================================================
@Composable
private fun NowPlayingPane(title: String, artist: String, album: String, artwork: android.graphics.Bitmap?) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(28.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C24)).border(0.5.dp, Hairline, RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
    ) {
      if (artwork != null) Image(bitmap = artwork.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      else Icon(Icons.Filled.MusicNote, stringResource(R.string.audio_player_no_album_art), tint = TextMid, modifier = Modifier.size(72.dp))
    }
    Spacer(Modifier.height(30.dp))
    Text(text = title.ifBlank { stringResource(R.string.mini_player_unknown_title) }, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(text = artist.ifBlank { stringResource(R.string.audio_player_unknown_artist) }, color = TextMid, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    if (album.isNotBlank() && artwork == null) {
      Spacer(Modifier.height(3.dp))
      Text(text = album, color = TextLow, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
  }
}

// ===== Lyrics ===============================================================
@Composable
private fun LyricsPane(viewModel: PlayerViewModel, lyrics: LyricDoc, activeIndex: Int) {
  val listState = rememberLazyListState()
  LaunchedEffect(activeIndex) { if (activeIndex >= 0) runCatching { listState.animateScrollToItem(activeIndex.coerceAtLeast(0)) } }
  LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    item { Spacer(Modifier.height(24.dp)) }
    itemsIndexed(lyrics.lines) { i, line ->
      val active = i == activeIndex
      Text(
        text = line.text.ifBlank { " " },
        color = if (active) TextHi else TextLow,
        fontSize = if (active) 18.sp else 15.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(enabled = lyrics.synced) { viewModel.seekTo((line.timeMs / 1000L).toInt(), precise = true) }.padding(horizontal = 24.dp, vertical = 8.dp),
      )
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

// ===== Bottom widgets =======================================================
@Composable
private fun SeekPill(label: String, onClick: () -> Unit) {
  Text(text = label, color = TextMid, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(CircleShape).background(Hairline.copy(alpha = 0.35f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun ModeLine(mode: PlayMode, order: QueueOrder, position: Int, total: Int, onCycleMode: () -> Unit, onOpenSheet: () -> Unit, onCycleOrder: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
    LinkText(text = when (mode) {
      PlayMode.Sequence -> stringResource(R.string.audio_mode_sequence)
      PlayMode.RepeatOne -> stringResource(R.string.audio_mode_repeat_one)
      PlayMode.Shuffle -> stringResource(R.string.audio_mode_shuffle)
    }, onClick = onCycleMode)
    Text("  ·  ", color = TextLow)
    LinkText(text = stringResource(R.string.audio_queue_track_count, position, total), onClick = onOpenSheet)
    Text("  ·  ", color = TextLow)
    LinkText(text = if (order == QueueOrder.Name) stringResource(R.string.audio_queue_sort_name) else stringResource(R.string.audio_queue_sort_time), onClick = onCycleOrder)
  }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
  Text(text = text, color = TextMid, fontSize = 11.5.sp, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 2.dp))
}

@Composable
private fun DockIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, dot: Boolean, active: Boolean, contentDesc: String, onClick: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp)) {
    Icon(icon, contentDesc, tint = if (active) Accent else TextMid, modifier = Modifier.size(22.dp))
    Spacer(Modifier.height(3.dp))
    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (dot) Accent else Color.Transparent))
  }
}

// ===== Shared sheet chrome ==================================================
@Composable
private fun SheetScaffold(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(
    modifier = modifier.fillMaxWidth().border(1.dp, Hairline.copy(alpha = 0.6f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
    color = SheetBg,
    tonalElevation = 6.dp,
    shadowElevation = 18.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
      // Whole top strip (handle area) is tap-to-dismiss.
      Box(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClose).padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter,
      ) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hairline.copy(alpha = 0.6f)))
      }
      Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Close, null, tint = TextMid, modifier = Modifier.size(18.dp)) }
      }
      content()
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(text = text.uppercase(), color = TextLow, fontSize = 11.sp, letterSpacing = 0.6.sp)
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Text(
    text = text, color = if (selected) Accent else TextMid, fontSize = 13.sp,
    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    modifier = modifier.clip(RoundedCornerShape(18.dp))
      .border(1.dp, if (selected) Accent else Hairline.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
      .background(if (selected) Accent.copy(alpha = 0.13f) else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
  )
}

// ===== Speed sheet ==========================================================
@Composable
private fun SpeedSheet(currentSpeed: Float, onClose: () -> Unit, modifier: Modifier = Modifier) {
  val prefs = koinInject<PlayerPreferences>()
  var drag by remember { mutableFloatStateOf(-1f) }
  val shown = if (drag >= 0f) drag else currentSpeed
  fun apply(v: Float) { MPVLib.setPropertyFloat("speed", v); prefs.defaultSpeed.set(v) }
  SheetScaffold(title = stringResource(R.string.audio_player_speed), onClose = onClose, modifier = modifier) {
    Text(text = "%.2f×".format(shown), color = TextHi, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Slider(
      value = shown.coerceIn(0.5f, 2f), onValueChange = { drag = it; apply(it) }, onValueChangeFinished = { drag = -1f },
      valueRange = 0.5f..2f, steps = 14,
      colors = SliderDefaults.colors(thumbColor = TextHi, activeTrackColor = Accent, inactiveTrackColor = Hairline),
      modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 18.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.5f, 0.75f, 1f, 1.25f).forEach { p -> ChoicePill(formatSpeed(p) + "×", kotlin.math.abs(p - currentSpeed) < 0.01f, onClick = { drag = -1f; apply(p) }) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1.5f, 1.75f, 2f).forEach { p -> ChoicePill(formatSpeed(p) + "×", kotlin.math.abs(p - currentSpeed) < 0.01f, onClick = { drag = -1f; apply(p) }) }
      }
    }
  }
}

// ===== Pitch sheet ==========================================================
@Composable
private fun PitchSheet(viewModel: PlayerViewModel, semitones: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var drag by remember { mutableIntStateOf(Int.MIN_VALUE) }
  val shown = if (drag != Int.MIN_VALUE) drag else semitones
  fun commit(v: Int) {
    if (!viewModel.setPitch(v)) Toast.makeText(context, context.getString(R.string.audio_player_pitch_failed), Toast.LENGTH_SHORT).show()
    drag = Int.MIN_VALUE
  }
  SheetScaffold(title = stringResource(R.string.audio_player_pitch), onClose = onClose, modifier = modifier) {
    Text(text = if (shown == 0) "0" else (if (shown > 0) "+" else "") + shown, color = TextHi, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Slider(
      value = shown.toFloat().coerceIn(-7f, 7f), onValueChange = { drag = it.toInt() }, onValueChangeFinished = { commit(shown) },
      valueRange = -7f..7f, steps = 13,
      colors = SliderDefaults.colors(thumbColor = TextHi, activeTrackColor = Accent, inactiveTrackColor = Hairline),
      modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 18.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-5, -3, -1, 0).forEach { s -> ChoicePill(if (s == 0) "0" else (if (s > 0) "+" else "") + s, semitones == s, onClick = { commit(s) }) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3, 5).forEach { s -> ChoicePill("+" + s, semitones == s, onClick = { commit(s) }) }
      }
    }
  }
}

// ===== Timer sheet ==========================================================
private val TIMER_PRESETS = listOf(10, 20, 30, 45, 60, 90)

@Composable
private fun TimerSheet(viewModel: PlayerViewModel, timerState: PlayerViewModel.AudioTimerState, onClose: () -> Unit, modifier: Modifier = Modifier) {
  var finishCurrent by rememberSaveable { mutableStateOf(false) }
  var customOn by rememberSaveable { mutableStateOf(false) }
  var customText by rememberSaveable { mutableStateOf("45") }
  fun parseMinutes(): Int = customText.toIntOrNull()?.coerceIn(1, 1440) ?: 45
  fun armMinutes(min: Int) { customOn = false; viewModel.armCountdown(min * 60, finishCurrent) }
  SheetScaffold(title = stringResource(R.string.audio_player_timer), onClose = onClose, modifier = modifier) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (timerState.mode != AudioTimerMode.Off) {
        val summary = when (timerState.mode) {
          AudioTimerMode.Countdown -> {
            val remain = stringResource(R.string.audio_timer_remaining, formatTime(timerState.secondsLeft.toLong()))
            if (timerState.finishCurrent) remain + " \u00b7 " + stringResource(R.string.audio_timer_behavior_finish_track) else remain
          }
          AudioTimerMode.EndOfCurrent -> stringResource(R.string.audio_timer_mode_end_of_current)
          AudioTimerMode.AfterTracks -> if (timerState.tracksLeft == 0) stringResource(R.string.audio_timer_mode_end_of_current) else stringResource(R.string.audio_timer_mode_after_tracks, timerState.tracksLeft)
          AudioTimerMode.Off -> ""
        }
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RowBg).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(summary, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
          Text(stringResource(R.string.audio_timer_cancel), color = Danger, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.cancelAudioTimer() })
        }
      }
      SectionLabel(stringResource(R.string.audio_timer_behavior))
      Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RowBg).clickable { finishCurrent = !finishCurrent }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(checked = finishCurrent, onCheckedChange = { finishCurrent = it }, colors = CheckboxDefaults.colors(checkedColor = Accent))
        Text(stringResource(R.string.audio_timer_check_finish_episode), color = TextHi, fontSize = 14.sp, modifier = Modifier.weight(1f))
      }
      Text(stringResource(R.string.audio_timer_immediate_default), color = TextLow, fontSize = 11.sp)
      SectionLabel(stringResource(R.string.audio_timer_pick_minutes))
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TIMER_PRESETS.take(4).forEach { m -> ChoicePill("$m’", false, { armMinutes(m) }, Modifier.weight(1f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TIMER_PRESETS.drop(4).forEach { m -> ChoicePill("$m’", false, { armMinutes(m) }, Modifier.weight(1f)) }
          ChoicePill(stringResource(R.string.audio_timer_custom_minutes), customOn, { customOn = !customOn }, Modifier.weight(1f))
        }
      }
      if (customOn) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
          RoundStepper("−", { customText = (parseMinutes() - 1).coerceAtLeast(1).toString() })
          OutlinedTextField(
            value = customText,
            onValueChange = { v -> customText = v.filter { it.isDigit() }.take(4) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = TextHi, fontSize = 20.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = TextHi,
              unfocusedTextColor = TextHi,
              cursorColor = Accent,
              focusedBorderColor = Accent,
              unfocusedBorderColor = Hairline,
            ),
            modifier = Modifier.width(96.dp),
          )
          Text("分钟", color = TextMid, fontSize = 14.sp)
          RoundStepper("+", { customText = (parseMinutes() + 1).coerceAtMost(1440).toString() })
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.audio_timer_start), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(14.dp)).background(Accent).clickable { armMinutes(parseMinutes()) }.padding(horizontal = 26.dp, vertical = 11.dp))
      }
      SectionLabel(stringResource(R.string.audio_timer_extra_tracks))
      ChoicePill(stringResource(R.string.audio_timer_mode_end_of_current), timerState.mode == AudioTimerMode.EndOfCurrent, onClick = { viewModel.armEndOfCurrent() }, modifier = Modifier.fillMaxWidth())
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 5).forEach { n ->
          ChoicePill(if (n == 1) stringResource(R.string.audio_timer_tracks_1) else stringResource(R.string.audio_timer_tracks_n, n), timerState.mode == AudioTimerMode.AfterTracks && timerState.tracksLeft == n, { viewModel.armAfterTracks(n) }, Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun RoundStepper(label: String, onClick: () -> Unit) {
  Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(RowBg).border(1.dp, Hairline, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, color = TextHi, fontSize = 22.sp)
  }
}

// ===== Queue sheet ==========================================================
@Composable
private fun QueueSheet(
  viewModel: PlayerViewModel,
  queueState: PlayerViewModel.QueueState,
  playMode: PlayMode,
  queueOrder: QueueOrder,
  loading: Boolean,
  onPickFolder: () -> Unit,
  onPlay: (Int) -> Unit,
  onCycleMode: () -> Unit,
  onSetOrder: (QueueOrder) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth().fillMaxHeight(0.8f).border(1.dp, Hairline.copy(alpha = 0.6f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
    color = SheetBg,
    tonalElevation = 6.dp,
    shadowElevation = 18.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
      Box(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter,
      ) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hairline.copy(alpha = 0.6f)))
      }
      Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.audio_queue_title), color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Close, null, tint = TextMid, modifier = Modifier.size(18.dp)) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoicePill(when (playMode) {
          PlayMode.Sequence -> stringResource(R.string.audio_mode_sequence)
          PlayMode.RepeatOne -> stringResource(R.string.audio_mode_repeat_one)
          PlayMode.Shuffle -> stringResource(R.string.audio_mode_shuffle)
        }, true, onCycleMode, Modifier.weight(1f))
        ChoicePill(stringResource(R.string.audio_queue_sort_name), queueOrder == QueueOrder.Name, { onSetOrder(QueueOrder.Name) }, Modifier.weight(1f))
        ChoicePill(stringResource(R.string.audio_queue_sort_time), queueOrder == QueueOrder.TimeNewestFirst, { onSetOrder(QueueOrder.TimeNewestFirst) }, Modifier.weight(1f))
      }
      Spacer(Modifier.height(10.dp))
      if (loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent, strokeWidth = 3.dp) }
      } else if (queueState.entries.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(stringResource(R.string.audio_queue_empty), color = TextMid, fontSize = 13.sp, textAlign = TextAlign.Center)
          Spacer(Modifier.height(16.dp))
          ChoicePill(stringResource(R.string.audio_queue_pick_folder), false, onPickFolder)
        }
      } else {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
          itemsIndexed(queueState.entries) { i, entry ->
            val active = i == queueState.index
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (active) Accent.copy(alpha = 0.12f) else Color.Transparent).clickable { onPlay(i) }.padding(horizontal = 12.dp, vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text((i + 1).toString(), color = if (active) Accent else TextLow, fontSize = 12.sp, modifier = Modifier.width(32.dp))
              Text(entry.title, color = if (active) TextHi else TextMid, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
          }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.audio_queue_pick_folder), color = Accent, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onPickFolder).padding(vertical = 10.dp))
        Spacer(Modifier.height(4.dp))
      }
    }
  }
}

// ===== Formatting ===========================================================
/** Audio-page gestures: hold = 2x speed, horizontal drag = seek, vertical drag = volume (0-100%). */
@Composable
private fun AudioGestureSurface(viewModel: PlayerViewModel, onHint: (String) -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var lastX = down.position.x
        var lastY = down.position.y
        var totalX = 0f
        var totalY = 0f
        var mode = 0 // 0 none, 1 seek, 2 volume, 3 hold-2x
        var seekStart = viewModel.pos ?: 0
        var lastSeek = seekStart
        var speedBoosted = false
        var volAcc = 0f
        var hintTick = 0L
        val maxDx = size.width / 60f
        val maxDy = size.height / 60f
        while (true) {
          val ev = awaitPointerEvent()
          val change = ev.changes.firstOrNull { it.id == down.id } ?: break
          val rawDx = change.position.x - lastX
          val rawDy = change.position.y - lastY
          lastX = change.position.x
          lastY = change.position.y
          val dx = rawDx.coerceIn(-maxDx, maxDx)
          val dy = rawDy.coerceIn(-maxDy, maxDy)
          totalX += dx
          totalY += dy
          if (mode != 0) change.consume()
          if (mode == 0) {
            val slop = 20f
            val idleTime = change.uptimeMillis - down.uptimeMillis
            if (idleTime > 600L && kotlin.math.abs(totalX) < slop && kotlin.math.abs(totalY) < slop) {
              mode = 3
              val base = MPVLib.getPropertyDouble("speed") ?: 1.0
              MPVLib.setPropertyDouble("speed", base * 2.0)
              speedBoosted = true
              onHint("2x")
            } else if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.4f && kotlin.math.abs(totalX) > slop) {
              mode = 1
              seekStart = viewModel.pos ?: 0
              lastSeek = seekStart
              change.consume()
            } else if (kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.4f && kotlin.math.abs(totalY) > slop) {
              mode = 2
              change.consume()
            }
          }
          when (mode) {
            1 -> {
              val dur = (viewModel.duration ?: 0).toFloat()
              if (dur > 0f) {
                val raw = (totalX / size.width.toFloat()) * dur * 0.75f
                val eased = kotlin.math.sign(raw) * kotlin.math.sqrt(kotlin.math.abs(raw)) * 6f
                val target = (seekStart + eased).toInt().coerceIn(0, viewModel.duration ?: 0)
                if (kotlin.math.abs(target - lastSeek) >= 1) {
                  if (target != viewModel.pos) viewModel.seekTo(target, precise = false)
                  lastSeek = target
                  val delta = target - seekStart
                  val now = System.currentTimeMillis()
                  if (now - hintTick > 120) {
                    hintTick = now
                    onHint((if (delta >= 0) "+" else "") + formatTime(kotlin.math.abs(delta).toLong()))
                  }
                }
              }
            }
            2 -> {
              val step = 100f / (size.height.toFloat() * 1.5f)
              volAcc += -dy * step
              if (kotlin.math.abs(volAcc) >= 1f) {
                val d = volAcc.toInt()
                val cur = MPVLib.getPropertyInt("volume") ?: 100
                val next = (cur + d).coerceIn(0, 100)
                MPVLib.setPropertyInt("volume", next)
                volAcc -= d
                val now = System.currentTimeMillis()
                if (now - hintTick > 120) {
                  hintTick = now
                  onHint("音量 " + next + "%")
                }
              }
            }
          }
          if (!change.pressed) break
        }
        if (mode == 3 && speedBoosted) {
          val base = MPVLib.getPropertyDouble("speed") ?: 1.0
          MPVLib.setPropertyDouble("speed", base / 2.0)
        }
      }
    },
  )
}

private fun formatTime(seconds: Long): String {
  if (seconds <= 0) return "0:00"
  val h = seconds / 3600
  val m = (seconds % 3600) / 60
  val s = seconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSpeed(v: Float): String {
  if (v <= 0f) return "1"
  return if (v % 1f == 0f) "%d".format(v.toInt())
  else if ((v * 2f) % 1f == 0f) "%.1f".format(v)
  else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
