v0.2.8+21 (2026-08-10)

Bug 修复

- W31.18: 修历史播放点开 content URI 闪退 (SecurityException)
  - 真因: ACTION_OPEN_DOCUMENT 选文件的 grant 默认跟 Activity 走,
    重启 app 后 content URI 失效。saveToHistory 把这个 URI 存进 Room,
    下次从历史记录点开 → PlayerActivity.onCreate → openFileDescriptor
    抛 SecurityException,Activity 异步崩在进程内,HomeScreen/HistoryScreen
    外层 try/catch 抓不到 → 闪退
  - 修法 1: 选文件回调立刻 takePersistableUriPermission (HomeScreen 主视频 +
    PlayerSheets 外挂字幕/音轨),让新选的 URI 重启后仍可访问
  - 修法 2: PlayerUtils.isPlayable(uri) 同步预检 content URI 权限,失效就
    Toast + deleteByUri (不再闪退,优雅降级)
  - 用户已失效的历史条目会在点开时被自动删除,无需手动清理

技术栈

- Kotlin + Jetpack Compose
- libmpv (mpv-android build)
- minSdk 21 / targetSdk 36
- Room 2.7 (Migration 5→6)

安装

- debug APK (arm64-v8a) — 已装 M4T0224612003168,等用户真机验证端到端:
  选文件 → 播完 → 重启 app → 点开历史 → 正常播放
- 源码: https://github.com/liaoguogang-hub/mpvPlayer

注意

- 历史记录里**已失效**的 URI 不会被新 APK 自动修复,点开时被识别后删除
  即可。建议测试前先清掉旧历史条目 (HistoryScreen 右上 deleteSweep 图标)