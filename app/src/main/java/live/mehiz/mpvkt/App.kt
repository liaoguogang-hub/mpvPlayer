package live.mehiz.mpvkt

import android.app.Application
import live.mehiz.mpvkt.di.AppModule
import live.mehiz.mpvkt.di.DatabaseModule
import live.mehiz.mpvkt.di.FileManagerModule
import live.mehiz.mpvkt.di.PreferencesModule
import live.mehiz.mpvkt.di.ViewModelModule
import live.mehiz.mpvkt.presentation.crash.CrashActivity
import live.mehiz.mpvkt.presentation.crash.GlobalExceptionHandler
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

@OptIn(KoinExperimentalAPI::class)
class App : Application(), KoinStartup {
  override fun onCreate() {
    super.onCreate()
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
