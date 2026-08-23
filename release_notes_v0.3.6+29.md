# v0.3.6+29 (W31.28) — 2026-08-23

## 改动(大重构)

**完全删除 W31.5-W31.27 自建 smbj SMB 客户端 + 回到 v0.2.4-8 风格的 HomeScreen 入口**

NAS 文件播放回到 mpv 内置 streaming + Android 系统 / 第三方 mount app 把 SMB 挂到本地 → SAF picker 直接选 → mpv 通过 ContentResolver 流式读(mpv 内置 demuxer cache 边下边播)。完全不需要我们自建 SMB 客户端。

### 删的
- `app/src/main/java/live/mehiz/mpvkt/ui/smb/W31SmbClient.kt`
- `app/src/main/java/live/mehiz/mpvkt/ui/smb/W31SmbBrowserScreen.kt`
- `app/src/main/java/live/mehiz/mpvkt/ui/smb/W31SmbServerDialog.kt`
- `app/src/main/java/live/mehiz/mpvkt/ui/smb/W31SmbServerPreferences.kt`
- `app/src/main/java/live/mehiz/mpvkt/ui/smb/W31SmbDownloadScope.kt`
- `smbj` 依赖(0.13 → 0.14 → 删)
- HomeScreen 的 "SMB 局域网" 按钮 + `showSmb` state + `if (showSmb)` overlay
- proguard `org.ietf.jgss.**` dontwarn(smbj Kerberos 才有)
- 加 proguard `org.slf4j.impl.**` dontwarn(R8 仍报 smbj 内部的 slf4j LoggerFactory.bind())

### 保留的
- **SAF document picker 主入口**(FileOpen icon, `home_pick_file`)— v0.2.4-8 风格 + W31.18 `takePersistableUriPermission`
- **SAF tree picker → FilePickerScreen 主入口**(FolderOpen icon, `home_browse_files`)— v0.2.4-8 风格 + W31.23 `takePersistableUriPermission(READ|WRITE)` + W31.20 FilePickerScreen 长文件名分两行
- **URL 输入 +打开 URL Button**(`home_open_url` + `isURLValid` PROTOCOLS 检查)
- **历史播放 Top 5**(W31.15)— `history.take(5).forEach { RecentHistoryCard }`
- W31.24 LazyColumn 嵌 verticalScroll 修复保留(Box 包两层)— 防止将来加 LazyColumn 翻车
- FSAF(Fuck-Storage-Access-Framework 1.1.3)— 桥接 SAF + 自定义协议 provider

### 没改的
- `mpv-android-lib:0.1.9` prebuilt .so(mpv 内置 demuxer cache,默认 `demuxer-cache-time` 几秒)
- 所有 preferences / player / theme / utils / database / history 代码

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.6+29 APK
2. 启动 app,主界面入口组(从下往上):
   - **选择文件** OutlinedButton FileOpen  ← SAF document picker(主)
   - **浏览视频文件** OutlinedButton FolderOpen ← SAF tree picker → FilePickerScreen(主)
   - **打开 URL** 输入框 + Button
   - **最近播放 Top 5**(如有)
   - **设置** 图标(右上)
3. **本地 / 已挂载 NAS 文件**:点"选择文件"或"浏览视频文件"→ SAF picker 选文件 → mpv 流式播(mpv 内置 demuxer cache 边下边播)
4. **远程 NAS 文件**:Android 系统 mount SMB 或第三方 mount app 把 SMB 挂到本地 → SAF picker 选 → mpv 流式播
5. cache 大小应**不再增长**(不再有 smb cache),mpv 通过 ContentResolver 直接读源文件

## 已知行为

- **NAS 文件访问需要 Android 系统 / 第三方 mount app 挂 SMB** — mpvKt 不再自建 SMB 客户端
- v0.3.5+28 (W31.27) 测过的"选择文件" 入口在 v0.3.6+29 仍存在,行为相同
- v0.3.6+29 没有"SMB 局域网"按钮了

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 22.1 MB | `8371349348ca0092e2e0365d67c0144989d3462ae5fe7dff2bbd73d4c534408e` |
| `app-armeabi-v7a-release.apk` | 21.3 MB | `370f8409ae974e7b77a10e2268c998a2aae18aceccee7269718e586e2b627957` |
| `app-x86-release.apk` | 22.5 MB | `3f8c49f5c5067a3e6cb9302b9c15f91d403fd8cb4e7debe13a0aceb26c50d343` |
| `app-x86_64-release.apk` | 23.3 MB | `d0bd72dc43b54489430d69447838d25e689182f84ac0ae58c1af482a4c3d3e41` |
| `app-universal-release.apk` | 65.3 MB | `f10df8558b7325f097d6d42ea1770bf9df63c4b6fb8462f6f33fed784bd6e641` |

arm64 比之前小 1.3 MB(smbj 0.14 删了)。

## 致谢

- [mpv-android](https://github.com/mpv-android/mpv-android) upstream `is.xyz.mpv` 风格
- v0.2.4-8 (commit `4e90d5d` by AbdallahMehiz 2024-09-01) HomeScreen 入口设计参考
- mpv 内置 `demuxer-cache-time` 边下边播能力
- [Fuck-Storage-Access-Framework (FSAF)](https://github.com/K1rakishou/Fuck-Storage-Access-Framework) SAF + 自定义协议 provider 桥

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>