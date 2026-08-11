v0.3.0+23 (2026-08-11)

新增 / 改进

- W31.22: 独立 release keystore(告别 debug 签名)
  - 真因: W31.14 用 `signingConfigs.getByName("debug")` 签 release variant,
    CN=Android Debug → 华为每次启动都弹"不良应用"风控提示,用户体验差
  - 修法: 生成独立 release keystore (`D:/study/_keystores/mpvplayer-release.jks`),
    凭据放 `app/keystore.properties`(gitignored),build.gradle.kts 自动加载,
    keystore.properties 不存在时 fallback 到 debug(本地编译兜底)
  - 签名主体: CN=liaoguogang-hub, OU=Personal, O=mpvPlayer
  - keystore 信息已记到本地保险处,丢 keystore 不可恢复,建议备份到 1Password
    或类似工具
- 版本号 minor bump: 0.2.9 → 0.3.0(独立签名是个分水岭)

技术栈

- Kotlin + Jetpack Compose
- libmpv (mpv-android build)
- minSdk 21 / targetSdk 36
- PKCS12 keystore + SHA256withRSA + 2048-bit RSA
- Room 2.7 (Migration 5→6)
- FSAF (内置文件浏览)

安装

- release APK (arm64-v8a, 24 MB) — 推荐,新签名 CN=liaoguogang-hub
- release APK (universal, 67 MB) — 全架构,新签名
- 源码: https://github.com/liaoguogang-hub/mpvPlayer

注意

- debug APK (applicationId=`.debug`) 继续用 AGP 自带 debug keystore,跟 release
  共存,不冲突
- 旧 release APK (v0.2.9+22 用 debug 签名的) 不能再覆盖装,v0.3.0+23 是
  applicationId=live.mehiz.mpvkt 但签名变了的版本,首次安装需卸载旧版
- 真机 M4T0224612003168 已装新 release,启动后无华为风控提示