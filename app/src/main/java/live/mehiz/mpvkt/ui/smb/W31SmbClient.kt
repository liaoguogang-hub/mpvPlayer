package live.mehiz.mpvkt.ui.smb

import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/// W31 SMB 局域网视频播放客户端。
///
/// 基于 **jcifs-ng 2.1.9**(eu.agno3.jcifs:jcifs-ng) — Apache AgNO3 fork,
/// Java CIFS/SMB1/2/3 client 纯 Java,无 JNI。
///
/// W31.31:换 smbj 0.13 → jcifs-ng 2.1.9。smbj 0.13 sync full 设计在远程 QNAP NAS
/// 上仍报 "DiskShare has already been closed"(smbj 库内部协议层 bug)。
/// jcifs-ng 跨平台稳定,Kodi / Plex / jDownloader / 多个 Java SMB 项目都用。
///
/// 用法:
///   1. 用户配 SMB server / share / 账密 (W31SmbServerPreferences)
///   2. W31SmbBrowserScreen 列出 share 下视频文件
///   3. 用户选完 → W31SmbClient.downloadToCache() 同步全下载到 cacheDir/smb/
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

  /// W31.5/W31.6 设计:**同步全文件下载到 cache**,返回本地 File。
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
      val lastEmitRef = java.util.concurrent.atomic.AtomicLong(0L)
      SmbFileInputStream(smbFile).use { input ->
        FileOutputStream(target).use { output ->
          val buf = ByteArray(256 * 1024)
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
  }
}