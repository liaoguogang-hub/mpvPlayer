# v0.3.2+25 (W31.24) — 2026-08-22

## 修复

**点 SMB 局域网按钮,app 立刻崩到 CrashActivity**(IllegalStateException)。

## 根因

`HomeScreen` 根容器是 `Column(verticalScroll(rememberScrollState()))`(让历史卡片 + 打开按钮
组能上下滑)。`if (showSmb) { Surface { W31SmbBrowserScreen } }` 嵌在 verticalScroll Column 里,
`W31SmbBrowserScreen` 内部用 `LazyColumn` 列 share 下的目录/视频文件。

Compose 规则:**`LazyColumn` 不能嵌在有无限最大高度的容器里**(verticalScroll 父容器会给子节点
maxHeight=Infinity)。Compose 测量阶段抛:

```
java.lang.IllegalStateException: Vertically scrollable component was measured with an
infinity maximum height constraints, which is disallowed. ... nesting layouts like
LazyColumn and Column(Modifier.verticalScroll()).
```

`GlobalExceptionHandler` 拦截 → 启 CrashActivity。

为什么 v0.3.1+24 没测出来:SMB 入口这条路径在 release 前没真机点过(`memory mpvPlayer_W31_22_release.md`
等 release notes 都只测了 SAF / FilePicker / 历史播放这条主路径),用户那边日常用 NFS/SMB 直接走
系统文件管理器,这次刚好点了一下才发现。

## 改动

### `HomeScreen.kt` (W31.24 修)
- `Scaffold { padding -> Column(.verticalScroll) { ... } }` 改成 `Scaffold { padding ->
  Box(.fillMaxSize().padding(padding)) { Column(.verticalScroll) { ... }; if (showSmb) { ... } } }`
- SMB `Surface(W31SmbBrowserScreen)` 挪到 Box 的另一个子节点,**与 verticalScroll Column 平级**,
  Box 给 overlay 提供 fillMaxSize 的有限高度约束,内部 LazyColumn 拿到正常 maxHeight → 不崩
- `var showSmb by remember { mutableStateOf(false) }` 提升到 `Content()` 顶部,Box / Column
  两个子节点共享同一份 remember state(原位置只能被 inner Column 闭包捕获,Box overlay
  访问不到会编译错)
- `import androidx.compose.foundation.layout.Box`

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.2+25 APK
2. 点 "SMB 局域网" 按钮(底部,设置图标旁边)
3. 期望:不再崩,黑色 overlay 全屏显示,顶部状态条 `192.168.50.1/toshiba_ext`,
   文件列表出 NAS 根目录(.minidlna / Movie / 学习 / 杂项 / 纪录片 / 连续剧 / Books / Obsidian ...)
4. 点 "Movie" → 列出 MKV/MP4 文件(Eraser.1996.蒸发密令.双语字幕.mkv / Hamnet.mp4 / ...)
5. 选 1.91GB 的 `Eraser.1996.mkv` → 进度条 0→32MB → PlayerActivity 启动 + 横屏 + SurfaceView 渲染
6. cache 目录 `cache/smb/192.168.50.1/toshiba_ext/Movie/` 应见 178MB+ 持续增长(phase 2 后台续传)
7. adb logcat 整个流程无 `FATAL EXCEPTION` / `IllegalStateException`

## 已知行为

- SMB overlay 用的是 Scaffold body 区域(在 TopAppBar 之下),mpvPlayer 的 TopAppBar 文字会
  仍可见一截;视觉上 SMB 页面像全屏,实际是 body 区域 fillMaxSize,符合预期(避免侵入 Scaffold
  topBar 引发其他对齐问题)
- phase 2 后台下载挂在 `W31SmbDownloadScope`(应用级 CoroutineScope),即便用户关 SMB overlay
  也继续跑,把剩余字节追到 cache

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 23.4 MB | `46f01ef8a228b9d4b7f691ec0a76d60e3199fd6671575f6d694f4b66af353040` |
| `app-armeabi-v7a-release.apk` | 22.6 MB | `b01a33a97c05d5f802e672985468f9a52585f7d9771c91dae0cfa3793de8dee2` |
| `app-x86-release.apk` | 23.8 MB | `d864df69109b5c25275d034033e518b89b90ba816053e3c2e64061dd4c032f3f` |
| `app-x86_64-release.apk` | 24.6 MB | `7f743a766344ee9b2d39c802a9e30b1f815c5f8418c72c39b669c67f3ee65755` |
| `app-universal-release.apk` | 66.6 MB | `ecf9c26edbca361999fa0be50001ee7072cfffb5e2a85e292e7b597d555dd34c` |

推荐 arm64-v8a(主流手机 90%+ 都是 arm64),模拟器选 x86_64。

## 致谢

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>