# v0.4.1+40 — Audio page polish, fixes & SMB streaming

Follow-up batch after v0.4.0+39 (all installed & verified on Huawei ALN-AL10):

## Audio page
- **No wrong-UI flash**: player reveals its page only after the first file loads and the
  audio/video container is settled (black transition + ±250 ms), on cold start and on
  switching files inside a live instance.
- **Orientation**: the audio page forces portrait *before the window is created* (extension
  + file-header sniffing for opaque SAF ids), and rotation animations are disabled
  (jump-cut), so mp3s never flash landscape first.
- **Lock (audio)**: single stable top button with two states (🔓 unlocked / 🔒 locked);
  locking keeps the artwork visible, hides the bottom controls and disables gestures.
- Gesture feel: per-event delta clamps, eased scrubbing, volume now operates mpv 0-100%
  with % feedback; hold = 2×; lock stable.
- Embedded (unsynced) lyrics are read from the file tag and shown as a static block.

## Video player lock
- Locking no longer hides controls instantly: the lock glyph switches in place, other
  buttons/seekbar stay put, then auto-hide after the normal timeout; tapping brings them
  back with the lock still locked and at the same bottom-left position.

## File browser & history
- Single-file system picker removed; the in-app folder browser is the one file entry.
- Browser lists folders/files asynchronously (no ANR on big trees), long-press opens a
  menu (play now / play from this file / play whole folder incl. sub-folders).
- History now stores the real source URI for queue/browser plays and self-cleans stale
  bare-name entries; tapping history resumes reliably.

## SMB
- **Multi-profile**: profiles are stored and switchable from a dropdown (✎ rename dialog,
  ＋ new); server config migration from the old single entry.
- **Edge case**: moov-at-end MP4s now stream too — head is prefetched, the tail
  (moov/index) is placed first, playback starts, and the middle gap fills in the
  background; download progress now reports real totals (previously always 100%).
- Diagnostics logs (SMB tag) for streaming decisions.

Build: ./gradlew :app:assembleDebug (all 4 ABIs).
