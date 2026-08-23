package live.mehiz.mpvkt.ui.smb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/// W31.34: 应用级 CoroutineScope,用于 SMB phase 2 后台续传。
///
/// W31.7 的设计:BrowserScreen 关掉(用户已点文件去播 mpv)后 phase 2 仍在跑,
/// 不能用 rememberCoroutineScope()(绑 Composable lifecycle,退出就 cancel)。
/// 用应用级 SupervisorJob + Dispatchers.IO,BrowserScreen 关掉后 phase 2 继续跑到 EOF。
///
/// W31.25 教训:不要让 phase 2 通过 share 共享 phase 1 的 session/connection(W31SmbClient
/// 内部现在 phase 1/2 各自独立 open SmbFile + close SmbFile,避免 SMB 状态机 bug)。
///
/// 取消策略:phase 2 内部通过 `isActive` 检查 + 在 mpv 退出后由调用方 cancel
/// `W31SmbDownloadScope.cancel(smbRemoteUri)`(同一文件再次打开/UI 退出时调用)。
object W31SmbDownloadScope {
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  // W31.34: phase 2 job 索引(remotePath → Job),用于取消/查重。
  // BrowserScreen 退出时 cancel 所有 mp4 之外的 job;同一文件被多次点只起一个 phase 2。
  private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

  fun cancel(remotePath: String) {
    jobs.remove(remotePath)?.cancel()
  }

  fun cancelAll() {
    jobs.values.forEach { it.cancel() }
    jobs.clear()
  }

  internal fun track(remotePath: String, job: kotlinx.coroutines.Job) {
    jobs.remove(remotePath)?.cancel()  // 同一文件多次点击,先取消旧的
    jobs[remotePath] = job
    job.invokeOnCompletion { jobs.remove(remotePath, job) }
  }
}
