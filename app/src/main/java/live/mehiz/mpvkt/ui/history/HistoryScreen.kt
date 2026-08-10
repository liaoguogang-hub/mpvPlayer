package live.mehiz.mpvkt.ui.history

import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import live.mehiz.mpvkt.ui.utils.LocalBackStack
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.database.entities.PlaybackHistoryEntity
import live.mehiz.mpvkt.domain.playbackhistory.repository.PlaybackHistoryRepository
import live.mehiz.mpvkt.presentation.Screen
import live.mehiz.mpvkt.presentation.components.ConfirmDialog
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.isPlayable
import live.mehiz.mpvkt.ui.theme.spacing
import org.koin.compose.koinInject

@Serializable
object HistoryScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val historyRepository: PlaybackHistoryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val history by produceHistory(historyRepository)

    var showClearAllDialog by remember { mutableStateOf(false) }

    BackHandler { backstack.removeLastOrNull() }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.history_title)) },
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
          },
          actions = {
            if (history.isNotEmpty()) {
              IconButton(onClick = { showClearAllDialog = true }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.history_clear_all))
              }
            }
          },
        )
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (history.isEmpty()) {
          HistoryEmpty()
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
              horizontal = MaterialTheme.spacing.medium,
              vertical = MaterialTheme.spacing.smaller,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller),
          ) {
            items(history, key = { it.uri }) { entry ->
              HistoryListItem(
                entry = entry,
                onClick = {
                  // W31.18: 预检 content URI 权限,失效就 Toast + 删条目,避免 PlayerActivity.onCreate 闪退
                  val uri = runCatching { entry.uri.toUri() }.getOrNull()
                  if (uri != null && !uri.isPlayable(context)) {
                    Toast.makeText(context, "File no longer accessible, removing from history", Toast.LENGTH_LONG).show()
                    scope.launch(Dispatchers.IO) { historyRepository.deleteByUri(entry.uri) }
                    return@HistoryListItem
                  }
                  try {
                    context.startActivity(
                      android.content.Intent(android.content.Intent.ACTION_VIEW, entry.uri.toUri())
                        .setClass(context, PlayerActivity::class.java)
                    )
                  } catch (e: Exception) {
                    Toast.makeText(context, e.localizedMessage ?: "Cannot open", Toast.LENGTH_SHORT).show()
                  }
                },
                onDelete = {
                  scope.launch(Dispatchers.IO) { historyRepository.deleteByUri(entry.uri) }
                },
              )
            }
          }
        }
      }
    }

    if (showClearAllDialog) {
      ConfirmDialog(
        title = stringResource(R.string.history_clear_confirm_title),
        subtitle = stringResource(R.string.history_clear_confirm_subtitle),
        onConfirm = {
          showClearAllDialog = false
          scope.launch(Dispatchers.IO) { historyRepository.clearAll() }
          Toast.makeText(context, R.string.history_clear_toast, Toast.LENGTH_SHORT).show()
        },
        onCancel = { showClearAllDialog = false },
      )
    }
  }
}

@Composable
private fun HistoryListItem(
  entry: PlaybackHistoryEntity,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier
          .weight(1f)
          .clickable(onClick = onClick)
          .padding(
            start = MaterialTheme.spacing.medium,
            top = MaterialTheme.spacing.medium,
            end = MaterialTheme.spacing.smaller,
            bottom = MaterialTheme.spacing.medium,
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
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = relativeTimeString(entry.lastPlayedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          )
          Text(
            text = formatDuration(entry.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
          )
        }
      }
      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = stringResource(R.string.history_item_delete),
        )
      }
    }
  }
}

@Composable
private fun HistoryEmpty() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.History,
      contentDescription = null,
      modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
    )
    Text(
      text = stringResource(R.string.history_empty),
      style = MaterialTheme.typography.titleMedium,
    )
  }
}

@Composable
private fun relativeTimeString(epochMs: Long): String {
  return DateUtils.getRelativeTimeSpanString(
    epochMs,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
  ).toString()
}

private fun formatDuration(seconds: Int): String {
  if (seconds <= 0) return "Unknown duration"
  val h = seconds / 3600
  val m = (seconds % 3600) / 60
  val s = seconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun produceHistory(repository: PlaybackHistoryRepository) =
  androidx.compose.runtime.produceState<List<PlaybackHistoryEntity>>(initialValue = emptyList(), repository) {
    repository.observeAll().collectLatest { value = it }
  }