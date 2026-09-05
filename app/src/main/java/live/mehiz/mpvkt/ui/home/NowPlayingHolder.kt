package live.mehiz.mpvkt.ui.home

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of the current playback – title, artist, art, paused flag – that [PlayerActivity]
 * updates and the home screen reads via a small Koin singleton.
 *
 * This avoids having to launch mpv on the home screen just to render a tiny mini-player.
 *
 * See:
 *  - [live.mehiz.mpvkt.ui.home.MiniPlayer] for the Composable UI.
 *  - [live.mehiz.mpvkt.di.AppModule] for the Koin registration.
 */
@Immutable
data class NowPlayingState(
  val uri: String,
  val title: String,
  val artist: String,
  val album: String,
  val isPlaying: Boolean,
  val artwork: Bitmap?,
  /** True while the in-process mpv core backing this snapshot is alive. */
  val coreActive: Boolean,
) {
  val hasContent: Boolean get() = uri.isNotBlank()

  companion object {
    val EMPTY: NowPlayingState = NowPlayingState(
      uri = "",
      title = "",
      artist = "",
      album = "",
      isPlaying = false,
      artwork = null,
      coreActive = false,
    )
  }
}

class NowPlayingHolder {
  private val _state: MutableStateFlow<NowPlayingState> = MutableStateFlow(NowPlayingState.EMPTY)
  val state: StateFlow<NowPlayingState> = _state.asStateFlow()

  /** Push a new snapshot from the active [PlayerActivity]. Safe to call from any thread. */
  fun update(snapshot: NowPlayingState) {
    _state.update { snapshot }
  }

  /** Convenience used when playback ends or the activity finishes. */
  fun clear() {
    _state.update { NowPlayingState.EMPTY }
  }
}
