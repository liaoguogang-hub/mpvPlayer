package live.mehiz.mpvkt

import android.app.Application
import androidx.annotation.Keep
import live.mehiz.mpvkt.di.AppModule
import live.mehiz.mpvkt.di.DatabaseModule
import live.mehiz.mpvkt.di.FileManagerModule
import live.mehiz.mpvkt.di.PreferencesModule
import live.mehiz.mpvkt.di.ViewModelModule
import live.mehiz.mpvkt.presentation.crash.CrashActivity
import live.mehiz.mpvkt.presentation.crash.GlobalExceptionHandler
import org.bouncycastle.crypto.digests.MD4Digest
import org.bouncycastle.crypto.engines.DESEngine
import org.bouncycastle.crypto.engines.RC4Engine
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
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
    keepBouncyCastleClasses()
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

  // W31.33: jcifs-ng 2.1.9 通过 java.security.SPI 反射加载 BC 类(MessageDigest.getInstance("MD4", "BC")
  // → BC 内部 DigestFactory → org.bouncycastle.crypto.digests.MD4Digest 类,ASN.1 编码走
  // org.bouncycastle.asn1.*)。R8 full mode 看不懂 SPI 反射,SHRINK 阶段会把没被静态引用的 BC 类
  // 砍掉(实测:seeds.txt 显示 BouncyCastleProvider/MD4Digest 在 keep 列表,但 ASN1ApplicationSpecific
  // 等类不在,被 R8 当未引用类删除 → 运行时 NoClassDefFoundError)。
  //
  // 修法:这里手动列已知 jcifs-ng NTLM / SPNEGO / SMB2 签名路径会用到的 BC 类,通过 Class 静态引用
  // 让 R8 在 SHRINK 阶段看到引用,把整棵 BC 子图 keep 住。@Keep 注解兜底防 obfuscation 改名。
  @Keep
  private fun keepBouncyCastleClasses() {
    val refs: Array<Class<*>> = arrayOf(
      // NTLM 哈希 + 加解密
      MD4Digest::class.java,
      org.bouncycastle.crypto.digests.MD5Digest::class.java,
      org.bouncycastle.crypto.digests.SHA1Digest::class.java,
      org.bouncycastle.crypto.digests.SHA256Digest::class.java,
      RC4Engine::class.java,
      DESEngine::class.java,
      HMac::class.java,
      KeyParameter::class.java,
      // ASN.1 编码(SMB2 signing / SPNEGO NegTokenInit / Kerberos 都要 ASN.1)
      org.bouncycastle.asn1.ASN1ApplicationSpecific::class.java,
      org.bouncycastle.asn1.ASN1ObjectIdentifier::class.java,
      org.bouncycastle.asn1.ASN1Encodable::class.java,
      org.bouncycastle.asn1.DERApplicationSpecific::class.java,
      org.bouncycastle.asn1.BERApplicationSpecific::class.java,
      org.bouncycastle.asn1.ASN1OctetString::class.java,
      org.bouncycastle.asn1.DERSequence::class.java,
      org.bouncycastle.asn1.ASN1Integer::class.java,
      // BC Provider 自己
      BouncyCastleProvider::class.java,
    )
    android.util.Log.i("App", "W31.33 BC class refs kept count=${refs.size}")
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
