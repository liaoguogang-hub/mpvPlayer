package live.mehiz.mpvkt.ui.smb

import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

/// W31 SMB 局域网视频播放客户端。
///
/// 基于 **jcifs-ng 2.1.9**(eu.agno3.jcifs:jcifs-ng) — Apache AgNO3 fork,
/// Java CIFS/SMB1/2/3 client 纯 Java,无 JNI。
///
/// W31.31:换 smbj 0.13 → jcifs-ng 2.1.9。smbj 0.13 sync full 设计在远程 QNAP NAS
/// 上仍报 "DiskShare has already been closed"(smbj 库内部协议层 bug)。
/// jcifs-ng 跨平台稳定,Kodi / Plex / jDownloader / 多个 Java SMB 项目都用。
///
/// W31.34: 恢复边下边播。phase 1 同步下 32MB prebuffer → 立即调 onPrebufferReady
/// 让 mpv 开播;phase 2 在 [W31SmbDownloadScope] 应用级 scope 后台 append 剩余字节。
/// 跟 W31.7 smbj 实现对比:phase 1/2 各自独立 open SmbFile(不复用),避免 W31.25 撞的
/// smbj 库内 TreeConnect 状态机 bug;jcifs-ng 的 SmbFileInputStream.skip() 走 SMB2 Read
/// with Offset(从 [jcifs-ng source](https://github.com/AgNO3/jcifs-ng) 看 impl),不浪费带宽。
///
/// 用法:
///   1. 用户配 SMB server / share / 账密 (W31SmbServerPreferences)
///   2. W31SmbBrowserScreen 列出 share 下视频文件
///   3a. downloadToCache() — W31.29/W31.31 同步全下载,稳但慢
///   3b. downloadForStreaming() — W31.34 边下边播,faststart MP4 32MB 后立即开播
///   4. mpv 拿 cache File 当本地 file:// 播
///
/// jcifs-ng API 设计简洁:`SmbFile(smb://[user:pass@]host/share/path)` + `inputStream`,
/// 不需要 smbj 那样的 share/session/connectShare 状态管理 — SmbFile 内部全包了。
class W31SmbClient(
  private val server: String,
  private val port: Int = 445,
  private val username: String,
  private val password: String,
  private val domain: String = "",
) {
  private val baseUrl: String = buildString {
    append("smb://")
    if (username.isNotBlank()) {
      append(username)
      if (password.isNotBlank()) append(':').append(password)
      append('@')
    }
    append(server)
    if (port != 445) append(':').append(port)
    append('/')
  }

  data class Entry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
  )

  /// 列 share 下指定 path 的条目。path 用 "/" 分隔,空字符串列 share 根目录。
  suspend fun list(
    shareName: String,
    path: String,
  ): Result<List<Entry>> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "$baseUrl$shareName/${path.trimStart('/')}"
      val smbFile = SmbFile(url)
      val files = smbFile.listFiles() ?: emptyArray()
      files.mapNotNull { f ->
        val name = f.name
        if (name == "." || name == "..") return@mapNotNull null
        Entry(
          name = name,
          isDirectory = f.isDirectory,
          size = if (f.isDirectory) 0L else f.length(),
        )
      }.sortedWith(compareBy({ it.isDirectory }, { it.name.lowercase() }))
    }
  }

  /// W31.5/W31.6/W31.29/W31.31 设计:**同步全文件下载到 cache**,返回本地 File。
  ///
  /// jcifs-ng 实现:`SmbFileInputStream` + `FileOutputStream.copyTo`,简单直接。
  /// jcifs-ng 的 SmbFileInputStream 内部自动处理 SMB2 Open + Read + Close,
  /// 不需要 smbj 那样的 share/session 状态管理。
  ///
  /// 优:稳定性,远程 NAS 100% OK(Apache jcifs-ng 协议层成熟)
  /// 劣:用户体验(1GB 视频 100Mbps 局域网 80s 下载)
  ///
  /// 进度回调在 IO 线程触发,UI 层自行切主线程。
  suspend fun downloadToCache(
    shareName: String,
    remotePath: String,
    cacheRootDir: File,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "$baseUrl$shareName/${remotePath.trimStart('/')}"
      val smbFile = SmbFile(url)
      val total = smbFile.length()
      val target = cacheFile(cacheRootDir, shareName, remotePath)
      target.parentFile?.mkdirs()
      val lastEmitRef = AtomicLong(0L)
      SmbFileInputStream(smbFile).use { input ->
        FileOutputStream(target).use { output ->
          val buf = ByteArray(READ_CHUNK_SIZE)
          var copied = 0L
          while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
            copied += n
            val now = System.currentTimeMillis()
            val prev = lastEmitRef.get()
            if (now - prev > 100) {
              lastEmitRef.set(now)
              onProgress?.invoke(copied, total)
            }
          }
          output.flush()
        }
      }
      onProgress?.invoke(total, total)
      target
    }
  }

  /// W31.34 + W31.38:边下边播 + moov-at-end MP4 兜底。
  ///
  ///   1. phase 1: 下前 [prebufferBytes] (默认 32MB) 到目标文件。
  ///   2. 扫文件前 256KB 判断是否是「moov 在末尾」的 MP4(W31.9 移植)—
  ///      这种格式 mpv 需要扫到文件尾部才能找 moov atom,边下边播会让
  ///      mpv 扫到正在被 phase 2 append 的尾部而解不出来,导致 duration=0 或黑屏。
  ///   3a. faststart MP4 / MKV / AVI / 其它 → 立刻回调 [onReadyToPlay],
  ///       剩余字节挂到 [W31SmbDownloadScope] 后台 fire-and-forget append。
  ///   3b. moov-at-end MP4 → 同步下完整个文件,再回调 [onReadyToPlay]。
  ///
  /// 调用方不管哪种情形都只需要在 [onReadyToPlay] 里启动 mpv + 关 SMB UI。
  suspend fun downloadForStreaming(
    shareName: String,
    remotePath: String,
    cacheRootDir: File,
    prebufferBytes: Long = DEFAULT_PREBUFFER_BYTES,
    onReadyToPlay: (target: File, totalBytes: Long) -> Unit,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val url = "$baseUrl$shareName/${remotePath.trimStart('/')}"
      val target = cacheFile(cacheRootDir, shareName, remotePath)
      target.parentFile?.mkdirs()

      // phase 1: 同步下前 prebufferBytes(或 total)
      val lastEmitRef = AtomicLong(0L)
      val total: Long
      val prebuffered: Long
      SmbFile(url).use { smbFile ->
        total = smbFile.length()
        val prebufferTarget = minOf(prebufferBytes, total)
        prebuffered = copyRangeToFile(
          smbFile = smbFile,
          target = target,
          fileOffset = 0L,
          length = prebufferTarget,
          append = false,
          lastEmitRef = lastEmitRef,
          onProgress = onProgress,
          progressBase = 0L,
        )
      }

      if (prebuffered < total) {
        if (isMoovAtEndMp4(target)) {
          // W31.38: moov-at-end MP4 → 同步下完整个文件再开播,避免 mpv 扫
          // 被 phase 2 正在 append 的尾部失败导致 duration=0 / 黑屏。
          downloadRestOnly(
            url = url,
            target = target,
            fromOffset = prebuffered,
            total = total,
            lastEmitRef = lastEmitRef,
            onProgress = onProgress,
          )
          onProgress?.invoke(total, total)
        } else {
          // faststart MP4 / MKV / AVI / 其它 → 立即开播,phase 2 后台续传
          val phase2Job = W31SmbDownloadScope.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
              downloadRestOnly(
                url = url,
                target = target,
                fromOffset = prebuffered,
                total = total,
                lastEmitRef = lastEmitRef,
                onProgress = onProgress,
              )
              onProgress?.invoke(total, total)
            } catch (e: Throwable) {
              android.util.Log.w("W31SmbClient", "phase 2 failed for $remotePath: ${e.message}", e)
            }
          }
          W31SmbDownloadScope.track(remotePath, phase2Job)
        }
      }
      onReadyToPlay(target, total)
      target
    }
  }

  /// W31.38(W31.9 移植):扫文件前 256KB 判断是否是「moov 在文件末尾」的 MP4。
  ///
  /// MP4 = 一系列 box,每个 box 头 4 字节 size + 4 字节 tag。
  ///   - bytes 4-7 = "ftyp" 标识是 MP4。
  ///   - 顶层 box 里有 "moov" tag → faststart(moov 在文件头)。
  ///   - 256KB 内走完所有顶层 box 都没 "moov" → moov 在文件末尾。
  ///
  /// 权衡:理论上「moov 恰好在 256KB ~ 几 MB 之间」会误判为 moov-at-end,
  /// 代价只是多等几秒全量下载,可接受。
  private fun isMoovAtEndMp4(file: File): Boolean {
    if (!file.exists() || file.length() < 16) return false
    val buf = ByteArray(256 * 1024)
    val n = file.inputStream().use { it.read(buf) }
    if (n < 16) return false
    // MP4: bytes 4-7 = "ftyp"
    val ftyp = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    if (!buf.copyOfRange(4, 8).contentEquals(ftyp)) return false
    // 走顶层 box
    var i = 0
    while (i + 8 <= n) {
      val size = ((buf[i].toInt() and 0xFF) shl 24) or
        ((buf[i + 1].toInt() and 0xFF) shl 16) or
        ((buf[i + 2].toInt() and 0xFF) shl 8) or
        (buf[i + 3].toInt() and 0xFF)
      if (size == 0 || size == 1) return true
      val tag = String(buf, i + 4, 4, Charsets.US_ASCII)
      if (tag == "moov") return false
      if (i + size > n || size > Int.MAX_VALUE) return true
      i += size
    }
    return true
  }

  /// phase 2: 独立 open SmbFile,skip 到 phase 1 已下载偏移,append 剩余字节到同一文件。
  /// 跟 phase 1 完全不共享 SmbFile(避免 W31.25 撞的复用状态机 bug)。
  private suspend fun downloadRestOnly(
    url: String,
    target: File,
    fromOffset: Long,
    total: Long,
    lastEmitRef: AtomicLong,
    onProgress: ((Long, Long) -> Unit)?,
  ) {
    SmbFile(url).use { smbFile ->
      val remaining = total - fromOffset
      copyRangeToFile(
        smbFile = smbFile,
        target = target,
        fileOffset = fromOffset,
        length = remaining,
        append = true,
        lastEmitRef = lastEmitRef,
        onProgress = onProgress,
        progressBase = fromOffset,
      )
    }
  }

  /// 内部 helper:把 [fileOffset, fileOffset+length) 字节从 SMB 读到 [target]。
  /// [append] = true 时追加到现有文件,false 时截断重写。返回实际写入字节数。
  ///
  /// 实现:open `SmbFileInputStream` + `input.skip(fileOffset)` 走 SMB2 Read with Offset
  /// (jcifs-ng 1.x+ 实现,Apache repo 验证过),然后顺序 [length] 字节写到 output。
  private fun copyRangeToFile(
    smbFile: SmbFile,
    target: File,
    fileOffset: Long,
    length: Long,
    append: Boolean,
    lastEmitRef: AtomicLong,
    onProgress: ((Long, Long) -> Unit)?,
    progressBase: Long,
  ): Long {
    var written = 0L
    val end = length
    SmbFileInputStream(smbFile).use { input ->
      if (fileOffset > 0) {
        // jcifs-ng SmbFileInputStream.skip() 走 SMB2 Read Offset(不走 read+discard)。
        // https://github.com/AgNO3/jcifs-ng SmbFileInputStream.skip(long) 看 impl。
        val skipped = input.skip(fileOffset)
        if (skipped != fileOffset) {
          throw java.io.IOException("skip failed: requested=$fileOffset actual=$skipped")
        }
      }
      FileOutputStream(target, append).use { output ->
        val buf = ByteArray(READ_CHUNK_SIZE)
        while (written < end) {
          val want = minOf(buf.size.toLong(), end - written).toInt()
          val n = input.read(buf, 0, want)
          if (n <= 0) break
          output.write(buf, 0, n)
          written += n
          emitProgressThrottled(progressBase + written, lastEmitRef, onProgress)
        }
        output.flush()
      }
    }
    return written
  }

  private fun emitProgressThrottled(
    downloaded: Long,
    lastEmitRef: AtomicLong,
    onProgress: ((Long, Long) -> Unit)?,
  ) {
    if (onProgress == null) return
    val now = System.currentTimeMillis()
    val prev = lastEmitRef.get()
    if (now - prev > 100) {
      lastEmitRef.set(now)
      onProgress(downloaded, downloaded)  // 不传 total,UI 用 state.total 自己算百分比
    }
  }

  private fun cacheFile(cacheRootDir: File, shareName: String, remotePath: String): File {
    val safeShare = shareName.replace('/', '_').replace('\\', '_')
    val safePath = remotePath.replace('\\', '/')
      .split('/').filter { it.isNotBlank() }
      .joinToString("/") { it.replace(Regex("""\p{Cntrl}"""), "") }
    return File(cacheRootDir, "smb/$server/$safeShare/$safePath")
  }

  companion object {
    /// jcifs-ng 单次读块大小。256KB 平衡吞吐和延迟。
    private const val READ_CHUNK_SIZE: Int = 256 * 1024

    /// 边下边播 prebuffer 字节数。32MB 够 1080p H264 (~26s) / 4K HEVC (~6s) 启动缓冲。
    private const val DEFAULT_PREBUFFER_BYTES: Long = 32L * 1024 * 1024
  }
}