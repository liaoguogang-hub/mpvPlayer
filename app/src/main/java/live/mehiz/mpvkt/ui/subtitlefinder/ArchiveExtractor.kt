package live.mehiz.mpvkt.ui.subtitlefinder

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/// W29.5 升级:解压结果改为 List<SubtitleFile> 含原始中文名。
///
/// W29 之前:candidates.maxByOrNull { it.length() } 选最大,但 zimuku 字幕包常含 6 个
/// 字幕(不同字幕组/简繁英多语言/多集),用户要的不是"最大的那个",要的是某个特定
/// 版本(中文/简繁/第几集)。PlexiPlay archive_service.dart 同样返回 List 让上层选。
///
/// W29.5 真因:
///   1. 选最大常常选错版本(英文版 vs 中文版 / S01E01 vs S01E02)
///   2. 文件名被改写成 `${stamp}_${idx}.${ext}` 时间戳+idx,UI 无法显示原中文名
///   3. PlexiPlay 修法:返回 SubtitleCandidate(path + 原名 + ext),UI 弹底部列表选
data class SubtitleFile(
  val path: File,
  val originalName: String,
)

/// W29 升级:支持 zip + rar + 7z + GBK 文件名解码。
///
/// 真因(参考 PlexiPlay subtitle_player archive_service.dart 注释):
///   1. zimuku / SubHD 下回来的 .zip 后缀文件,内容经常其实是 rar 或 7z,
///      直接用 ZipInputStream 抛异常被 runCatching 吞掉,返回 null,
///      UI 报"解压后没找到字幕文件"。
///   2. zip 内中文文件名用 GBK 编码(Windows 上传源),Java 默认 UTF-8
///      解码后变成乱码,扩展名匹配失败 (.srt 变 .乱码)。
///   3. PlexiPlay 修法:文件头 magic bytes 嗅探 + GBK/GB18030 fallback +
///      用 junrar / commons-compress 解 rar / 7z。
///
/// 文件头 sniff:
///   - ZIP: PK\x03\x04 (0x50 0x4B 0x03 0x04)
///   - RAR4: Rar!\x1A\x07 (0x52 0x61 0x72 0x21 0x1A 0x07)
///   - 7z:  7z\xBC\xAF\x27\x1C (0x37 0x7A 0xBC 0xAF 0x27 0x1C)
object ArchiveExtractor {

  private val SUBTITLE_EXTS = setOf("srt", "ass", "ssa", "vtt")

  enum class Kind { ZIP, RAR, SEVENZ, UNKNOWN }

  /// 嗅探前 8 字节判定真实格式。zimuku / SubHD 经常后缀是 .zip 但内容是 rar / 7z。
  fun sniff(file: File): Kind {
    return runCatching {
      FileInputStream(file).use { input ->
        val buf = ByteArray(8)
        val n = input.read(buf)
        if (n < 4) return Kind.UNKNOWN
        when {
          buf[0] == 0x50.toByte() && buf[1] == 0x4B.toByte() &&
            (buf[2] == 0x03.toByte() || buf[2] == 0x05.toByte() || buf[2] == 0x07.toByte()) -> Kind.ZIP
          buf[0] == 0x52.toByte() && buf[1] == 0x61.toByte() && buf[2] == 0x72.toByte() &&
            buf[3] == 0x21.toByte() && buf[4] == 0x1A.toByte() && buf[5] == 0x07.toByte() -> Kind.RAR
          buf[0] == 0x37.toByte() && buf[1] == 0x7A.toByte() &&
            buf[2] == 0xBC.toByte() && buf[3] == 0xAF.toByte() &&
            buf[4] == 0x27.toByte() && buf[5] == 0x1C.toByte() -> Kind.SEVENZ
          else -> Kind.UNKNOWN
        }
      }
    }.getOrDefault(Kind.UNKNOWN)
  }

  /// 总入口:嗅探 → 选对应 extractor → 返回全部字幕文件(包含原中文名)。
  /// unknown 走 zip 兜底(命中 fail 时返回空列表)。
  fun extractAll(archive: File, outDir: File): List<SubtitleFile> {
    outDir.mkdirs()
    return when (sniff(archive)) {
      Kind.ZIP -> extractZip(archive, outDir)
      Kind.RAR -> extractRar(archive, outDir)
      Kind.SEVENZ -> extractSevenZ(archive, outDir)
      Kind.UNKNOWN -> extractZip(archive, outDir) // 兜底
    }
  }

  /// 兼容旧 API:只有一个时直接返回,多个时按体积大小排第一个(给"快速选"用)。
  fun extract(archive: File, outDir: File): SubtitleFile? =
    extractAll(archive, outDir).maxByOrNull { it.path.length() }

  // -------- ZIP --------

  private fun extractZip(archive: File, outDir: File): List<SubtitleFile> {
    val result = mutableListOf<SubtitleFile>()
    runCatching {
      ZipInputStream(FileInputStream(archive)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          val rawName = entry.name
          // zip 文件名编码修复:zimuku Windows 源用 GBK,Java 默认 UTF-8 → 乱码
          val fixedName = fixZipEntryName(rawName)
          val name = fixedName.substringAfterLast('/')
          val lower = name.lowercase()
          if (!entry.isDirectory && name.isNotEmpty() &&
            SUBTITLE_EXTS.any { lower.endsWith(".$it") }
          ) {
            val ext = lower.substringAfterLast('.')
            // W30:保留原中文文件名,不再 ASCII 化。mpvKt addSubtitle 走 sub-add + native path,
            // 中文路径 OK。最终文件会被 SubtitleLibraryManager rename 到 libraryDir,
            // 这里 outDir 只是个中转站,文件名冲突时再唯一化。
            val out = uniqueFile(outDir, name, ext)
            FileOutputStream(out).use { fos -> zis.copyTo(fos) }
            result.add(SubtitleFile(out, name))
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
    }
    return result
  }

  /// 同名文件已存在就追加 (1) (2) ... 后缀,避免覆盖。
  private fun uniqueFile(dir: File, name: String, ext: String): File {
    val candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val stem = candidate.nameWithoutExtension
    for (i in 1..999) {
      val next = File(dir, "$stem ($i).$ext")
      if (!next.exists()) return next
    }
    return File(dir, "${stem}_${System.currentTimeMillis()}.$ext")
  }

  /// 修复 zip entry name 编码。Java 默认 UTF-8 解码中文压缩源常丢字符,
  /// 跟 PlexiPlay archive_service.dart:_fixZipName 同样思路:已含 CJK 字符视为
  /// 解码正确;否则按 GBK 重新解码。
  private fun fixZipEntryName(name: String): String {
    if (name.any { c -> c.code in 0x4E00..0x9FFF }) return name
    val latin1Bytes = name.toByteArray(Charsets.ISO_8859_1)
    return runCatching { String(latin1Bytes, java.nio.charset.Charset.forName("GB18030")) }.getOrNull()
      ?: runCatching { String(latin1Bytes, java.nio.charset.Charset.forName("GBK")) }.getOrNull()
      ?: name
  }

  // -------- RAR (junrar) --------

  private fun extractRar(archive: File, outDir: File): List<SubtitleFile> {
    val result = mutableListOf<SubtitleFile>()
    runCatching {
      Archive(archive).use { rar ->
        var header = rar.nextFileHeader()
        while (header != null) {
          if (!header.isDirectory) {
            // junrar 5+ 用 fileName,旧版本用 fileNameString,做兼容。
            val rawName = try {
              header.fileName
            } catch (_: Throwable) {
              @Suppress("DEPRECATION")
              header.fileNameString
            }
            val name = rawName.trim().replace('\\', '/').substringAfterLast('/')
            val lower = name.lowercase()
            if (name.isNotEmpty() && SUBTITLE_EXTS.any { lower.endsWith(".$it") }) {
              val ext = lower.substringAfterLast('.')
              val out = uniqueFile(outDir, name, ext)
              FileOutputStream(out).use { fos ->
                rar.extractFile(header, fos)
              }
              result.add(SubtitleFile(out, name))
            }
          }
          header = rar.nextFileHeader()
        }
      }
    }
    return result
  }

  // -------- 7z (commons-compress) --------

  private fun extractSevenZ(archive: File, outDir: File): List<SubtitleFile> {
    val result = mutableListOf<SubtitleFile>()
    runCatching {
      SevenZFile(archive).use { sevenZ ->
        var entry = sevenZ.nextEntry
        val buf = ByteArray(8192)
        while (entry != null) {
          if (!entry.isDirectory) {
            val name = entry.name.replace('\\', '/').substringAfterLast('/')
            val lower = name.lowercase()
            if (name.isNotEmpty() && SUBTITLE_EXTS.any { lower.endsWith(".$it") }) {
              val ext = lower.substringAfterLast('.')
              val out = uniqueFile(outDir, name, ext)
              FileOutputStream(out).use { fos ->
                while (true) {
                  val n = sevenZ.read(buf)
                  if (n < 0) break
                  fos.write(buf, 0, n)
                }
              }
              result.add(SubtitleFile(out, name))
            }
          }
          entry = sevenZ.nextEntry
        }
      }
    }
    return result
  }
}