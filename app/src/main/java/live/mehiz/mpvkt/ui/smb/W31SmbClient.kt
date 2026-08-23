package live.mehiz.mpvkt.ui.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.ProgressListener
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet

/// W31 SMB 局域网视频播放客户端。
///
/// 基于 smbj 0.14 (W31.26 升级自 0.13,纯 Java SMB1/2/3 client,无 JNI)。
///
/// 用法:
///   1. 用户配 SMB server / share / 账密 (W31SmbServerPreferences)
///   2. W31SmbBrowserScreen 列出 share 下视频文件
///   3. 用户选完 → W31SmbClient.downloadForStreaming(...)
///      先下 32MB 立即调 onReadyToPlay 让 mpv 开播,
///      剩余字节挂到 W31SmbDownloadScope 后台追加到同一文件
///   4. mpv 拿 cache File 当本地 file:// 播,边播边读到新追加的数据
///
/// 限制:
///   - faststart MP4 / MKV / AVI 32MB prebuffer 后立即可播
///   - moov-at-end MP4 需要完整文件才能识别,mpv 会报格式错误,等 phase 2 完成后重试
///   - seek 到未下载区段会等到该区段下载完成(预读量 32MB ≈ 1080p 26s)
///   - 单一 server 配置
///
/// W31.25:phase 1 + phase 2 跨同一个 share + smbFile 复用,不再 close→reconnectShare。
///
/// W31.26:phase 1 自身仍可能抛 DiskShare closed / Transport EOF(网络抖动或 QNAP 端
/// idle timeout 关 share),外层加 retry:每次失败重建 share + 重新 openFile + 重新读
/// 32MB,指数 backoff 500/1000/1500ms,3 次都失败才放弃。
class W31SmbClient(
  private val server: String,
  private val port: Int = 445,
  private val username: String,
  private val password: String,
  private val domain: String = "",
) {
  private val client = SMBClient()
  private var connection: Connection? = null
  private var session: Session? = null

  data class Entry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
  )

  @Synchronized
  fun open(): Result<Unit> = runCatching {
    if (connection?.isConnected == true && session != null) return@runCatching
    val conn = client.connect(server, port)
    // 匿名共享:username/password 都空,走 smbj guest session (NTLM GUEST)。
    val auth = if (username.isBlank() && password.isBlank()) {
      AuthenticationContext.guest()
    } else {
      AuthenticationContext(username, password.toCharArray(), domain.ifBlank { null })
    }
    val sess = conn.authenticate(auth)
    connection = conn
    session = sess
  }

  fun close() {
    runCatching { session?.logoff() }
    runCatching { connection?.close() }
    session = null
    connection = null
  }

  /// 列 share 下指定 path 的条目。path 用 "/" 分隔,空字符串列 share 根目录。
  fun list(shareName: String, path: String): Result<List<Entry>> = runCatching {
    open().getOrThrow()
    val share = share(shareName)
    try {
      val items = share.list(path)
      items.mapNotNull { fi ->
        val name = fi.fileName
        if (name == "." || name == "..") return@mapNotNull null
        val isDir = (fi.fileAttributes and DIRECTORY_BIT) != 0L
        Entry(name, isDir, fi.endOfFile)
      }
    } finally {
      runCatching { share.close() }
    }
  }

  /// W31.7 边下边播 + W31.9 moov-at-end MP4 fallback + W31.25 share 跨阶段复用 + W31.26 retry:
  ///
  ///   1. phase 1: 下前 [prebufferBytes] (默认 32MB) 到目标文件。
  ///   2. 扫一下文件前 256KB 判断是否是「moov 在末尾」的 MP4 — 这种格式 mpv
  ///      需要扫到文件尾部才能找 moov atom,如果边下边播会让 mpv 扫到正在被
  ///      追加的尾部而解不出来,黑屏。
  ///   3a. faststart MP4 / MKV / AVI / 其它 → 立刻回调 [onReadyToPlay],把剩
  ///       余字节挂到 [W31SmbDownloadScope] 后台 fire-and-forget append。
  ///   3b. moov-at-end MP4 → 同步下完整个文件,再回调 [onReadyToPlay]。
  ///
  /// 调用方不管哪种情形都只需要在 [onReadyToPlay] 里启动 mpv + 关 SMB UI。
  ///
  /// W31.25:phase 1 + phase 2 跨**同一个 share + smbFile** 完成,不在 phase 1 结束
  /// 时 close。
  ///
  /// W31.26:phase 1 失败如果异常是 SMBRuntimeException(DiskShare closed 等)/ Transport
  /// EOF,自动重建 share + 重新 openFile + 重新读 32MB,指数 backoff。3 次都失败才抛。
  suspend fun downloadForStreaming(
    shareName: String,
    remotePath: String,
    cacheRootDir: File,
    prebufferBytes: Long = DEFAULT_PREBUFFER_BYTES,
    onReadyToPlay: (target: File) -> Unit,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      open().getOrThrow()
      var attempt = 0
      var lastError: Throwable? = null
      while (attempt < MAX_RECONNECT_ATTEMPTS) {
        attempt++
        // W31.26:每次 retry 都重新 connectShare(可能 connectShare 也被 server 关了)
        val share = share(shareName)
        val smbFileHolder = arrayOfNulls<com.hierynomus.smbj.share.File>(1)
        val closeGuard = java.util.concurrent.atomic.AtomicBoolean(false)
        fun closeAllOnce() {
          if (closeGuard.compareAndSet(false, true)) {
            runCatching { smbFileHolder[0]?.close() }
            runCatching { share.close() }
          }
        }
        try {
          val smbFile = share.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2_SHARE_ACCESS_READ,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(SMB2CreateOptions::class.java),
          )
          smbFileHolder[0] = smbFile
          val total = share.getFileInformation(remotePath).standardInformation.endOfFile
          val target = cacheFile(cacheRootDir, shareName, remotePath)
          target.parentFile?.mkdirs()
          val buf = ByteArray(READ_CHUNK_SIZE)
          val lastEmitRef = java.util.concurrent.atomic.AtomicLong(0L)
          val prebufferActual = minOf(prebufferBytes, total)
          val prebuffered = readRangeInto(smbFile, buf, target, append = false, 0L, prebufferActual, total, lastEmitRef, onProgress)
          // phase 1 成功,根据文件大小 / moov 位置分 3 路
          if (prebuffered >= total) {
            onProgress?.invoke(total, total)
            closeAllOnce()
            onReadyToPlay(target)
            return@runCatching target
          } else if (isMoovAtEndMp4(target)) {
            readRangeInto(smbFile, buf, target, append = true, prebuffered, total - prebuffered, total, lastEmitRef, onProgress)
            onProgress?.invoke(total, total)
            closeAllOnce()
            onReadyToPlay(target)
            return@runCatching target
          } else {
            // faststart:phase 2 挂后台,share + smbFile 接管给 W31SmbDownloadScope
            W31SmbDownloadScope.launch {
              try {
                readRangeInto(smbFile, buf, target, append = true, prebuffered, total - prebuffered, total, lastEmitRef, onProgress)
                onProgress?.invoke(total, total)
              } catch (_: Throwable) {
                // 后台续传失败是 best-effort
              } finally {
                closeAllOnce()
              }
            }
            onReadyToPlay(target)
            return@runCatching target
          }
        } catch (e: SMBRuntimeException) {
          closeAllOnce()
          lastError = e
          if (attempt < MAX_RECONNECT_ATTEMPTS && isRetryableSmbError(e)) {
            delay(RETRY_DELAY_MS * attempt)
            continue
          }
          throw e
        } catch (e: com.hierynomus.protocol.transport.TransportException) {
          closeAllOnce()
          lastError = e
          if (attempt < MAX_RECONNECT_ATTEMPTS) {
            delay(RETRY_DELAY_MS * attempt)
            continue
          }
          throw e
        } catch (t: Throwable) {
          closeAllOnce()
          throw t
        }
      }
      throw lastError ?: IllegalStateException("W31SmbClient: retry exhausted without lastError")
    }
  }

  /// W31.26:判断 SMB 异常是否可重试(share / pipe closed / transport 异常)。
  /// 网络抖动 / QNAP 端 idle timeout 关 share 是典型场景。
  private fun isRetryableSmbError(e: Throwable): Boolean {
    val msg = e.message ?: ""
    return msg.contains("has already been closed", ignoreCase = true) ||
      msg.contains("transport", ignoreCase = true) ||
      msg.contains("EOF", ignoreCase = true) ||
      msg.contains("connection reset", ignoreCase = true)
  }

  /// 扫文件前 256KB,判断是否是「moov 在文件末尾」的 MP4。
  ///
  /// MP4 = 一系列 box,每个 box 头 4 字节 size + 4 字节 tag。
  /// - 字节 4-7 = "ftyp" 标识是 MP4。
  /// - 顶层 box 里有 "moov" tag → faststart(moov 在文件头)。
  /// - 256KB 内走完所有顶层 box 都没 "moov" → moov 在文件末尾。
  ///
  /// 注意这只扫前 256KB,理论上罕见的「moov 恰好在 256KB ~ 几 MB 之间」会误判
  /// 为 moov-at-end,代价只是多等几秒全量下载,可接受。
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

  /// 内部 helper:把 [fileOffset, fileOffset+length) 字节从 SMB 读到 [target]。
  /// [append] = true 时追加到现有文件,false 时截断重写。返回实际写入字节数。
  ///
  /// W31.25:这个 helper 同时被 phase 1 + phase 2 调用,跨 coroutine 复用同一 smbFile。
  /// phase 1 同步完成 → phase 2 launch 后串行 read 同一 smbFile 不同 [fileOffset] 区间,
  /// smbj 内部 seek+read 在同一 socket 上是安全的(顺序 IO)。
  private fun readRangeInto(
    smbFile: com.hierynomus.smbj.share.File,
    buf: ByteArray,
    target: File,
    append: Boolean,
    fileOffset: Long,
    length: Long,
    total: Long,
    lastEmitRef: java.util.concurrent.atomic.AtomicLong,
    onProgress: ((Long, Long) -> Unit)?,
  ): Long {
    var written = 0L
    val end = fileOffset + length
    FileOutputStream(target, append).use { fos ->
      var offset = fileOffset
      while (offset < end) {
        val want = minOf(buf.size.toLong(), end - offset).toInt()
        val read = smbFile.read(buf, offset, 0, want)
        if (read <= 0) break
        fos.write(buf, 0, read)
        offset += read
        written += read
        emitProgressThrottled(written, total, lastEmitRef, onProgress)
      }
      fos.flush()
    }
    return written
  }

  private fun emitProgressThrottled(
    downloaded: Long,
    total: Long,
    lastEmitRef: java.util.concurrent.atomic.AtomicLong,
    onProgress: ((Long, Long) -> Unit)?,
  ) {
    if (onProgress == null) return
    val now = System.currentTimeMillis()
    val prev = lastEmitRef.get()
    if (now - prev > 100) {
      lastEmitRef.set(now)
      onProgress(downloaded, total)
    }
  }

  private fun share(name: String): DiskShare {
    val sess = session ?: error("session not open")
    return sess.connectShare(name) as? DiskShare
      ?: error("share '$name' 不是磁盘共享(打印机/管道不支持)")
  }

  private fun cacheFile(cacheRootDir: File, shareName: String, remotePath: String): File {
    val safeShare = shareName.replace('/', '_').replace('\\', '_')
    val safePath = remotePath.replace('\\', '/')
      .split('/').filter { it.isNotBlank() }
      .joinToString("/") { it.replace(Regex("""\p{Cntrl}"""), "") }
    return File(cacheRootDir, "smb/$server/$safeShare/$safePath")
  }

  companion object {
    /// SMB FILE_ATTRIBUTE_DIRECTORY 位 (0x10),smbj 用 long 存 fileAttributes
    private const val DIRECTORY_BIT: Long = 0x10L

    private val SMB2_SHARE_ACCESS_READ = EnumSet.of(
      SMB2ShareAccess.FILE_SHARE_READ,
      SMB2ShareAccess.FILE_SHARE_WRITE,
      SMB2ShareAccess.FILE_SHARE_DELETE,
    )

    /// 边下边播的 prebuffer 字节数。32MB 够 1080p H264 (~26s) / 4K HEVC (~6s) 启动缓冲。
    private const val DEFAULT_PREBUFFER_BYTES: Long = 32L * 1024 * 1024

    /// SMB 单次读块大小。256KB 是 smbj 文档建议值,在千兆局域网下能打满带宽。
    private const val READ_CHUNK_SIZE: Int = 256 * 1024

    /// W31.26:phase 1 retry 最大次数(每次都重建 share + openFile + 重读)。
    /// 远程 NAS 网络抖动频繁,3 次够覆盖 99% 场景,再多用户体验不到反而拖慢首开。
    private const val MAX_RECONNECT_ATTEMPTS: Int = 3

    /// W31.26:retry 基础延迟(指数 backoff:500 / 1000 / 1500 ms)。
    private const val RETRY_DELAY_MS: Long = 500L
  }
}