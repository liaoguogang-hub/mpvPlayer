package live.mehiz.mpvkt.ui.subtitlefinder

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.io.File

/// "在线找字幕"屏 (W30):内嵌 WebView 打开字幕库,用户自己搜索/过 Cloudflare/点下载,
/// 拦截到 .zip 后自动下载 + 解压,所有解压出来的字幕都入本地库,
/// 弹底部选择让用户挑要加载哪个,挑完调 [onSubtitleFound] 回传 File。
///
/// 跟 PlexiPlay subtitle_player Phase 1 思路一致:
///   - 零反爬逻辑,反爬交给用户手动过
///   - WebView 的 cookie/UA 复用,确保下载不被站点的 referer / Cloudflare 拦
///   - WebView 用 desktop UA,避免 zimuku/SubHD 把手机 UA 当爬虫返 Cloudflare 验证页
///   - zip 自动解压,保留原中文文件名(GBK 修),zip/rar/7z 全支持
///
/// W30 新增:
///   - searchUrl 模板(KEY 占位符),顶栏搜索按钮按关键词直接跳搜索结果页
///   - SubtitleLibraryManager 持久化所有解压字幕到 filesDir/subtitles/library/,
///     即使选了 1 个,其他 5 个也在本地库中,下次可从本地库入口选
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SubtitleFinderScreen(
  initialQuery: String,
  onDismiss: () -> Unit,
  onSubtitleFound: (File) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val sitePrefs = remember { SubtitleSitePreferences(context) }
  var scheme by remember { mutableStateOf(sitePrefs.scheme) }
  var domain by remember { mutableStateOf(sitePrefs.domain) }
  var searchUrlTemplate by remember { mutableStateOf(sitePrefs.searchUrl) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showSearchDialog by remember { mutableStateOf(initialQuery.isNotEmpty()) }
  var showLibrary by remember { mutableStateOf(false) }
  var webView by remember { mutableStateOf<WebView?>(null) }

  var loadError by remember { mutableStateOf<String?>(null) }
  var downloading by remember { mutableStateOf(false) }
  var downloadProgress by remember { mutableStateOf(0f) }
  var downloadError by remember { mutableStateOf<String?>(null) }
  var candidates by remember { mutableStateOf<List<SubtitleFile>?>(null) }

  fun applySite(newScheme: String, newDomain: String, newSearchUrl: String) {
    sitePrefs.scheme = newScheme
    sitePrefs.domain = newDomain
    sitePrefs.searchUrl = newSearchUrl
    scheme = newScheme
    domain = newDomain
    searchUrlTemplate = newSearchUrl
    loadError = null
    webView?.loadUrl("${newScheme}://${newDomain}/")
  }

  fun loadIngested(chosen: SubtitleFile, all: List<SubtitleFile>) {
    // W30:全部入库 → 找 chosen 对应的新 entry → 调 onSubtitleFound 加载它
    val manager = SubtitleLibraryManager(context)
    val added = manager.ingest(all, sitePrefs.domain)
    val match = added.firstOrNull { it.displayName == chosen.originalName }
      ?: added.firstOrNull { it.fileName == chosen.path.name }
    if (match != null) {
      val file = manager.resolve(match)
      onSubtitleFound(file)
    } else {
      onSubtitleFound(chosen.path)
    }
    onDismiss()
  }

  LaunchedEffect(initialQuery) {
    if (initialQuery.isNotEmpty() && searchUrlTemplate.isNotBlank()) {
      webView?.loadUrl(sitePrefs.searchUrlFor(initialQuery))
    }
  }

  BackHandler(enabled = true) { onDismiss() }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color.Black,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
      // 顶栏
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
        }
        Text(
          text = "$scheme://$domain",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        if (searchUrlTemplate.isNotBlank()) {
          IconButton(onClick = { showSearchDialog = true }) {
            Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White)
          }
        }
        IconButton(onClick = { showLibrary = true }) {
          Icon(Icons.Default.Folder, contentDescription = "本地字幕库", tint = Color.White)
        }
        IconButton(onClick = { showEditDialog = true }) {
          Icon(Icons.Default.Dns, contentDescription = "修改域名", tint = Color.White)
        }
      }

      if (downloading) {
        LinearProgressIndicator(
          progress = { if (downloadProgress <= 0f) 0f else downloadProgress },
          modifier = Modifier.fillMaxWidth(),
          color = Color(0xFF80C8FF),
        )
      }
      downloadError?.let { msg ->
        Text(
          text = msg,
          color = Color(0xFFFF8080),
          fontSize = 12.sp,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
      }

      Box(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        if (loadError != null) {
          LoadErrorView(message = loadError!!, onRetry = {
            loadError = null
            webView?.reload()
          })
        } else {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  useWideViewPort = true
                  loadWithOverviewMode = true
                  allowFileAccess = true
                  cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                  // W30:用 desktop Chrome UA。zimuku/SubHD Cloudflare 默认拦手机 UA 当爬虫。
                  userAgentString = DESKTOP_UA
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                  override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                  ) {
                    loadError = "加载失败: $description\nURL: $failingUrl"
                  }
                }
                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                  scope.launch {
                    downloading = true
                    downloadProgress = 0f
                    downloadError = null
                    val result = SubtitleDownloader.download(
                      context = context,
                      url = url,
                      onProgress = { p -> downloadProgress = p },
                    )
                    downloading = false
                    result.onSuccess { archiveFile ->
                      val outDir = File(context.cacheDir, "subtitles/extracted").apply { mkdirs() }
                      val all = ArchiveExtractor.extractAll(archiveFile, outDir)
                      archiveFile.delete()
                      when {
                        all.isEmpty() -> {
                          val kind = ArchiveExtractor.sniff(archiveFile)
                          downloadError = "解压后没找到 .srt/.ass 字幕文件 (格式=$kind)"
                        }
                        all.size == 1 -> {
                          loadIngested(all.first(), all)
                        }
                        else -> {
                          candidates = all
                        }
                      }
                    }.onFailure { e ->
                      downloadError = "下载失败: ${e.message ?: e.javaClass.simpleName}"
                    }
                  }
                }
                loadUrl("${sitePrefs.scheme}://${sitePrefs.domain}/")
                webView = this
              }
            },
            onRelease = { v ->
              v.stopLoading()
              v.destroy()
              webView = null
            },
          )
        }
      }
    }
  }

  if (showEditDialog) {
    EditSiteDialog(
      initialScheme = scheme,
      initialDomain = domain,
      initialSearchUrl = searchUrlTemplate,
      onDismiss = { showEditDialog = false },
      onConfirm = { newScheme, newDomain, newSearchUrl ->
        showEditDialog = false
        applySite(newScheme, newDomain, newSearchUrl)
      },
      onReset = {
        showEditDialog = false
        applySite(
          SubtitleSitePreferences.DEFAULT_SCHEME,
          SubtitleSitePreferences.DEFAULT_DOMAIN,
          "",
        )
      },
    )
  }

  if (showSearchDialog) {
    SearchDialog(
      initialQuery = initialQuery,
      onDismiss = { showSearchDialog = false },
      onSearch = { keyword ->
        showSearchDialog = false
        if (searchUrlTemplate.isNotBlank() && keyword.isNotBlank()) {
          webView?.loadUrl(sitePrefs.searchUrlFor(keyword))
        }
      },
    )
  }

  candidates?.let { list ->
    CandidatePickerSheet(
      candidates = list,
      onDismiss = { candidates = null },
      onPick = { picked ->
        candidates = null
        loadIngested(picked, list)
      },
    )
  }

  if (showLibrary) {
    SubtitleLibraryScreen(
      onDismiss = { showLibrary = false },
      onSubtitleChosen = { entry ->
        showLibrary = false
        val file = runCatching { SubtitleLibraryManager(context).resolve(entry) }.getOrNull()
        if (file != null) {
          onSubtitleFound(file)
          onDismiss()
        }
      },
    )
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CandidatePickerSheet(
  candidates: List<SubtitleFile>,
  onDismiss: () -> Unit,
  onPick: (SubtitleFile) -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF1A1A1A),
    scrimColor = Color.Black.copy(alpha = 0.6f),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
        text = "找到 ${candidates.size} 个字幕,选一个(其余自动入本地库)",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
      )
      LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        items(candidates) { file ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF2A2A2A))
              .clickable { onPick(file) }
              .padding(vertical = 12.dp, horizontal = 12.dp),
          ) {
            Text(file.originalName, color = Color.White, fontSize = 14.sp)
            Text(
              text = ".${file.path.name.substringAfterLast('.')}",
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

@Composable
private fun LoadErrorView(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.Start,
  ) {
    Text("无法加载字幕库", color = Color.White, fontSize = 18.sp)
    Text(message, color = Color(0xFFCCCCCC), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
      Text("重试")
    }
  }
}

@Composable
private fun EditSiteDialog(
  initialScheme: String,
  initialDomain: String,
  initialSearchUrl: String,
  onDismiss: () -> Unit,
  onConfirm: (scheme: String, domain: String, searchUrl: String) -> Unit,
  onReset: () -> Unit,
) {
  var scheme by remember { mutableStateOf(initialScheme) }
  var domainInput by remember { mutableStateOf(initialDomain) }
  var searchUrlInput by remember { mutableStateOf(initialSearchUrl) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("修改字幕库") },
    text = {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Row {
            TextButton(
              onClick = { scheme = "https" },
              colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = if (scheme == "https") Color(0xFF80C8FF).copy(alpha = 0.2f) else Color.Transparent,
              ),
            ) { Text("https") }
            TextButton(
              onClick = { scheme = "http" },
              colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = if (scheme == "http") Color(0xFF80C8FF).copy(alpha = 0.2f) else Color.Transparent,
              ),
            ) { Text("http") }
          }
          OutlinedTextField(
            value = domainInput,
            onValueChange = { domainInput = it },
            singleLine = true,
            modifier = Modifier.padding(start = 8.dp).fillMaxWidth(),
            label = { Text("域名") },
          )
        }
        OutlinedTextField(
          value = searchUrlInput,
          onValueChange = { searchUrlInput = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          label = { Text("搜索 URL 模板 (含 KEY 占位符)") },
        )
        Text(
          "例:https://zimuku.org/index.php?searchword=KEY。空 = 顶栏隐藏搜索按钮。",
          fontSize = 12.sp,
          color = Color(0xFFAAAAAA),
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(scheme, domainInput, searchUrlInput) }) { Text("保存") }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onReset) { Text("恢复默认") }
        TextButton(onClick = onDismiss) { Text("取消") }
      }
    },
  )
}

@Composable
private fun SearchDialog(
  initialQuery: String,
  onDismiss: () -> Unit,
  onSearch: (String) -> Unit,
) {
  var keyword by remember { mutableStateOf(initialQuery) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("搜索字幕") },
    text = {
      OutlinedTextField(
        value = keyword,
        onValueChange = { keyword = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("片名 / 关键字") },
      )
    },
    confirmButton = {
      TextButton(onClick = { onSearch(keyword) }) { Text("搜索") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

/// 桌面 Chrome UA。zimuku/SubHD/assrt 等 Cloudflare 站默认拦手机 UA。
/// 复用同一个 UA,WebView cookie/UA 一致性最稳。
private const val DESKTOP_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"