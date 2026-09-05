# v0.4.0+39 — Audio + Video integrated player

## 🎉 Major: Audio & Video integrated player

mpvKt can now play pure audio files (mp3, flac, opus, m4a …) with a dedicated music-player UI instead of the black video surface.

### New: Audio-mode UI (`AudioPlayerOverlay`)

When the current file has no video track — or when you explicitly force it — the
activity swaps to a Spotify-style overlay:

- Large artwork (extracted from the embedded cover with Android's
  `MediaMetadataRetriever` — no mpv video pipeline involved)
- Title / artist / album metadata straight from mpv
- Large round play/pause + skip-back/forward
- Album-aware seekbar with mm:ss timers
- Bottom row with speed / sleep timer / "switch to video" toggle
- All gestures stay consistent with the video UI

The `MPVView` stays visible underneath: the audio overlay is an opaque
full-screen Compose layer, so the user never sees it. Hiding the view was
attempted first (to save battery) but had to be reverted — on several devices,
including Huawei, toggling the `SurfaceView` visibility tears down the
surface and silently stops audio playback.

### New: Audio mode preference

`PlayerPreferences.audioMode` controls when the audio UI kicks in:

| Mode            | Behaviour |
|-----------------|-----------|
| **Auto** (default) | Audio UI when the file has no video track |
| **Always audio UI** | Always audio UI (good for music videos) |
| **Always video UI** | Original behaviour even for audio-only files |

A per-session override toggle is available inside the audio overlay so you can
flip individual files without changing the preference.

### New: Mini-player on the home screen

When playback is active, a Material 3 `MiniPlayer` strip appears at the bottom of
`HomeScreen`:

- Title + artist
- Thumbnail
- Play/pause button that sends an intent to `MediaPlaybackService`
- Close button that stops playback

Tapping the strip (anywhere outside the buttons) reopens `PlayerActivity` for the
current track. The shared state lives in a Koin singleton (`NowPlayingHolder`)
so the home screen does not have to instantiate mpv.

### Improved: Background playback (MediaPlaybackService)

- Switched the audio attributes' content type from `CONTENT_TYPE_MOVIE` to
  `CONTENT_TYPE_MUSIC`, so system surfaces (notification shade, lock-screen
  controls, Auto, Wear OS) group playback under **Music**.
- `MediaMetadataCompat` now includes `ALBUM`, `TRACK_NUMBER` and
  `DISPLAY_DESCRIPTION`, and the artwork is refreshed each time the metadata
  changes so embedded cover art propagates correctly.

### Improved: Album art extraction

Cover art is read with Android's `MediaMetadataRetriever` (`embeddedPicture`)
right after `FILE_LOADED`, then pushed into `PlayerViewModel.albumArt` for both
`AudioPlayerOverlay` and the mini-player. This deliberately avoids mpv's video
pipeline entirely:

- we do **not** pass `--audio-display=attachment`,
- we do **not** call `MPVLib.grabThumbnail()`,
- `MPVView.visibility` is **never** changed.

Why: on several devices (Huawei included) toggling a `SurfaceView` to
`INVISIBLE`/`GONE` tears down and re-creates its surface, which strands mpv's
video-output pipeline and silently stops audio playback. The opaque
`AudioPlayerOverlay` Compose layer already sits on top of the `MPVView`, so
hiding the view was never necessary — this change both fixes the silent audio
bug and removes the periodic `screenshot-raw command failed` log spam.

### Plumbing

- `PlayerViewModel`
  - `isAudioOnly: StateFlow<Boolean>` (combines `track-list` content + user pref +
    per-session override)
  - `audioMetadata: StateFlow<AudioTrackInfo>` (title / artist / album)
  - `albumArt: StateFlow<Bitmap?>` (set once from the extracted
    embedded cover; falls back to a music-note icon)
  - `startAlbumArtCapture() / stopAlbumArtCapture()`
  - `toggleAudioOnlyMode()`
- `PlayerEnums.AudioMode { Auto, AudioOnly, VideoUiOnly }`
- `NowPlayingHolder` (Koin singleton) bridges `PlayerActivity` ↔ `MiniPlayer`
- `PlayerActivity` extracts the embedded cover with
  `MediaMetadataRetriever` on `FILE_LOADED`, pushes it into
  `viewModel.setAlbumArt(...)` and the mini-player, and never touches
  `MPVView.visibility`.
- `PlayerControls` branches to `AudioPlayerOverlay` when `isAudioOnly`, while
  still keeping the existing sheets / panels / sheets plumbing.

### Files touched

```
M  app/src/main/java/live/mehiz/mpvkt/ui/player/PlayerEnums.kt
A  app/src/main/java/live/mehiz/mpvkt/ui/player/controls/AudioPlayerOverlay.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/player/controls/PlayerControls.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/player/PlayerActivity.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/player/PlayerViewModel.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/player/MPVView.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/player/MediaPlaybackService.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/home/HomeScreen.kt
A  app/src/main/java/live/mehiz/mpvkt/ui/home/MiniPlayer.kt
A  app/src/main/java/live/mehiz/mpvkt/ui/home/NowPlayingHolder.kt
M  app/src/main/java/live/mehiz/mpvkt/ui/preferences/PlayerPreferencesScreen.kt
M  app/src/main/java/live/mehiz/mpvkt/preferences/PlayerPreferences.kt
M  app/src/main/java/live/mehiz/mpvkt/di/AppModule.kt
M  app/src/main/res/values/strings.xml
M  app/src/main/res/values-zh-rCN/strings.xml
```

### Round 2 – Music-player page, lyrics, sleep timer (on-device verified)

Follow-up feedback batch implemented and installed on the Huawei ALN-AL10:

- **Mini-player tap fixed.** The strip is drawn *above* the scrollable home content
  (z-order bug – the scroll Column used to swallow every tap) and now re-opens
  PlayerActivity; reopening the *same* file no longer restarts playback
  (onNewIntent skips a redundant loadfile). If the mpv core is gone, the play/pause
  button falls back to opening the player (coreActive flag in NowPlayingHolder).
- **Audio page rebuilt** (AudioPlayerOverlay) like a dedicated music app: file name
  in the top bar, large artwork / metadata pane, seekbar with timers, transport row
  with playlist prev / play-pause / next and ±10 s seek, plus popup panels for
  **playback speed** (0.5–2.0×, saved as default) and **pitch** (±7 semitones via an
  asetrate/aresample lavfi chain, guarded with a fallback toast).
- **Lyrics**: LRC parsing (LrcParser), a synchronized auto-scrolling lyrics pane
  (tap a line to seek), auto-loading of a sibling .lrc for file sources and a
  manual picker (SAF, persistent grant) for any source.
- **Sleep timer**: countdown presets (immediate pause, or finish the current track
  first), “stop when the current track ends”, and “stop after N more tracks”
  (counted via FILE_LOADED, pausing at the N-th track's end). Timer state is shown
  on the bottom chip and is cancelled when the track changes while armed.

Verified on device: audio plays in Auto mode with an active AudioFlinger track, no
FATAL; switching files and re-opening the player from the home mini-player work.

### Round 3 – Audio page v2 polish (per user review)

- Audio playback now **locks to portrait** (video UI re-applies the orientation preference on exit).
- Audio page v2: full-page artwork hero (no overlap), **transparent floating top bar**, bottom controls
  stack (seekbar / transport / chips) on a clean gradient — music-app style.
- **Live readouts**: the speed panel shows the current value (×) updating in real time while the
  slider moves; the pitch panel shows the semitone value live during the drag (audio is applied on release).
- Sleep timer redesign: behavior selector (pause immediately / finish current track first), minute
  presets, and a **custom minutes stepper** (±), plus “stop by track” options; panels use a
  bottom-sheet chrome with a drag handle.

### Round 4 – Playlist/play modes, seekbar drag, folder browse (user review 2)

- Seekbar is draggable again (drag state no longer fights live position updates; release seeks).
- Same-directory auto playlist for local files (natural file-name order), plus sorting by name / time;
  folder picker (SAF tree) recursively lists audio including sub-folders, with sort chips and a track list.
- Play modes: in order / repeat single / shuffle (queue bar above the controls; EOF auto-advance honours the mode).
- Speed / pitch preset chips now wrap onto two lines so nothing is clipped; values update live during slider drag.
- Every sheet/panel dismisses via the handle, the X, tapping the dim area, or the system back button; cancelled
  system file/folder pickers show a toast and return cleanly.

### Round 5 – Visual redesign to commercial player conventions

- Full-bleed **blurred artwork backdrop** (Spotify style) with a dark scrim; gradient fallback when no art.
- **Centered artwork card** (rounded, subtle hairline) as the hero; album text hidden while art is shown.
- **Slim seekbar** (white thumb/track) with elapsed and remaining time on one row, flanked by −10/+10 pills.
- Main transport reduced to **prev / play / next**; repeat / order / queue info moved to a quiet link line above the seekbar.
- Bottom dock = **icons only with a tiny dot** for active states (lyrics / speed / pitch / timer / folder).
- All sheets share one chrome (rounded 22dp, drag handle, close X, scrim/back dismissal) and use a single blue accent with neutral row styling instead of colourful pills.

### Verified

`./gradlew :app:assembleDebug` builds clean (all 4 ABIs).
