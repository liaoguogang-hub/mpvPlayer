# v0.3.5+28 (W31.27) — 2026-08-23

## 改动

**`HomeScreen.kt`**:把系统 SAF document picker 从 TextButton 高级入口 **提升为 OutlinedButton 主入口**(FileOpen icon + `home_pick_file`)。文件管理器选本地文件时走系统 DocumentsUI,跟 v0.2.4-8 风格一致。

```
v0.3.5+28 HomeScreen 入口组:
├─ [URL 输入框 + 打开 URL 按钮]
├─ [浏览视频文件] OutlinedButton FolderOpen  ← FilePickerScreen(内置,保留)
├─ [选择文件] OutlinedButton FileOpen       ← SAF document picker(提升,本版本新)
├─ [SMB 局域网] OutlinedButton Storage
+ [历史播放 Top 5]
```

## 没改动的

- **W31.26 smbj 0.14 升级 + retry 包装保留** — 但 **W31.26 在华为新机装了后 SMB 转圈连不上**,user 报告这个事实,本次发版不修 SMB。
- FilePickerScreen 内置浏览保留(FileManager fromUri + 自渲染列表)— 在 v0.2.4-8 时代就有的"浏览文件夹"功能。
- SAF tree picker(OpenDocumentTree)仍走 FilePickerScreen,W31.23 takePersistableUriPermission 保留。
- W31.18 takePersistableUriPermission 文档 picker 保留。
- W31.24 LazyColumn 嵌 verticalScroll 修复保留。

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.5+28 APK
2. 启动 app,看主界面入口组
3. **新主入口"选择文件"(FileOpen icon)** 点击 → 弹系统 SAF document picker → 选个本地 mp4 / mkv → 应能正常播
4. 旧入口"浏览视频文件"(FolderOpen icon)— 仍走 FilePickerScreen,行为不变
5. SMB 入口仍存在,但**已知在 v0.3.5+28(W31.26 smbj 0.14)上转圈**;用户说在 v0.3.3+26 (W31.25, smbj 0.13) 上 SMB OK,**回退 smbj 0.13 待 W31.28 单独修**

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 23.4 MB | `65bd9785c74425288982623208574159ceb957450ad43351c3333b0b93d7012e` |
| `app-armeabi-v7a-release.apk` | 22.6 MB | `02d62dcd53c88058b656de91887e9f3b4b16efae28cba1224c488a052ab5e8a4` |
| `app-x86-release.apk` | 23.8 MB | `8300301b4a6aa5039b31ff134ca428ee700eccc578be756ca2b9536f2c0a4662` |
| `app-x86_64-release.apk` | 24.6 MB | `4c25b2803f4a7f74e897362296991c47afbef7a83e6d3597ccb525d2f0811081` |
| `app-universal-release.apk` | 66.6 MB | `36268b3236c5b6f70c997cb87231552e6a0484575079f73ce367c08de363ce1f` |

## 致谢

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>