package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.Utils.PROTOCOLS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity
import live.mehiz.mpvkt.domain.playbackhistory.repository.PlaybackHistoryRepository
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.ConfirmDialog
import live.mehiz.mpvkt.ui.history.HistoryScreen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.isPlayable
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.smb.W31SmbBrowserScreen
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
object HomeScreen : Screen, KoinComponent {
  // W31.18: 让 playFile() (非 @Composable) 能拿 repository 做 deleteByUri
  private val historyRepo: PlaybackHistoryRepository by inject()
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack: androidx.navigation3.runtime.NavBackStack<Screen> = LocalBackStack.current
    val historyRepository: PlaybackHistoryRepository = koinInject()
    val history by produceHistory(historyRepository)
    // W31.24: SMB 浏览器是 Box 全屏 overlay (不能放进 verticalScroll Column,会触发
    // LazyColumn 被测到 infinity maxHeight → IllegalStateException)。
    // state 提到 Content() 顶部,Box/Column 同级访问同一份 remember。
    var showSmb by remember { mutableStateOf(false) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = R.string.app_name)) },
          actions = {
            IconButton(onClick = { backstack.add(PreferencesScreen) }) {
              Icon(Icons.Default.Settings, null)
            }
          },
          navigationIcon = {
            Image(
              painter = painterResource(id = R.drawable.ic_launcher_foreground),
              contentDescription = "app_logo",
            )
          },
        )
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Top,
        ) {
        // W31.15: 最近播放 section,只在有数据时渲染
        if (history.isNotEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.medium,
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.History,
                  contentDescription = null,
                  modifier = Modifier.padding(end = MaterialTheme.spacing.smaller),
                )
                Text(
                  text = stringResource(R.string.home_recent_title),
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              TextButton(onClick = { backstack.add(HistoryScreen) }) {
                Text(stringResource(R.string.home_view_all))
              }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.smaller))
            history.take(5).forEach { entry ->
              RecentHistoryCard(
                entry = entry,
                onClick = { playFile(entry.uri, context) },
              )
              Spacer(Modifier.height(MaterialTheme.spacing.extraSmall))
            }
          }
        }

        // 打开视频按钮组
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              horizontal = MaterialTheme.spacing.medium,
              vertical = MaterialTheme.spacing.large,
            ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
        ) {
          val uri = rememberTextFieldState()
          var isUrlValid by remember { mutableStateOf(true) }
          LaunchedEffect(uri.text) {
            isUrlValid = uri.text.isNotEmpty() || isURLValid(uri.text.toString())
          }
          OutlinedTextField(
            state = uri,
            label = { Text(stringResource(R.string.home_url_input_label)) },
            supportingText = {
              Text(if (isUrlValid) "" else stringResource(R.string.home_invalid_protocol))
            },
            trailingIcon = {
              if (!isUrlValid) Icon(Icons.Filled.Info, null)
            },
            isError = !isUrlValid,
          )
          Button(
            onClick = { playFile(uri.text.toString(), context) },
            enabled = uri.text.isNotBlank() && isUrlValid,
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Link, null)
              Text(text = stringResource(R.string.home_open_url))
            }
          }
          // W31.20: 默认用 app 内置 FilePickerScreen 浏览视频(分 2 行文件名 + 文件大小 +
          // 修改时间,避免系统 SAF picker "最近"列表单行截断)。SAF picker 留作高级入口
          // 满足需要跨 app 共享文件/选 SD 卡等场景。
          val fileManager = FileManager(context)
          val directoryPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
          ) {
            if (it == null) return@rememberLauncherForActivityResult
            // W31.22: OpenDocumentTree 返回的 treeUri 默认带临时 grant(跟 Activity 走),
            // 重启 app / 系统 LRU 清理后 grant 失效 → 子文件 content URI 也跟着失效 →
            // 历史记录点开 isPlayable 失败 → "File no longer accessible" → 删条目。
            // 必须 take 持久权限,所有 child URI 跟着 tree grant 一起持久化。
            // W31.23: 必须 READ|WRITE 双 flag(部分 ROM DocumentsUI 不接受只 READ),
            // catch 改 Exception 防止 IllegalArgumentException 等被吞,
            // 成功也打 Log.i 便于 dumpsys content 之外的现场确认。
            try {
              val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
              context.contentResolver.takePersistableUriPermission(it, flags)
              android.util.Log.i("HomeScreen", "W31.23 takePersistableUriPermission OK tree=$it flags=$flags")
            } catch (e: Exception) {
              android.util.Log.w("HomeScreen", "W31.23 takePersistableUriPermission FAILED tree=$it: ${e.javaClass.simpleName}: ${e.message}")
            }
            backstack.add(FilePickerScreen(fileManager.fromUri(it)!!.getFullPath()))
          }
          OutlinedButton(onClick = { directoryPicker.launch(null) }) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.FolderOpen, null)
              Text(text = stringResource(R.string.home_browse_files))
            }
          }
          val documentPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
          ) {
            if (it == null) return@rememberLauncherForActivityResult
            // W31.18: ACTION_OPEN_DOCUMENT 返回的 content URI 默认带临时 grant
            // (跟着 Activity 生命周期走),重启 app 后再访问就 SecurityException。
            // 历史播放点开历史记录会取这个 URI 重启,必须 take 持久权限。
            try {
              val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
              context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
              // 极少数 provider 不允许持久化,不影响本次播放
              android.util.Log.w("HomeScreen", "takePersistableUriPermission failed: ${e.message}")
            }
            playFile(it.toString(), context)
          }
          // W31.27:SAF document picker 提升为主入口(原 v0.2.4-8 风格)。FilePickerScreen
          // 仍保留作为内置浏览,SAF 走系统 DocumentsUI / Files 跨 app 共享。
          OutlinedButton(onClick = { documentPicker.launch(arrayOf("*/*")) }) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.FileOpen, null)
              Text(text = stringResource(R.string.home_pick_file))
            }
          }
          // W31:SMB 局域网视频入口
          OutlinedButton(onClick = { showSmb = true }) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Storage, null)
              Text(text = "SMB 局域网")
            }
          }
        }
      }
      if (showSmb) {
        androidx.compose.runtime.key(Unit) {
          androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black,
          ) {
            W31SmbBrowserScreen(
              onDismiss = { showSmb = false },
              onPlayFile = { file ->
                playFile(file.absolutePath, context)
              },
            )
          }
        }
      }
    }
  }
}

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun RecentHistoryCard(
    entry: PlaybackHistoryEntity,
    onClick: () -> Unit,
  ) {
    val historyRepository: PlaybackHistoryRepository = koinInject()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    ElevatedCard(
      modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = onClick,
          onLongClick = { showDeleteDialog = true },
        ),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            horizontal = MaterialTheme.spacing.medium,
            vertical = MaterialTheme.spacing.small,
          ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.History,
          contentDescription = null,
        )
        Column(
          modifier = Modifier
            .padding(start = MaterialTheme.spacing.medium)
            .weight(1f),
        ) {
          Text(
            text = entry.displayName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            text = DateUtils.getRelativeTimeSpanString(
              entry.lastPlayedAt,
              System.currentTimeMillis(),
              DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          )
        }
      }
    }

    if (showDeleteDialog) {
      ConfirmDialog(
        title = stringResource(R.string.home_recent_item_delete_title),
        subtitle = stringResource(R.string.home_recent_item_delete_subtitle),
        onConfirm = {
          showDeleteDialog = false
          scope.launch(Dispatchers.IO) { historyRepository.deleteByUri(entry.uri) }
        },
        onCancel = { showDeleteDialog = false },
      )
    }
  }

  @Composable
  private fun produceHistory(repository: PlaybackHistoryRepository) =
    androidx.compose.runtime.produceState<List<PlaybackHistoryEntity>>(initialValue = emptyList(), repository) {
      repository.observeRecent(5).collectLatest { value = it }
    }

  // Basically a copy of:
  // https://github.com/mpv-android/mpv-android/blob/32cbff3cedea73b4616b34542cb95bf1d00504cc/app/src/main/java/is/xyz/mpv/Utils.kt#L406
  private fun isURLValid(url: String): Boolean {
    val uri = url.toUri()
    return uri.isHierarchical && !uri.isRelative &&
      !(uri.host.isNullOrBlank() && uri.path.isNullOrBlank()) &&
      PROTOCOLS.contains(uri.scheme)
  }

  fun playFile(
    filepath: String,
    context: Context,
  ) {
    // W31.18: 预检 content URI 权限,失效就 Toast + 删历史条目,避免 PlayerActivity.onCreate 闪退
    val uri = runCatching { filepath.toUri() }.getOrNull()
    if (uri != null && !uri.isPlayable(context)) {
      Toast.makeText(context, "File no longer accessible, removing from history", Toast.LENGTH_LONG).show()
      kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        historyRepo.deleteByUri(filepath)
      }
      return
    }
    val i = Intent(Intent.ACTION_VIEW, filepath.toUri())
    i.setClass(context, PlayerActivity::class.java)
    context.startActivity(i)
  }
}