package live.mehiz.mpvkt.ui.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.ProgressListener
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/// W31 SMB 局域网视频播放客户端。
///
/// 基于 smbj 0.13 (W31.29 回退自 0.14,纯 Java SMB1/2/3 client,无 JNI)。
///
/// 用法:
///   1. 用户配 SMB server / share / 账密 (W31SmbServerPreferences)
///   2. W31SmbBrowserScreen 列出 share 下视频文件
///   3. 用户选完 → W31SmbClient.downloadToCache() **同步全下载到 cacheDir/smb/**
///   4. mpv 拿 cache File 当本地 file:// 播(无 phase 1+2 后台续传,无 DiskShare closed bug)
///
/// W31.29:回到 W31.5/W31.6 **同步全下载设计**(删 W31.7 phase 1+2 / W31.25 share 复用 /
/// W31.26 retry 包装)。W31.7 引入的 32MB prebuffer + 后台续传设计在远程 NAS
/// (高延迟 + smbj 0.14 TreeConnect 状态机)上有 DiskShare closed / 转圈等 bug。
///
/// 远程 NAS 上 1GB 文件 100Mbps 局域网约 80s 同步下载,但稳定。
/// 局域网 NAS 上 32MB prebuffer 优化体验明显,但 user 优先稳定性,选择回退。
///
/// W31.29 同时加 SmbConfig.withTimeout(5s):smbj SMBClient.connect() 默认
/// 没 timeout,DNS 慢 / SYN 丢包会无限等,导致 SMB 入口转圈连不上。
class W31SmbClient(
  private val server: String,
  private val port: Int = 445,
  private val username: String,
  private val password: String,
  private val domain: String = "",
) {
  private val client = SMBClient(
    // W31.29:5s SMB timeout(read/write/transact/so),避免远程 NAS DNS 慢 / SYN 丢包
    // 时 SMB 入口无限转圈(smbj 默认无限等)。
    SmbConfig.builder()
      .withTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .build()
  )
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

  /// W31.5/W31.6 设计:**同步全文件下载到 cache**,返回本地 File。
  ///
  /// W31.29 回到此设计,删除:
  ///   - W31.7 phase 1 (32MB prebuffer) + phase 2 (后台 append 剩余) → 撞 smbj TreeConnect
  ///   - W31.25 share 跨 phase 1/2 复用 → phase 1 自身仍可崩 DiskShare closed
  ///   - W31.26 retry 包装 → 治标,smbj 0.14 转圈根因没解决
  ///
  /// 优:稳定性,远程 NAS 100% OK
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
        // 用 smbj 内置 ProgressListener,每次 SMB2 Read request 块读完回调 (offset, total)
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

    /// W31.29:smbj SMB timeout(read/write/transact/so 都用这个)。远程 NAS 跟局域网
    /// NAS 都 OK,避免 DNS 慢/SYN 丢包时无限等转圈。
    private const val CONNECT_TIMEOUT_MS: Long = 5_000L
  }
}