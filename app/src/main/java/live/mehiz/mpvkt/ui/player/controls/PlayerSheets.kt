package live.mehiz.mpvkt.ui.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.ui.player.Decoder
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.Sheets
import live.mehiz.mpvkt.ui.player.TrackNode
import live.mehiz.mpvkt.ui.player.controls.components.sheets.AudioTracksSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.ChaptersSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.DecodersSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.MoreSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.PlaybackSpeedSheet
import live.mehiz.mpvkt.ui.player.controls.components.sheets.SubtitlesSheet

@Composable
fun PlayerSheets(
  sheetShown: Sheets,

  // subtitles sheet
  subtitles: ImmutableList<TrackNode>,
  onAddSubtitle: (Uri) -> Unit,
  onSelectSubtitle: (Int) -> Unit,
  onOpenSubtitleFinder: () -> Unit,
  // audio sheet
  audioTracks: ImmutableList<TrackNode>,
  onAddAudio: (Uri) -> Unit,
  onSelectAudio: (TrackNode) -> Unit,
  // chapters sheet
  chapter: Segment?,
  chapters: ImmutableList<Segment>,
  onSeekToChapter: (Int) -> Unit,
  // Decoders sheet
  decoder: Decoder,
  onUpdateDecoder: (Decoder) -> Unit,
  // Speed sheet
  speed: Float,
  speedPresets: List<Float>,
  onSpeedChange: (Float) -> Unit,
  onAddSpeedPreset: (Float) -> Unit,
  onRemoveSpeedPreset: (Float) -> Unit,
  onResetSpeedPresets: () -> Unit,
  onMakeDefaultSpeed: (Float) -> Unit,
  onResetDefaultSpeed: () -> Unit,
  // More sheet
  sleepTimerTimeRemaining: Int,
  onStartSleepTimer: (Int) -> Unit,
  buttons: ImmutableList<CustomButtonEntity>,

  onOpenPanel: (Panels) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  when (sheetShown) {
    Sheets.None -> {}
    Sheets.SubtitleTracks -> {
      val subtitlesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
      ) {
        if (it == null) return@rememberLauncherForActivityResult
        // W31.18: take 持久 URI 权限,否则下次再开/重选就 SecurityException
        try {
          val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
          context.contentResolver.takePersistableUriPermission(it, flags)
        } catch (_: SecurityException) { /* provider 不允许则跳过 */ }
        onAddSubtitle(it)
      }
      SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onSelect = onSelectSubtitle,
        onAddSubtitle = { subtitlesPicker.launch(arrayOf("*/*")) },
        onOpenSubtitleSettings = { onOpenPanel(Panels.SubtitleSettings) },
        onOpenSubtitleDelay = { onOpenPanel(Panels.SubtitleDelay) },
        onOpenSubtitleFinder = onOpenSubtitleFinder,
        onDismissRequest = onDismissRequest,
      )
    }

    Sheets.AudioTracks -> {
      val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
      ) {
        if (it == null) return@rememberLauncherForActivityResult
        // W31.18: take 持久 URI 权限
        try {
          val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
          context.contentResolver.takePersistableUriPermission(it, flags)
        } catch (_: SecurityException) { /* skip */ }
        onAddAudio(it)
      }
      AudioTracksSheet(
        tracks = audioTracks,
        onSelect = onSelectAudio,
        onAddAudioTrack = { audioPicker.launch(arrayOf("*/*")) },
        onOpenDelayPanel = { onOpenPanel(Panels.AudioDelay) },
        onDismissRequest,
      )
    }

    Sheets.Chapters -> {
      if (chapter == null) return
      ChaptersSheet(
        chapters,
        currentChapter = chapter,
        onClick = { onSeekToChapter(chapters.indexOf(it)) },
        onDismissRequest,
      )
    }

    Sheets.Decoders -> {
      DecodersSheet(
        selectedDecoder = decoder,
        onSelect = onUpdateDecoder,
        onDismissRequest,
      )
    }

    Sheets.More -> {
      MoreSheet(
        remainingTime = sleepTimerTimeRemaining,
        onStartTimer = onStartSleepTimer,
        onDismissRequest = onDismissRequest,
        onEnterFiltersPanel = { onOpenPanel(Panels.VideoFilters) },
        customButtons = buttons,
      )
    }

    Sheets.PlaybackSpeed -> {
      PlaybackSpeedSheet(
        speed,
        onSpeedChange = onSpeedChange,
        speedPresets = speedPresets,
        onAddSpeedPreset = onAddSpeedPreset,
        onRemoveSpeedPreset = onRemoveSpeedPreset,
        onResetPresets = onResetSpeedPresets,
        onMakeDefault = onMakeDefaultSpeed,
        onResetDefault = onResetDefaultSpeed,
        onDismissRequest = onDismissRequest,
      )
    }
  }
}
