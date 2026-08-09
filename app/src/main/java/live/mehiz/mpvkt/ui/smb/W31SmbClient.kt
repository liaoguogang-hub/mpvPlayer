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
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet

/// W31 SMB 局域网视频播放客户端。
///
/// 基于 smbj 0.13 (纯 Java SMB1/2/3 client,无 JNI)。
///
/// 用法:
///   1. 用户配 SMB server / share / 账密 (W31SmbServerPreferences)
///   2. W31SmbBrowserScreen 列出 share 下视频文件
///   3. 用户选完 → W31SmbClient.downloadForStreaming(...)
///      先下 32MB 立即调 onPrebufferReady 让 mpv 开播,
///      剩余字节挂到 W31SmbDownloadScope 后台追加到同一文件
///   4. mpv 拿 cache File 当本地 file:// 播,边播边读到新追加的数据
///
/// 限制:
///   - faststart MP4 / MKV / AVI 32MB prebuffer 后立即可播
///   - moov-at-end MP4 需要完整文件才能识别,mpv 会报格式错误,等 phase 2 完成后重试
///   - seek 到未下载区段会等到该区段下载完成(预读量 32MB ≈ 1080p 26s)
///   - 单一 server 配置
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

  /// 把 share 内文件下载到 cacheRootDir/smb/<server>/<share>/<relPath>,返回本地 File。
  /// 进度回调在 IO 线程触发,UI 层自行切主线程。
  suspend fun downloadToCache(
    shareName: String,
    remotePath: String,
    cacheRootDir: File,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      open().getOrThrow()
      val share = share(shareName)
      try {
        val smbFile = share.openFile(
          remotePath,
          EnumSet.of(AccessMask.GENERIC_READ),
          EnumSet.noneOf(FileAttributes::class.java),
          SMB2_SHARE_ACCESS_READ,
          SMB2CreateDisposition.FILE_OPEN,
          EnumSet.noneOf(SMB2CreateOptions::class.java),
        )
        val total = share.getFileInformation(remotePath).standardInformation.endOfFile
        val target = cacheFile(cacheRootDir, shareName, remotePath)
        target.parentFile?.mkdirs()
        // W31.5:用 ProgressListener 拿到真实下载字节数,UI 显示进度条。
        // smbj 内部按 SMB2 Read request 块读,每次读完一段回调 (offset, total)。
        val lastEmitRef = java.util.concurrent.atomic.AtomicLong(0L)
        FileOutputStream(target).use { fos ->
          smbFile.read(fos, ProgressListener { offset, _ ->
            val now = System.currentTimeMillis()
            val prev = lastEmitRef.get()
            if (now - prev > 100) {
              lastEmitRef.set(now)
              onProgress?.invoke(offset, total)
            }
          })
        }
        onProgress?.invoke(total, total)
        runCatching { smbFile.close() }
        target
      } finally {
        runCatching { share.close() }
      }
    }
  }

  /// W31.7:边下边播。先把前 [prebufferBytes] (默认 32MB) 写到目标文件,
  /// 立即回调 [onPrebufferReady] 让 UI 启动 mpv;然后 phase 2 续传剩余字节到
  /// 同一文件 — 在 [W31SmbDownloadScope] 应用级 scope 里 fire-and-forget 跑,
  /// 即便 BrowserScreen 已 dismiss 也会继续,mpv 边播边读到新追加的数据。
  ///
  /// 限制:
  ///   - 对 faststart MP4 / MKV / AVI 立即可播 (header 在文件头)
  ///   - 对 moov-at-end MP4 需要完整文件才能播,prebufferReady 后 mpv 报错,
  ///     等 phase 2 完成重试
  ///   - 调用方在 phase 1 完成后应立刻 [onPlayFile] + 关 SMB UI,phase 2 在后台跑
  suspend fun downloadForStreaming(
    shareName: String,
    remotePath: String,
    cacheRootDir: File,
    prebufferBytes: Long = DEFAULT_PREBUFFER_BYTES,
    onPrebufferReady: (target: File, prebuffered: Long, total: Long) -> Unit,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      // phase 1: 开 share + 下前 N MB + 关 share。返回 (file, total, prebuffered)。
      val (file, total, prebuffered) = downloadPrebufferOnly(shareName, remotePath, cacheRootDir, prebufferBytes, onProgress).getOrThrow()
      onPrebufferReady(file, prebuffered, total)

      // phase 2: 重新开 share + append 剩余字节到同一文件。挂到应用级 scope。
      if (prebuffered < total) {
        W31SmbDownloadScope.launch {
          try {
            downloadRestOnly(shareName, remotePath, file, prebuffered, total, onProgress)
            onProgress?.invoke(total, total)
          } catch (_: Throwable) {
            // 后台续传失败是 best-effort,UI 已 dismiss 不必打扰用户
          }
        }
      }
      file
    }
  }

  /// 下载文件前 [prebufferBytes] 字节到 cache。返回 (target, totalSize, actualDownloaded)。
  private suspend fun downloadPrebufferOnly(
    shareName: String,
    remotePath: String,
    cacheRootDir: File,
    prebufferBytes: Long,
    onProgress: ((Long, Long) -> Unit)?,
  ): Result<Triple<File, Long, Long>> = withContext(Dispatchers.IO) {
    runCatching {
      open().getOrThrow()
      val share = share(shareName)
      try {
        val smbFile = share.openFile(
          remotePath,
          EnumSet.of(AccessMask.GENERIC_READ),
          EnumSet.noneOf(FileAttributes::class.java),
          SMB2_SHARE_ACCESS_READ,
          SMB2CreateDisposition.FILE_OPEN,
          EnumSet.noneOf(SMB2CreateOptions::class.java),
        )
        try {
          val total = share.getFileInformation(remotePath).standardInformation.endOfFile
          val target = cacheFile(cacheRootDir, shareName, remotePath)
          target.parentFile?.mkdirs()
          val buf = ByteArray(READ_CHUNK_SIZE)
          val lastEmitRef = java.util.concurrent.atomic.AtomicLong(0L)
          val prebufferActual = minOf(prebufferBytes, total)
          val downloaded = readRangeInto(smbFile, buf, target, append = false, 0L, prebufferActual, total, lastEmitRef, onProgress)
          Triple(target, total, downloaded)
        } finally {
          runCatching { smbFile.close() }
        }
      } finally {
        runCatching { share.close() }
      }
    }
  }

  /// phase 2:从 [fromOffset] 起 append [total - fromOffset] 字节到 [target]。
  /// 独立开 share(phase 1 已关),挂到 [W31SmbDownloadScope] 后台跑。
  private suspend fun downloadRestOnly(
    shareName: String,
    remotePath: String,
    target: File,
    fromOffset: Long,
    total: Long,
    onProgress: ((Long, Long) -> Unit)?,
  ) {
    open().getOrThrow()
    val share = share(shareName)
    try {
      val smbFile = share.openFile(
        remotePath,
        EnumSet.of(AccessMask.GENERIC_READ),
        EnumSet.noneOf(FileAttributes::class.java),
        SMB2_SHARE_ACCESS_READ,
        SMB2CreateDisposition.FILE_OPEN,
        EnumSet.noneOf(SMB2CreateOptions::class.java),
      )
      try {
        val buf = ByteArray(READ_CHUNK_SIZE)
        val lastEmitRef = java.util.concurrent.atomic.AtomicLong(0L)
        readRangeInto(smbFile, buf, target, append = true, fromOffset, total - fromOffset, total, lastEmitRef, onProgress)
      } finally {
        runCatching { smbFile.close() }
      }
    } finally {
      runCatching { share.close() }
    }
  }

  /// 内部 helper:把 [fileOffset, fileOffset+length) 字节从 SMB 读到 [target]。
  /// [append] = true 时追加到现有文件,false 时截断重写。返回实际写入字节数。
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
  }
}