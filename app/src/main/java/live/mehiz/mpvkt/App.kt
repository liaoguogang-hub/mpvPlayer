package live.mehiz.mpvkt

import android.app.Application
import live.mehiz.mpvkt.di.AppModule
import live.mehiz.mpvkt.di.DatabaseModule
import live.mehiz.mpvkt.di.FileManagerModule
import live.mehiz.mpvkt.di.PreferencesModule
import live.mehiz.mpvkt.di.ViewModelModule
import live.mehiz.mpvkt.presentation.crash.CrashActivity
import live.mehiz.mpvkt.presentation.crash.GlobalExceptionHandler
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration
import java.security.Security

@OptIn(KoinExperimentalAPI::class)
class App : Application(), KoinStartup {
  override fun onCreate() {
    super.onCreate()
    // W31.32: jcifs-ng 2.1.9 NTLM 认证内部用 MessageDigest.getInstance("MD4", "BC")
    // 算 NTLMv1 hash。Android 从 API 28 起内置 BC 被 strip 掉 MD2/MD4/RIPEMD160 等 legacy
    // 算法(理由:不安全),所以 Security.getProvider("BC") 拿到的是阉割版 BC,无 MD4。
    // 修复:remove 阉割版,add 我们 bcprov-jdk18on 1.78.1 全量 jar 的 BouncyCastleProvider。
    // 必须 remove 后再 add —— 否则 jcifs-ng 拿到的还是旧 BC provider 实例。
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.addProvider(BouncyCastleProvider())
    android.util.Log.i(
      "App",
      "W31.32 BC provider re-registered: " +
        "available MD4=${try { java.security.MessageDigest.getInstance("MD4") != null } catch (e: Throwable) { "missing ($e)" }}",
    )
    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
    // W31.23: 列当前 app 已 take 的持久 URI 权限,debug W31.22 tree grant 是否真持久化。
    val persisted = contentResolver.persistedUriPermissions
    android.util.Log.i("App", "W31.23 persistedUriPermissions count=${persisted.size}")
    persisted.forEachIndexed { idx, perm ->
      android.util.Log.i("App", "W31.23 persisted[$idx] uri=${perm.uri} read=${perm.isReadPermission} write=${perm.isWritePermission} persistedTime=${perm.persistedTime}")
    }
  }

  override fun onKoinStartup() = koinConfiguration {
    androidContext(this@App)
    modules(
      AppModule,
      PreferencesModule,
      DatabaseModule,
      FileManagerModule,
      ViewModelModule,
    )
  }
}
