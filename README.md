# <img alt="app icon" src=".github/assets/app_icon.svg" width="48" /> mpvKt
A media player for Android based on [mpv-android](https://github.com/mpv-android/mpv-android) aiming to provide a *nicer* user interface over the original.

[![Build](https://github.com/abdallahmehiz/mpvKt/actions/workflows/build.yml/badge.svg)](https://github.com/abdallahmehiz/mpvKt/actions/workflows/build.yml)
## Additional features
- Nicer player UI
- Better playback history implementation
- Easier customization
- Sleep timer, Speed presets
- Smoother PiP
- **Audio + video integrated player** (v0.4.0+): mpvKt now plays audio-only files (mp3 / flac / opus / m4a …) with a dedicated music-player page — file name in the top bar, embedded album art, seekbar with timers, playlist prev/play-pause/next, ±10 s seek, playback speed (0.5–2×) and pitch (±7 semitones), synchronized LRC lyrics (auto-load a sibling .lrc or pick one), and an audiobook-style sleep timer (countdown / stop at end of current track / stop after N more tracks). The home-screen mini-player re-opens the player on tap. Set the UI policy in **Settings → Player → Audio mode**: *Auto* (default), *Always audio UI*, or *Always video UI*.

## Showcase

<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="24%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="24%"> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png" width="24%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6_en-US.png" width="49%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/7_en-US.png" width="49%" /> <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/8_en-US.png" width="49%" />

## Installation
you can download the app from the [Github releases page](https://github.com/abdallahmehiz/mpvKt/releases) or one of the official sources listed below:

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" height="80">](https://f-droid.org/en/packages/live.mehiz.mpvkt/)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" height="80">](https://apt.izzysoft.de/fdroid/index/apk/live.mehiz.mpvkt)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=live.mehiz.mpvkt)

You can also access nightly builds from [here](https://github.com/abdallahmehiz/mpvKt/actions/workflows/nightlies.yml)
## Acknowledgments
- [mpv-android](https://github.com/mpv-android) for the base mpv library to use for this project.