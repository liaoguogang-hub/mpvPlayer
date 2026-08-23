package live.mehiz.mpvkt.ui.smb

import android.content.Context
import android.content.SharedPreferences

/// W31 SMB 局域网视频 server 配置持久化。
///
/// 单一 server 配置(Phase 1):家庭/办公场景一个 NAS / 文件服务器够用。
/// 多 server 列表是 Phase 2。
class W31SmbServerPreferences(context: Context) {
  private val prefs: SharedPreferences = context.applicationContext
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  var server: String
    get() = prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: ""
    set(value) { prefs.edit().putString(KEY_SERVER, value.trim()).apply() }

  var port: Int
    get() = prefs.getInt(KEY_PORT, 445)
    set(value) { prefs.edit().putInt(KEY_PORT, if (value in 1..65535) value else 445).apply() }

  var share: String
    get() = prefs.getString(KEY_SHARE, null)?.takeIf { it.isNotBlank() } ?: ""
    set(value) { prefs.edit().putString(KEY_SHARE, value.trim()).apply() }

  var username: String
    get() = prefs.getString(KEY_USERNAME, null) ?: ""
    set(value) { prefs.edit().putString(KEY_USERNAME, value).apply() }

  var password: String
    get() = prefs.getString(KEY_PASSWORD, null) ?: ""
    set(value) { prefs.edit().putString(KEY_PASSWORD, value).apply() }

  var domain: String
    get() = prefs.getString(KEY_DOMAIN, null) ?: ""
    set(value) { prefs.edit().putString(KEY_DOMAIN, value).apply() }

  val isConfigured: Boolean
    get() = server.isNotBlank() && share.isNotBlank()

  fun toClient(): W31SmbClient = W31SmbClient(
    server = server,
    port = port,
    username = username,
    password = password,
    domain = domain,
  )

  fun clear() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val PREFS_NAME = "w31_smb_server_prefs"
    private const val KEY_SERVER = "server"
    private const val KEY_PORT = "port"
    private const val KEY_SHARE = "share"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_DOMAIN = "domain"
  }
}