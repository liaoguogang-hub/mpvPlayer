# v0.3.1+24 (W31.23) — 2026-08-16

## 修复

**`浏览视频文件 → 使用此文件夹` 后,历史记录里视频文件过一段时间就 fail no longer access 并被删除**

根因:`OpenDocumentTree()` 返回的 `treeUri` 没调 `takePersistableUriPermission`,默认 grant 跟 Activity 走,
重启 app / 系统 LRU 清理后失效 → 子文件 content URI 跟着失效 → 点历史 → `openFileDescriptor` 抛
`SecurityException` → W31.18 的 `isPlayable` 预检返回 false → Toast → `deleteByUri`。

## 改动

### `HomeScreen.kt` directoryPicker (主修)
- `OpenDocumentTree()` 回调里立刻 `takePersistableUriPermission(treeUri, READ|WRITE)`
- flag 从 `READ` 单 flag 改 `READ|WRITE`(部分 ROM DocumentsUI 不接受只 READ)
- catch 从 `SecurityException` 改 `Exception`(防 `IllegalArgumentException` 被吞)
- 成功路径 `Log.i "W31.23 takePersistableUriPermission OK tree=... flags=..."`
- 失败路径带异常类型名方便现场诊断

### `FilePickerScreen.kt` onNavigate 单文件分支 (防御性兜底)
- 选中单文件时也对 child URI `takePersistableUriPermission`,tree grant 偶发回收时仍可访问
- tree 派生的 child URI 单独 take 某些 provider 会抛 SecurityException,`runCatching` 吞掉不影响主流程

### `App.kt` onCreate (debug 辅助)
- 启动时打印 `persistedUriPermissions` 列表(数量 + URI + read/write/persistedTime)
- 验证 take 是否真持久化进 Android 系统

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.1+24 APK
2. 开 app → logcat 第一行应见 `App: W31.23 persistedUriPermissions count=0`(首次)
3. 点"浏览视频文件" → 选个目录 → "使用此文件夹"
4. logcat 应见 `HomeScreen: W31.23 takePersistableUriPermission OK tree=content://... flags=...`(没 FAILED)
5. 选视频播放 → 进历史 → `adb shell am force-stop live.mehiz.mpvkt`
6. 重启 app → logcat `persistedUriPermissions count=1+` → 点历史里那条 → 不再 fail

## 已知行为

**老的失效条目无法救回**(W31.23 之前的 take 没成功,grant 早被系统回收)。这些条目会按 W31.18 的逻辑
继续"点开就 fail → 删条目",用户可手动长按删除。

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 24.5 MB | `aa23769184cdd8b0c2bfacd88c79fb243edf1223e95a473518b28d89ca4f4584` |
| `app-armeabi-v7a-release.apk` | 23.7 MB | `3231e1afe1e797d0ed14e33195756c1af7e8a68cb51a86d2f41757c1e2928a6a` |
| `app-x86-release.apk` | 24.9 MB | `f0f2a5037da3eeb233d3ca2b0cfc1d1b7d78675a9e0ab0b4b33acf7de477d0ca` |
| `app-x86_64-release.apk` | 25.8 MB | `439606827172fd447c8fc2a73f766c926e9e3461b06239edf58bc5f4f5e62e4d` |
| `app-universal-release.apk` | 69.8 MB | `2654e6ff002474c2eff5ddd46438391833063cb069d360248c8cf6832942b5e7` |

推荐 arm64-v8a(主流手机 90%+ 都是 arm64),模拟器选 x86_64。

## 致谢

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>