package live.mehiz.mpvkt.ui.subtitlefinder

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/// 拦截到 WebView 下载链接后,用 HttpURLConnection 复用 WebView 的 cookie + UA
/// 把字幕压缩包下到 cacheDir/subtitles/。Phase 1 不引 OkHttp,零新依赖。
///
/// sanity check: 文件 < 1KB 直接删掉,几乎可以肯定是 HTML 错误页(Cloudflare /
// 404 / 反爬中间页)而非真正的字幕包。
object SubtitleDownloader {

  suspend fun download(
    context: Context,
    url: String,
    onProgress: ((Float) -> Unit)? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val cookies = CookieManager.getInstance().getCookie(url) ?: ""
      val ua = runCatching { WebSettings.getDefaultUserAgent(context) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

      val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 30_000
        readTimeout = 60_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", ua)
        if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
        setRequestProperty("Accept", "*/*")
      }

      val code = conn.responseCode
      if (code !in 200..299) {
        conn.disconnect()
        throw RuntimeException("HTTP $code ${conn.responseMessage ?: ""}".trim())
      }

      val contentLength = conn.contentLengthLong
      val outDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
      val outFile = File(outDir, "sub_${System.currentTimeMillis()}.bin")

      conn.inputStream.use { input ->
        FileOutputStream(outFile).use { output ->
          val buf = ByteArray(64 * 1024)
          var total = 0L
          var lastEmit = 0L
          while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            output.write(buf, 0, read)
            total += read
            if (contentLength > 0 && onProgress != null) {
              val now = System.currentTimeMillis()
              if (now - lastEmit > 100) {
                lastEmit = now
                onProgress(total.toFloat() / contentLength)
              }
            }
          }
          if (onProgress != null) onProgress(1f)
        }
      }

      if (outFile.length() < 1024) {
        outFile.delete()
        throw RuntimeException("文件太小 (${outFile.length()}B),不是字幕压缩包")
      }

      outFile
    }
  }
}