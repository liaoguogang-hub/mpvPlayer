v0.2.5+18 (2026-08-09)

新增功能 / 改进

- W31.5: 字幕库搜索 (字幕库网, 手动过 Cloudflare + 下载拦截 + 自动解压 + 自动加载)
- W31.5: SMB 局域网视频播放 (smbj 0.13 pure-Java SMB1/2/3 client)
- W31.6: SMB 缓存下载进度条 (实时字节数 + 百分比)
- W31.7: SMB 边下边播 (32MB prebuffer + 后台续传到同一文件)
- W31.12: 换 PlexiPlay 风格图标 (蓝紫渐变 #4A3AC4 → #AC4FC6 + 大白 play 三角)
- W31.14: 出 release APK (R8/ProGuard 混淆 + debug keystore 签 release)

Bug 修复

- W31.11: SMB 视频 "无法播放" 根因 — PlayerUtils.resolveUri 不认 null scheme,
  SMB 缓存的绝对路径 Uri 没 scheme 掉进 else -> null 分支 → getPlayableUri 返 null
  → player::playFile 永不调用。修: 加 null -> path 分支

技术栈

- Kotlin + Jetpack Compose
- libmpv (mpv-android build)
- minSdk 21 / targetSdk 36
- 适配 arm64-v8a / armeabi-v7a / x86 / x86_64

安装

- release APK (arm64-v8a, 23 MB) — 推荐
- release APK (universal, 67 MB) — 全架构
- 源码: https://github.com/liaoguogang-hub/mpvPlayer

注意

- release APK 用 debug keystore 签名 (个人 fork,真要分发前再换独立 keystore)
- R8 启用了,但 -dontwarn 屏蔽了可选依赖的 missing class (xz/7z/javax.el/jgss)
- 已真机验证 launch + UI 启动,完整 SMB/字幕功能跑通请参考 commit 历史