# v0.3.3+26 (W31.25) — 2026-08-23

## 修复

**远程 NAS(公网 IPv4 / DDNS / 高延迟 QNAP)SMB 播放报 `下载失败: DiskShare has already been closed`**

局域网 NAS 上不出现,远程 NAS 一播就报。鸿蒙 / 应用联网管控都解了也没用。

## 根因

W31.7 边下边播设计的 SMB share 生命周期:

```
phase 1 (32MB prebuffer):
  open connection + session
  share = connectShare("M")            ← 新 DiskShare
  smbFile = share.openFile(...)
  read 32MB
  smbFile.close()
  share.close()                        ← phase 1 结束立即关

phase 2 (后台续传,faststart MP4/MKV):
  open connection + session (复用)
  share = connectShare("M")            ← 第二次 connectShare 同一 share
  smbFile = share.openFile(...)
  read rest...
  smbFile.close()
  share.close()
```

远程 QNAP NAS 上:
- phase 1 关闭 share 后,QNAP SMB server 端 TreeConnect session 进入 idle 池
- phase 2 立刻 reconnectShare,smbj 内部 TreeConnect 状态机发现同一 session 短时间内
  反复 TreeConnect → 抛 `DiskShare has already been closed` (smbj 0.13 + SMB2 TreeConnect
  重入 bug)
- 局域网 smbj socket 一直热,reconnect OK 不踩

## 改动

### `W31SmbClient.kt` (W31.25 修)
- 删除原 `downloadPrebufferOnly` / `downloadRestOnly` 两个独立 helper
- `downloadForStreaming` 重写:share + smbFile 跨 phase 1 + phase 2 复用,不关就绪
- AtomicBoolean `closeGuard` 保证 share + smbFile 只 close 一次(无论 phase 1/2 完成 / 异常)
- phase 1 同步读 32MB(共享 smbFile)
- phase 2 后台(W31SmbDownloadScope)继续读剩余字节,同一 smbFile seek 不同 offset
- phase 2 跑完或抛错在 `finally { closeAllOnce() }` 关闭资源
- readRangeInto helper 注释更新,标明"phase 1 + phase 2 跨 coroutine 复用 smbFile,
  smbj 内部 seek+read 在同 socket 上是安全的(顺序 IO)"

### 副作用
- ✅ 局域网 SMB 播放不受影响(regression-free)— 同 share 同 smbFile,phase 1 跑完 phase 2
  接手,逻辑等价
- ✅ moov-at-end MP4 同步全量下载路径也用同一 share,行为一致
- ⚠️ 内存持有期延长:share 句柄在 phase 2 后台跑完才释放,但应用级 W31SmbDownloadScope
  立即触发,mpv 播完用户关 app → onDestroy 调 close() 时 smbj 自然清理,无内存泄漏

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.3+26 APK
2. 进 SMB 浏览器 → 选远程 NAS 上的视频(选个 1GB+ 的,确保 phase 1 + phase 2 都跑)
3. 期望:
   - 32MB prebuffer 立即开播(Phase 1)
   - 进度条继续 0% → 100%(Phase 2 后台)
   - 播放无中断
   - 无 `DiskShare has already been closed` 报错
4. cache 目录 `cache/smb/<server>/<share>/<path>` 应见完整文件大小

## 已知行为

- phase 1 失败(网络断 / 账密错 / NAS 端 share 不存在)会同步抛错,UI 显示 `下载失败: ...`
- phase 2 后台失败被吞(`catch (_: Throwable)`),UI 已 dismiss,后台静默续传失败不影响已开播播放
  (mpv 拿到 32MB 至少能播 26 秒,期间 phase 2 在后台补数据)

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 23.4 MB | `846077bf4c8de2794d9e499a942840b285af319b0a8527c416e95b7109d4597e` |
| `app-armeabi-v7a-release.apk` | 22.6 MB | `e4210f770172d42693bf9831f264251cdb25ed6f0b1213ed8eda2806512126b2` |
| `app-x86-release.apk` | 23.8 MB | `78afb95bd2c7d47e4f034b5d158d63e5446873b61c35eb2364a9b75e518c79ec` |
| `app-x86_64-release.apk` | 24.6 MB | `2e7dc99ac6a12663100d4ac03f45f6f1f4c5ae203e514c278695c24396416189` |
| `app-universal-release.apk` | 66.6 MB | `4cc7b2f1903fa73d83b41479d1b20e0df71f14c91b2738f559c6402fa4246c4d` |

推荐 arm64-v8a(主流手机 90%+ 都是 arm64),模拟器选 x86_64。

## 致谢

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>