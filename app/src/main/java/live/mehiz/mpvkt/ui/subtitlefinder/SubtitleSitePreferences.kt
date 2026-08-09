package live.mehiz.mpvkt.ui.subtitlefinder

import android.content.Context
import android.content.SharedPreferences

/// 字幕库域名 / scheme / 搜索 URL 模板 持久化。
///
/// 跟 PlexiPlay subtitle_player Phase 1 一致:
///   - 默认 zimuku.org (用户可在 WebView 顶栏改成 SubHD / assrt / 自建站)
///   - 写进应用私有 SharedPreferences,下次启动沿用
///   - scheme 默认 https,自建 / 局域网字幕站可以切 http
///   - searchUrl 含 KEY 占位符,SubtitleFinderScreen 顶栏搜索按钮按下后替换成搜索词跳转
class SubtitleSitePreferences(context: Context) {
  private val prefs: SharedPreferences = context.applicationContext
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  var domain: String
    get() = prefs.getString(KEY_DOMAIN, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_DOMAIN
    set(value) {
      prefs.edit().putString(KEY_DOMAIN, cleanDomain(value)).apply()
    }

  var scheme: String
    get() = prefs.getString(KEY_SCHEME, null)?.lowercase()
      ?.takeIf { it == "http" || it == "https" } ?: DEFAULT_SCHEME
    set(value) {
      prefs.edit().putString(KEY_SCHEME, cleanScheme(value)).apply()
    }

  /// 搜索 URL 模板,含 KEY 占位符。例:https://zimuku.org/index.php?searchword=KEY
  /// 不含占位符就当成无搜索能力的站,搜索按钮隐藏。
  var searchUrl: String
    get() = prefs.getString(KEY_SEARCH_URL, null)?.takeIf { it.isNotBlank() } ?: defaultSearchUrl(scheme, domain)
    set(value) {
      prefs.edit().putString(KEY_SEARCH_URL, value.trim()).apply()
    }

  val homeUrl: String get() = "$scheme://$domain/"
  fun searchUrlFor(keyword: String): String = searchUrl.replace("KEY", keyword.trim())

  companion object {
    private const val PREFS_NAME = "subtitle_finder_prefs"
    private const val KEY_DOMAIN = "subtitle_domain"
    private const val KEY_SCHEME = "subtitle_scheme"
    private const val KEY_SEARCH_URL = "subtitle_search_url"
    const val DEFAULT_DOMAIN = "zimuku.org"
    const val DEFAULT_SCHEME = "https"

    private val DOMAIN_REGEX = Regex("^[A-Za-z0-9.\\-]+$")

    private fun cleanDomain(input: String): String {
      var s = input.trim()
      if (s.isEmpty()) return DEFAULT_DOMAIN
      s = s.replaceFirst(Regex("^https?://"), "")
      val slash = s.indexOf('/')
      if (slash >= 0) s = s.substring(0, slash)
      s = s.trim()
      if (s.isEmpty() || !DOMAIN_REGEX.matches(s)) return DEFAULT_DOMAIN
      return s
    }

    private fun cleanScheme(input: String): String {
      val s = input.trim().lowercase()
      return if (s == "http" || s == "https") s else DEFAULT_SCHEME
    }

    /// 按域名给常见站点的搜索 URL 模板。KEY 占位符会被搜索词替换。
    private fun defaultSearchUrl(scheme: String, domain: String): String = when (domain.lowercase()) {
      "zimuku.org", "zmk.tw" -> "$scheme://$domain/index.php?searchword=KEY"
      "subhd.tv" -> "$scheme://$domain/search/KEY"
      "assrt.net" -> "$scheme://$domain/sub/?searchword=KEY"
      else -> ""
    }
  }
}