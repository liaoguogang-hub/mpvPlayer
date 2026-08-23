# v0.3.7+30 (W31.29) — 2026-08-23

## 改动

**保留 SMB + 修 W31.7 引入的远程 NAS bug**

之前 W31.28 我误删 SMB。user 反馈后撤回 + 重新做 W31.29:保留 smbj 库和所有 SMB 代码,改 W31.29:
1. **W31SmbClient** 回到 **W31.5/W31.6 同步全下载**设计(删 W31.7 phase 1+2 / W31.25 share 复用 / W31.26 retry 包装)
2. **smbj 0.14 → 0.13 回退**(0.14 在华为新机 SMB 转圈连不上,user 验证 0.13 OK)
3. **SmbConfig.withTimeout(5s)** — 避免 SMB 入口转圈无限等(smbj 默认无限等,DNS 慢/SYN 丢包时挂起)
4. **W31SmbBrowserScreen** 改调 `downloadToCache`(同步全下载)而非 `downloadForStreaming`(W31.7 边下边播)
5. **删 W31SmbDownloadScope** — 应用级 CoroutineScope 不再需要(无 phase 2 后台续传)

### 没动的
- **HomeScreen** 仍是 W31.27 v0.2.4-8 风格(SAF document picker 主入口 + SAF tree picker FilePickerScreen + SMB 入口 + URL + 历史 Top 5 + W31.24 Box 包两层)
- W31.18 SAF document picker takePersistableUriPermission
- W31.23 SAF tree picker takePersistableUriPermission(READ|WRITE)
- W31.15 历史播放
- W31.20 FilePickerScreen 长文件名分两行
- smbj library + 所有 SMB 文件

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.7+30 APK
2. 启动 app,主界面入口组(从下往上):
   - **选择文件** OutlinedButton FileOpen  ← SAF document picker(主, W31.27)
   - **浏览视频文件** OutlinedButton FolderOpen ← SAF tree picker → FilePickerScreen(主)
   - **打开 URL** 输入框 + Button
   - **最近播放 Top 5**(如有)
   - **SMB 局域网** OutlinedButton Storage ← W31.29 保留,改了底层
3. **点 SMB 局域网** → 输入 www.leoliao.cc.cd / 445 / M / admin / 密码 → 列文件 → 选 1GB+ 文件
4. 期望:
   - 同步下载进度条 0→100%(1GB 文件 100Mbps 约 80s,比 W31.25 phase 1+2 慢但稳定)
   - 下载完成启动 mpv 播放
   - cache 目录 `cache/smb/192.168.50.1/M/电影/碟中谍.MP4` 等
   - **无 DiskShare has already been closed 报错**
   - **不再转圈**(5s connect timeout 兜底)
5. logcat 应见 `W31SmbClient` 没异常

## 已知行为

- 1GB 文件 100Mbps 局域网约 80s 同步下载(v0.2.4-8 时代的体验,但稳定)
- W31.7 引入的 32MB prebuffer 边下边播优化**不再有** — stability 优先
- 鸿蒙"风险应用管控" / "应用联网管控" 仍需用户手动信任

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 23.4 MB | `f00f6082519730e295978b85de7272b694b84cfab6241ac80d5ffb605cbfa410` |
| `app-armeabi-v7a-release.apk` | 22.6 MB | `dc8ce7605c50c946037939532c973c60e57cf6551d63d8fa57b47a85e5a28e59` |
| `app-x86-release.apk` | 23.8 MB | `465e0bfc9e74e392f3dcaa29d58e286f9efaaa320105e810f06d3232d1d6a338` |
| `app-x86_64-release.apk` | 24.6 MB | `e7c8cf95570f835abf995f776c376a7d902bfc03cb1b5c970c72c8dc5cbfcf62` |
| `app-universal-release.apk` | 66.6 MB | `07a1ccbf021b081d03f624eba8a86a12487bde8bb1eba4d7d8b0480f53f63009` |

arm64 23.4 MB(smbj 0.13 比 0.14 略小)。

## 致谢

- v0.2.4-8 (commit `4e90d5d` by AbdallahMehiz 2024-09-01) HomeScreen 入口设计
- smbj 0.13 + SmbConfig builder API
- W31.5/W31.6 同步全下载设计(在 git history 中)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>