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

## 下载

- `app-arm64-v8a-release.apk`(推荐,52 MB)
- `app-universal-release.apk`(全架构,101 MB)
- SHA256 见 GitHub release 页面

## 致谢

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>