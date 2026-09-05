package live.mehiz.mpvkt.ui.player

/** One synchronized lyric line. */
data class LyricLine(val timeMs: Long, val text: String)

/** Parsed LRC document. Times already include any [offset:...] tag. */
data class LyricDoc(
  val lines: List<LyricLine>,
  val title: String = "",
  val artist: String = "",
  val sourceName: String = "",
) {
  val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Minimal LRC parser supporting [mm:ss], [mm:ss.xx], [mm:ss.xxx], multiple
 * timestamps per line, metadata tags ([ti:], [ar:], [al:], [offset:ms]) and
 * blank lyric lines (kept as gaps between stanzas is not needed – they are dropped).
 */
object LrcParser {
  private val TS = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

  fun parse(raw: String?): LyricDoc {
    if (raw == null || raw.isBlank()) return LyricDoc(emptyList())
    var title = ""
    var artist = ""
    var offsetMs = 0L
    val out = ArrayList<LyricLine>()
    for (rawLine in raw.lineSequence()) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue
      val meta = Regex("""\[(ti|title|ar|artist|al|offset|by|length):(.*)]""", RegexOption.IGNORE_CASE).find(line)
      if (meta != null) {
        when (meta.groupValues[1].lowercase()) {
          "ti", "title" -> title = meta.groupValues[2].trim()
          "ar", "artist" -> artist = meta.groupValues[2].trim()
          "offset" -> offsetMs = meta.groupValues[2].trim().toLongOrNull() ?: 0L
        }
        continue
      }
      val tags = TS.findAll(line).toList()
      if (tags.isEmpty()) continue
      val text = line.substring(tags.last().range.last + 1).trim()
      for (tag in tags) {
        val m = tag.groupValues
        val minutes = m[1].toLongOrNull() ?: continue
        val seconds = m[2].toLongOrNull() ?: continue
        val fracRaw = m[3]
        val fracMs = when {
          fracRaw.isEmpty() -> 0L
          fracRaw.length >= 3 -> fracRaw.take(3).toLong()
          else -> fracRaw.padEnd(2, '0').toLong() * 10L // .x / .xx as centiseconds
        }
        val ms = minutes * 60_000L + seconds * 1000L + fracMs + offsetMs
        out.add(LyricLine(timeMs = ms.coerceAtLeast(0L), text = text))
      }
    }
    out.sortBy { it.timeMs }
    return LyricDoc(lines = out, title = title, artist = artist, sourceName = "")
  }
}
