package live.mehiz.mpvkt.ui.home

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity
import live.mehiz.mpvkt.domain.playbackhistory.repository.PlaybackHistoryRepository
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.ui.history.HistoryScreen
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.preferences.PreferencesScreen
import live.mehiz.mpvkt.ui.smb.W31SmbBrowserScreen
import live.mehiz.mpvkt.ui.theme.spacing
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import org.koin.compose.koinInject

@Serializable
object HomeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val historyRepository: PlaybackHistoryRepository = koinInject()
    val history by produceHistory(historyRepository)

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
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
          var showSmb by remember { mutableStateOf(false) }
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
          val documentPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
          ) {
            if (it == null) return@rememberLauncherForActivityResult
            playFile(it.toString(), context)
          }
          OutlinedButton(
            onClick = { documentPicker.launch(arrayOf("*/*")) },
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.FileOpen, null)
              Text(text = stringResource(R.string.home_pick_file))
            }
          }
          val fileManager = FileManager(context)
          val directoryPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
          ) {
            if (it == null) return@rememberLauncherForActivityResult
            backstack.add(FilePickerScreen(fileManager.fromUri(it)!!.getFullPath()))
          }
          OutlinedButton(onClick = { directoryPicker.launch(null) }) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.FolderOpen, null)
              Text(text = stringResource(R.string.home_open_file_picker))
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
  }

  @Composable
  private fun RecentHistoryCard(
    entry: PlaybackHistoryEntity,
    onClick: () -> Unit,
  ) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
            maxLines = 1,
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
    val i = Intent(Intent.ACTION_VIEW, filepath.toUri())
    i.setClass(context, PlayerActivity::class.java)
    context.startActivity(i)
  }
}