package live.mehiz.mpvkt.ui.smb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// W31 SMB server 配置弹窗。
@Composable
fun W31SmbServerDialog(
  initial: W31SmbServerPreferences,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  var server by remember { mutableStateOf(initial.server) }
  var port by remember { mutableStateOf(initial.port.toString()) }
  var share by remember { mutableStateOf(initial.share) }
  var username by remember { mutableStateOf(initial.username) }
  var password by remember { mutableStateOf(initial.password) }
  var domain by remember { mutableStateOf(initial.domain) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("SMB 局域网视频") },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = server,
          onValueChange = { server = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("服务器 IP / 主机名") },
          placeholder = { Text("192.168.50.1") },
        )
        OutlinedTextField(
          value = port,
          onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          label = { Text("端口 (默认 445)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
          value = share,
          onValueChange = { share = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          label = { Text("共享名") },
          placeholder = { Text("toshiba_ext") },
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          label = { Text("用户名 (匿名共享留空)") },
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          label = { Text("密码 (匿名共享留空)") },
          visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
          value = domain,
          onValueChange = { domain = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          label = { Text("域 (可选,家庭 NAS 留空)") },
        )
        Text(
          "配好后会自动列出共享里的视频,选完下载到本地再播。1GB 视频大约 30-60s (100Mbps 局域网)。",
          fontSize = 12.sp,
          color = Color(0xFFAAAAAA),
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          initial.server = server
          initial.port = port.toIntOrNull() ?: 445
          initial.share = share
          initial.username = username
          initial.password = password
          initial.domain = domain
          onConfirm()
        },
        enabled = server.isNotBlank() && share.isNotBlank(),
      ) { Text("保存") }
    },
    dismissButton = {
      Row {
        TextButton(onClick = {
          initial.clear()
          onConfirm()
        }) { Text("清空") }
        TextButton(onClick = onDismiss) { Text("取消") }
      }
    },
  )
}