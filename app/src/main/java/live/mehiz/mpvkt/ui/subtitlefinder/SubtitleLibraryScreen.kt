package live.mehiz.mpvkt.ui.subtitlefinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/// 本地字幕库:展示所有已下载/解压过的字幕条目,点选直接加载。
/// 用户反馈 W30:解压出来的 6 个字幕必须都在本地,下次还能复用。
@Suppress("unused")
@Composable
fun SubtitleLibraryScreen(
  onDismiss: () -> Unit,
  onSubtitleChosen: (SubtitleLibraryEntry) -> Unit,
) {
  val context = LocalContext.current
  val manager = remember { SubtitleLibraryManager(context) }
  var entries by remember { mutableStateOf<List<SubtitleLibraryEntry>>(emptyList()) }
  var pendingDelete by remember { mutableStateOf<SubtitleLibraryEntry?>(null) }

  LaunchedEffect(Unit) { entries = manager.list() }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color(0xFF1A1A1A),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "本地字幕库 (${entries.size})",
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
        }
      }

      if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Text(
            "还没有字幕\n下载字幕后会自动入库",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          items(entries) { entry ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A2A2A))
                .clickable {
                  val file = runCatching { manager.resolve(entry) }.getOrNull()
                  if (file != null) {
                    onSubtitleChosen(entry)
                  }
                }
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = entry.displayName,
                  color = Color.White,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                )
                Text(
                  text = "${entry.sourceDomain} · ${formatTime(entry.addedAt)} · .${entry.ext}",
                  color = Color(0xFFAAAAAA),
                  fontSize = 11.sp,
                  modifier = Modifier.padding(top = 2.dp),
                )
              }
              IconButton(onClick = { pendingDelete = entry }) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFFF8080))
              }
            }
          }
        }
      }
    }
  }

  pendingDelete?.let { entry ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("删除字幕?") },
      text = { Text(entry.displayName) },
      confirmButton = {
        TextButton(onClick = {
          manager.remove(entry)
          entries = manager.list()
          pendingDelete = null
        }) { Text("删除", color = Color(0xFFFF8080)) }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text("取消") }
      },
    )
  }
}

private fun formatTime(millis: Long): String =
  SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))