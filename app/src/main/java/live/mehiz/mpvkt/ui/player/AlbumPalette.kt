package live.mehiz.mpvkt.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightweight tonal palette extracted from the current album artwork. */
data class AlbumPalette(
  val primary: Color = Color(0xFF1F1F28),
  val onPrimary: Color = Color(0xFFF2F2F4),
  val accent: Color = Color(0xFF8AB4FF),
  val accentSoft: Color = Color(0x338AB4FF),
  val muted: Color = Color(0xB3FFFFFF),
) {
  companion object {
    val Default = AlbumPalette()
  }
}

object AlbumPaletteExtractor {

  /**
   * Sample the cover bitmap at 16×16 to find a vivid, non-grey accent and a
   * darkish primary tone. Runs on Default dispatcher; safe from main thread.
   */
  suspend fun fromBitmap(bitmap: Bitmap?): AlbumPalette = withContext(Dispatchers.Default) {
    if (bitmap == null) return@withContext AlbumPalette.Default
    val src = runCatching { Bitmap.createScaledBitmap(bitmap, 16, 16, true) ?: bitmap }.getOrNull()
      ?: return@withContext AlbumPalette.Default
    var bestAccent = -0x1
    var bestSat = -1f
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var n = 0
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    for (px in pixels) {
      val a = (px ushr 24) and 0xFF
      if (a < 32) continue
      val r = (px ushr 16) and 0xFF
      val g = (px ushr 8) and 0xFF
      val b = px and 0xFF
      rSum += r; gSum += g; bSum += b; n++
      val max = maxOf(r, g, b)
      val min = minOf(r, g, b)
      val sat = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
      if (sat > bestSat && max - min > 24 && sat > 0.18f) {
        bestSat = sat
        bestAccent = px
      }
    }
    val accent = if (bestAccent == -0x1) {
      val avg = if (n == 0) 0xFF1F1F28.toInt() else
        (0xFF000000.toInt() or
          ((rSum / n).toInt() shl 16) or
          ((gSum / n).toInt() shl 8) or
          (bSum / n).toInt())
      avg
    } else bestAccent
    val primary = darken(accent, 0.55f)
    val muted = withAlpha(accent, 0.55f)
    return@withContext AlbumPalette(
      primary = Color(primary),
      onPrimary = Color(0xFFF2F2F4),
      accent = Color(accent),
      accentSoft = Color(accent).copy(alpha = 0.22f),
      muted = Color(muted),
    )
  }

  private fun darken(c: Int, factor: Float): Int {
    val a = (c ushr 24) and 0xFF
    val r = (((c ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
    val g = (((c ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
    val b = ((c and 0xFF) * factor).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
  }

  private fun withAlpha(c: Int, alpha: Float): Int {
    val r = (c ushr 16) and 0xFF
    val g = (c ushr 8) and 0xFF
    val b = c and 0xFF
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
  }
}
