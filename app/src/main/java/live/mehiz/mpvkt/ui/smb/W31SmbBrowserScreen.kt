package live.mehiz.mpvkt.ui.smb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/// W31 SMB 浏览器:列出 share 内的目录/视频文件,点击后用 [onPlayFile] 播本地 cache。
///
/// W31.34 + W31.38:边下边播 + moov-at-end 兜底
///   - 点击文件 → W31SmbClient.downloadForStreaming
///   - phase 1 同步下前 32MB prebuffer → 扫文件前 256KB 找 moov tag
///     - faststart MP4 / MKV / AVI → 立即调 onReadyToPlay 启动 mpv,phase 2 后台 append
///     - moov-at-end MP4 → 同步下完整个文件再调 onReadyToPlay
///   - mpv 边播边读到 phase 2 追加的数据(W31.9 移植,W31.38 适配 jcifs-ng + W31.34)
///   - 目录浏览只一层(列表点进去再次进入子目录)
@Composable
fun W31SmbBrowserScreen(
  onDismiss: () -> Unit,
  onPlayFile: (File) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember { W31SmbServerPreferences(context) }

  var showConfig by remember { mutableStateOf(!prefs.isConfigured) }
  var serverMenu by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }
  var renameText by remember { mutableStateOf("") }
  var names by remember { mutableStateOf(prefs.profileNames()) }
  fun refreshNames() { names = prefs.profileNames() }
  var path by remember { mutableStateOf("") }
  var entries by remember { mutableStateOf<List<W31SmbClient.Entry>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var downloading by remember { mutableStateOf<String?>(null) }
  var downloadProgress by remember { mutableStateOf(0f) }
  var downloadBytes by remember { mutableStateOf(0L) }
  var downloadTotal by remember { mutableStateOf(0L) }

  var client by remember { mutableStateOf<W31SmbClient?>(null) }

  fun loadCurrent() {
    if (!prefs.isConfigured) {
      showConfig = true
      return
    }
    val c = prefs.toClient()
    client = c
    loading = true
    error = null
    scope.launch {
      val r = withContext(Dispatchers.IO) { c.list(prefs.share, path) }
      loading = false
      r.onSuccess { entries = it }
        .onFailure { error = it.message ?: it.javaClass.simpleName }
    }
  }

  LaunchedEffect(path) {
    if (prefs.isConfigured) loadCurrent()
  }

  LaunchedEffect(Unit) {
    if (prefs.isConfigured) loadCurrent()
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color(0xFF101010),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (path.isNotEmpty()) {
          IconButton(onClick = {
            path = path.substringBeforeLast('/', "")
          }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
          }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
          Text(
            text = if (!prefs.isConfigured) "未配置" else (if (prefs.name.isNotBlank()) prefs.name else prefs.server + "/" + prefs.share),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
          )
          if (path.isNotEmpty()) {
            Text(text = "/$path", color = Color(0xFFAAAAAA), fontSize = 12.sp)
          }
        }
        Box {
          IconButton(onClick = { names = prefs.profileNames(); serverMenu = true }) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "切换服务器", tint = Color.White)
          }
          DropdownMenu(expanded = serverMenu, onDismissRequest = { serverMenu = false }) {
            names.forEachIndexed { idx, nm ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nm, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      text = "✎",
                      color = Color(0xFF80C8FF),
                      modifier = Modifier.clickable {
                        prefs.selectIndex(idx)
                        refreshNames()
                        renameText = prefs.name
                        serverMenu = false
                        showRename = true
                      }.padding(8.dp),
                    )
                  }
                },
                onClick = {
                  prefs.selectIndex(idx)
                  refreshNames()
                  path = ""
                  client = null
                  serverMenu = false
                  loadCurrent()
                },
              )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("✎ 重命名当前配置") }, onClick = {
              renameText = prefs.name
              serverMenu = false
              showRename = true
            })
            DropdownMenuItem(text = { Text("＋ 新建配置") }, onClick = {
              prefs.newProfile()
              refreshNames()
              serverMenu = false
              showConfig = true
            })
          }
        }
        IconButton(onClick = { showConfig = true }) {
          Icon(Icons.Default.Settings, contentDescription = "配置", tint = Color.White)
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
        }
      }

      if (downloading != null) {
        LinearProgressIndicator(
          progress = { downloadProgress.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          color = Color(0xFF80C8FF),
        )
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
          Text(
            text = downloading!!,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
          )
          if (downloadTotal > 0) {
            Text(
              text = "${formatSize(downloadBytes)} / ${formatSize(downloadTotal)}  (${(downloadProgress * 100).toInt()}%)",
              color = Color(0xFFAAAAAA),
              fontSize = 11.sp,
              modifier = Modifier.padding(top = 2.dp),
            )
          }
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        when {
          loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = Color(0xFF80C8FF))
            }
          }
          error != null -> {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
              Text("连接失败", color = Color(0xFFFF8080), fontSize = 16.sp)
              Text(error!!, color = Color(0xFFCCCCCC), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
              TextButton(onClick = { loadCurrent() }, modifier = Modifier.padding(top = 8.dp)) {
                Text("重试")
              }
            }
          }
          entries.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("空目录", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            }
          }
          else -> {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
              items(entries) { entry ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .clickable {
                      if (entry.isDirectory) {
                        path = if (path.isEmpty()) entry.name else "$path/${entry.name}"
                      } else {
                        val fullPath = if (path.isEmpty()) entry.name else "$path/${entry.name}"
                        downloading = fullPath
                        downloadProgress = 0f
                        scope.launch {
                          downloadBytes = 0L
                          downloadTotal = 0L
                          // W31.34 + W31.38:边下边播 + moov-at-end 兜底。
                          //   - faststart MP4 / MKV / AVI:phase 1 (32MB) 后立即开播,
                          //     phase 2 在 W31SmbDownloadScope 后台 append。
                          //   - moov-at-end MP4(常见手机拍视频):同步下完整个文件再开播,
                          //     避免 mpv 扫被 phase 2 正在 append 的尾部失败导致 duration=0 / 黑屏。
                          // jcifs-ng 实现:phase 1/2 各自独立 open SmbFile,不复用,避免 W31.25
                          // 撞的 smbj share 复用 TreeConnect 状态机 bug。
                          var readyFired = false
                          val r = client!!.downloadForStreaming(
                            shareName = prefs.share,
                            remotePath = fullPath,
                            cacheRootDir = context.cacheDir,
                            onReadyToPlay = { file, total ->
                              downloadBytes = total
                              downloadTotal = total
                              downloadProgress = 1f
                              // onReadyToPlay 在 IO 线程触发,startActivity 必须主线程
                              readyFired = true
                              scope.launch(Dispatchers.Main) {
                                onPlayFile(file)
                                onDismiss()
                              }
                            },
                            onProgress = { read, total ->
                              downloadBytes = read
                              downloadTotal = total
                              if (total > 0) downloadProgress = read.toFloat() / total
                            },
                          )
                          r.onFailure { e ->
                            downloading = null
                            if (!readyFired) {
                              error = "下载失败: ${e.message ?: e.javaClass.simpleName}"
                            }
                          }
                        }
                      }
                    }
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (entry.isDirectory) Color(0xFF80C8FF) else Color(0xFFCCCCCC),
                  )
                  Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                      text = entry.name,
                      color = Color.White,
                      fontSize = 14.sp,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (!entry.isDirectory && entry.size > 0) {
                      Text(
                        text = formatSize(entry.size),
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showConfig) {
    W31SmbServerDialog(
      initial = prefs,
      onDismiss = {
        prefs.discardBlankActive()
        refreshNames()
        showConfig = false
      },
      onConfirm = {
        prefs.saveActiveProfile()
        refreshNames()
        android.util.Log.i("SMB", "after-save names=" + names)
        path = ""
        showConfig = false
        loadCurrent()
      },
    )
  }

  if (showRename) {
    AlertDialog(
      onDismissRequest = { showRename = false },
      title = { Text("重命名配置") },
      text = {
        OutlinedTextField(
          value = renameText,
          onValueChange = { renameText = it },
          singleLine = true,
          label = { Text("配置名称") },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(onClick = {
          val nn = renameText.trim()
          if (nn.isNotBlank()) {
            prefs.name = nn
            refreshNames()
            android.util.Log.i("SMB", "renamed idx=" + prefs.activeIndex() + " to=" + nn + " all=" + prefs.profileNames())
          }
          showRename = false
        }) { Text("保存") }
      },
      dismissButton = {
        TextButton(onClick = { showRename = false }) { Text("取消") }
      },
    )
  }
}

private fun formatSize(bytes: Long): String = when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> "${bytes / 1024} KB"
  bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
  else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}