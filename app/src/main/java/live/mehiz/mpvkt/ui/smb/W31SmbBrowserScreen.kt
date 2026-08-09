package live.mehiz.mpvkt.ui.smb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/// W31 SMB 浏览器:列出 share 内的目录/视频文件,点击下载后用 [onPlayFile] 播本地 cache。
///
/// Phase 1 限制:
///   - 全文件下载到 cacheDir/smb/<server>/<share>/<path>,1GB 视频约 30-60s
///   - 不支持边下边播
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
  var path by remember { mutableStateOf("") }
  var entries by remember { mutableStateOf<List<W31SmbClient.Entry>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var downloading by remember { mutableStateOf<String?>(null) }
  var downloadProgress by remember { mutableStateOf(0f) }

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
            text = if (prefs.isConfigured) "${prefs.server}/${prefs.share}" else "未配置",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
          )
          if (path.isNotEmpty()) {
            Text(text = "/$path", color = Color(0xFFAAAAAA), fontSize = 12.sp)
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
        Text(
          text = downloading!!,
          color = Color(0xFFCCCCCC),
          fontSize = 11.sp,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
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
                          val r = client!!.downloadToCache(
                            shareName = prefs.share,
                            remotePath = fullPath,
                            cacheRootDir = context.cacheDir,
                            onProgress = { read, total ->
                              if (total > 0) downloadProgress = read.toFloat() / total
                            },
                          )
                          downloading = null
                          r.onSuccess { f ->
                            onPlayFile(f)
                            onDismiss()
                          }.onFailure { e ->
                            error = "下载失败: ${e.message ?: e.javaClass.simpleName}"
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
                    Text(entry.name, color = Color.White, fontSize = 14.sp)
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
      onDismiss = { showConfig = false },
      onConfirm = {
        showConfig = false
        loadCurrent()
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