package live.mehiz.mpvkt.ui.player

import androidx.annotation.StringRes
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.DecoderPreferences
import live.mehiz.mpvkt.preferences.preference.Preference

enum class PlayerOrientation(@StringRes val titleRes: Int) {
  Free(R.string.pref_player_orientation_free),
  Video(R.string.pref_player_orientation_video),
  Portrait(R.string.pref_player_orientation_portrait),
  ReversePortrait(R.string.pref_player_orientation_reverse_portrait),
  SensorPortrait(R.string.pref_player_orientation_sensor_portrait),
  Landscape(R.string.pref_player_orientation_landscape),
  ReverseLandscape(R.string.pref_player_orientation_reverse_landscape),
  SensorLandscape(R.string.pref_player_orientation_sensor_landscape),
}

enum class VideoAspect(@StringRes val titleRes: Int) {
  Crop(R.string.player_aspect_crop),
  Fit(R.string.player_aspect_fit),
  Stretch(R.string.player_aspect_stretch),
}

/**
 * Controls how the player surfaces audio-only files (mp3/flac/opus/... or any file with no video track).
 *
 * - [Auto]:        show audio UI iff the loaded file has no video track (default, backwards compatible).
 * - [AudioOnly]:   always render audio UI (forces MPVView hidden, useful when listening to music videos).
 * - [VideoUiOnly]: always render the existing video-centric UI even for audio-only files.
 */
enum class AudioMode(@StringRes val titleRes: Int, @StringRes val summaryRes: Int) {
  Auto(R.string.pref_audio_mode_auto, R.string.pref_audio_mode_auto_summary),
  AudioOnly(R.string.pref_audio_mode_audio, R.string.pref_audio_mode_audio_summary),
  VideoUiOnly(R.string.pref_audio_mode_video, R.string.pref_audio_mode_video_summary),
}

enum class SingleActionGesture(@StringRes val titleRes: Int) {
  None(R.string.pref_gesture_double_tap_none),
  Seek(R.string.pref_gesture_double_tap_seek),
  PlayPause(R.string.pref_gesture_double_tap_play),
  Custom(R.string.pref_gesture_double_tap_custom),
}

enum class CustomKeyCodes(val keyCode: String) {
  DoubleTapLeft("0x10001"),
  DoubleTapCenter("0x10002"),
  DoubleTapRight("0x10003"),
  MediaPrevious("0x10004"),
  MediaPlay("0x10005"),
  MediaNext("0x10006"),
}

enum class Decoder(val title: String, val value: String) {
  AutoCopy("Auto", "auto-copy"),
  Auto("Auto", "auto"),
  SW("SW", "no"),
  HW("HW", "mediacodec-copy"),
  HWPlus("HW+", "mediacodec"),
  ;

  companion object {
    fun getDecoderFromValue(value: String): Decoder {
      return Decoder.entries.first { it.value == value }
    }
  }
}

enum class Debanding(@StringRes val titleRes: Int) {
  None(R.string.player_sheets_deband_none),
  CPU(R.string.player_sheets_deband_cpu),
  GPU(R.string.player_sheets_deband_gpu),
}

enum class Sheets {
  None,
  PlaybackSpeed,
  SubtitleTracks,
  AudioTracks,
  Chapters,
  Decoders,
  More,
}

enum class Panels {
  None,
  SubtitleSettings,
  SubtitleDelay,
  AudioDelay,
  VideoFilters,
}

sealed class PlayerUpdates {
  data object None : PlayerUpdates()
  data object MultipleSpeed : PlayerUpdates()
  data object AspectRatio : PlayerUpdates()
  data class ShowText(val value: String) : PlayerUpdates()
}

enum class VideoFilters(
  @StringRes val titleRes: Int,
  val preference: (DecoderPreferences) -> Preference<Int>,
  val mpvProperty: String,
) {
  BRIGHTNESS(
    R.string.player_sheets_filters_brightness,
    { it.brightnessFilter },
    "brightness",
  ),
  SATURATION(
    R.string.player_sheets_filters_Saturation,
    { it.saturationFilter },
    "saturation",
  ),
  CONTRAST(
    R.string.player_sheets_filters_contrast,
    { it.contrastFilter },
    "contrast",
  ),
  GAMMA(
    R.string.player_sheets_filters_gamma,
    { it.gammaFilter },
    "gamma",
  ),
  HUE(
    R.string.player_sheets_filters_hue,
    { it.hueFilter },
    "hue",
  ),
}

enum class DebandSettings(
  @StringRes val titleRes: Int,
  val preference: (DecoderPreferences) -> Preference<Int>,
  val mpvProperty: String,
  val start: Int,
  val end: Int,
) {
  Iterations(
    R.string.player_sheets_deband_iterations,
    { it.debandIterations },
    "deband-iterations",
    0,
    16,
  ),
  Threshold(
    R.string.player_sheets_deband_threshold,
    { it.debandThreshold },
    "deband-threshold",
    0,
    200,
  ),
  Range(
    R.string.player_sheets_deband_range,
    { it.debandRange },
    "deband-range",
    1,
    64,
  ),
  Grain(
    R.string.player_sheets_deband_grain,
    { it.debandGrain },
    "deband-grain",
    0,
    200,
  )
}
