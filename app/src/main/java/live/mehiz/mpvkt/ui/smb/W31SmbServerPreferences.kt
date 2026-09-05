package live.mehiz.mpvkt.ui.smb

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/// W31 SMB server 配置持久化(多配置):多个 server 各存一份,可下拉切换。
class W31SmbServerPreferences(context: Context) {
  data class Profile(
    var name: String = "",
    var server: String = "",
    var port: Int = 445,
    var share: String = "",
    var username: String = "",
    var password: String = "",
    var domain: String = "",
  ) {
    val isFilled: Boolean get() = server.isNotBlank() && share.isNotBlank()
    fun toJson(): JSONObject = JSONObject().apply {
      put("name", name); put("server", server); put("port", port); put("share", share)
      put("username", username); put("password", password); put("domain", domain)
    }
    companion object {
      fun fromJson(o: JSONObject): Profile = Profile(
        name = o.optString("name", ""), server = o.optString("server", ""),
        port = o.optInt("port", 445), share = o.optString("share", ""),
        username = o.optString("username", ""), password = o.optString("password", ""),
        domain = o.optString("domain", ""),
      )
    }
  }

  private val prefs: SharedPreferences = context.applicationContext
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val list: MutableList<Profile> = mutableListOf()
  private var activeIdx: Int = 0

  init {
    val raw = prefs.getString(KEY_JSON, null)
    if (raw != null) {
      try {
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
          list.add(Profile.fromJson(arr.getJSONObject(i)))
        }
      } catch (_: Exception) {
        list.clear()
      }
    }
    if (list.isEmpty()) {
      val legacyServer = prefs.getString(KEY_SERVER, null).orEmpty()
      val legacyShare = prefs.getString(KEY_SHARE, null).orEmpty()
      if (legacyServer.isNotBlank() || legacyShare.isNotBlank()) {
        list.add(
          Profile(
            name = legacyServer.ifBlank { legacyShare },
            server = legacyServer,
            port = prefs.getInt(KEY_PORT, 445),
            share = legacyShare,
            username = prefs.getString(KEY_USERNAME, null).orEmpty(),
            password = prefs.getString(KEY_PASSWORD, null).orEmpty(),
            domain = prefs.getString(KEY_DOMAIN, null).orEmpty(),
          ),
        )
        persist()
      }
    }
    if (list.isEmpty()) list.add(Profile())
    val activeName = prefs.getString(KEY_ACTIVE, null)
    val found = if (activeName != null) list.indexOfFirst { it.name == activeName } else -1
    if (found >= 0) activeIdx = found
  }

  private fun active(): Profile = list[activeIdx.coerceIn(0, list.size - 1)]

  private fun persist() {
    val arr = JSONArray()
    for (p in list) arr.put(p.toJson())
    prefs.edit().putString(KEY_JSON, arr.toString()).apply()
  }

  private fun persistActive() {
    prefs.edit().putString(KEY_ACTIVE, active().name).apply()
  }

  var name: String
    get() = active().name
    set(value) { active().name = value.trim(); persist() }

  var server: String
    get() = active().server
    set(value) { active().server = value.trim(); persist() }

  var port: Int
    get() = active().port
    set(value) { active().port = if (value in 1..65535) value else 445; persist() }

  var share: String
    get() = active().share
    set(value) { active().share = value.trim(); persist() }

  var username: String
    get() = active().username
    set(value) { active().username = value; persist() }

  var password: String
    get() = active().password
    set(value) { active().password = value; persist() }

  var domain: String
    get() = active().domain
    set(value) { active().domain = value; persist() }

  val isConfigured: Boolean
    get() = active().isFilled

  fun toClient(): W31SmbClient = W31SmbClient(
    server = active().server,
    port = active().port,
    username = active().username,
    password = active().password,
    domain = active().domain,
  )

  fun profiles(): List<Profile> = list.toList()
  fun profileNames(): List<String> = list.mapIndexed { i, p ->
    p.name.ifBlank { if (i == activeIdx) "(未命名)" else "server " + (i + 1) }
  }
  fun activeIndex(): Int = activeIdx

  fun selectIndex(i: Int) {
    if (i in 0 until list.size) {
      activeIdx = i
      persistActive()
    }
  }

  /** Save the currently edited profile (blank name falls back to the server address). */
  fun saveActiveProfile() {
    if (active().name.isBlank()) active().name = active().server.ifBlank { "SMB " + (activeIdx + 1) }
    persist()
    persistActive()
    android.util.Log.i("SMB", "saved idx=" + activeIdx + " name=" + active().name + " all=" + profileNames())
  }

  /** Start a brand-new empty profile and switch to it. */
  fun newProfile() {
    list.add(Profile())
    activeIdx = list.size - 1
    persist()
    persistActive()
  }

  fun deleteProfileAt(i: Int) {
    if (list.size <= 1 || i !in 0 until list.size) return
    list.removeAt(i)
    if (activeIdx >= list.size) activeIdx = list.size - 1
    persist()
    persistActive()
  }

  /** Remove the active profile only when it is still completely blank (cancelled "new"). */
  fun discardBlankActive() {
    val a = active()
    if (list.size > 1 && a.name.isBlank() && a.server.isBlank() && a.share.isBlank()) {
      list.removeAt(activeIdx)
      if (activeIdx >= list.size) activeIdx = list.size - 1
      persist()
      persistActive()
    }
  }

  fun clear() {
    val a = active()
    a.name = ""; a.server = ""; a.port = 445; a.share = ""; a.username = ""; a.password = ""; a.domain = ""
    persist()
  }

  companion object {
    private const val PREFS_NAME = "w31_smb_server_prefs"
    private const val KEY_JSON = "profiles_json"
    private const val KEY_ACTIVE = "active_profile"
    private const val KEY_SERVER = "server"
    private const val KEY_PORT = "port"
    private const val KEY_SHARE = "share"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_DOMAIN = "domain"
  }
}
