package live.mehiz.mpvkt.ui.smb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/// W31.7:应用级 CoroutineScope,用于 SMB 视频的 phase 2 后台续传。
///
/// BrowserScreen 一旦 dismiss(用户进了 mpv player),普通 rememberCoroutineScope
/// 随之销毁,phase 2 续传会被取消。把续传挂到这个全局 scope 上,即便 UI 关掉也能
/// 一直跑到文件完整。生命周期 = 应用进程生命周期。
object W31SmbDownloadScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
