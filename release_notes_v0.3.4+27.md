# v0.3.4+27 (W31.26) — 2026-08-23

## 修复

**远程 NAS(公网 IPv4 + DDNS + 高延迟 QNAP)SMB 播放仍报 `下载失败: DiskShare has already been closed`**(v0.3.3+26 W31.25 没修对,W31.25 只修了 phase 1 close → phase 2 reconnectShare,但 phase 1 自己读 32MB 过程中就崩了)

## 根因(二次定位)

WebSearch 调研 smbj GitHub issue + 各种 stack trace 报告,典型成因:

| # | 根因 | 触发 |
|---|------|------|
| 1 | 网络抖动导致 SMB2 Read 响应丢失 | smbj 0.13 内部 state machine 标记 share 为 closed |
| 2 | QNAP 端 idle timeout 关 share | 高延迟公网传输期间超过 server 端空闲阈值 |
| 3 | SMB2 Transport EOF | TCP RST / server 进程重启 / 路由器 NAT idle drop |

这些都是**瞬时错误**,理论上**retry 应该能恢复**。smbj 0.13 自己不重试,业务层必须做。

W31.25 我搞错方向 — 以为是 phase 1 close → phase 2 reconnectShare 的状态机 bug,实际**phase 1 第一次读就崩**。W31.26 加外层 retry。

## 改动

### `gradle/libs.versions.toml` (W31.26)
- `smbj` 0.13.0 → **0.14.0**(2024-12-09 发布,包含 SMB2 TreeConnect + Transport 重试相关 bug fix)

### `W31SmbClient.kt` (W31.26)
- `downloadForStreaming` 加外层 retry loop,**最多 3 次**,每次失败:
  - `closeAllOnce()` 关闭失败的 share + smbFile
  - `delay(500 * attempt)` 指数 backoff(500/1000/1500ms)
  - 重新 `connectShare` + 重新 `openFile` + 重新读 32MB
- 可重试异常识别(`isRetryableSmbError`):
  - `SMBRuntimeException` with message 含 `"has already been closed"` / `"transport"` / `"EOF"` / `"connection reset"`
  - `TransportException`(smbj 0.14 单独 catch)
- 3 次都失败才原样抛(给 UI 显示)
- 新增 companion 常量 `MAX_RECONNECT_ATTEMPTS = 3` / `RETRY_DELAY_MS = 500L`
- 文档注释大改,标明 W31.26 三层 retry 策略

### `W31.25 share 跨阶段复用` 保留
- phase 1 retry 成功后,phase 2 后台仍复用 phase 1 的 share + smbFile(W31.25 修法)
- phase 2 后台续传失败 best-effort catch,不影响已开播播放

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.4+27 APK
2. 进 SMB 浏览器 → 远程 NAS(`/电影/碟中谍` 或类似)→ 选 1GB+ 视频
3. **期望**:phase 1 第一次失败 → 自动 retry 1~2 次(每次 retry 间隔 500ms~1.5s)→ phase 1 成功 → 32MB prebuffer → PlayerActivity 启动 → 进度条持续增长
4. logcat 应见 `W31SmbClient: retry attempt=N` 之类标记(W31.27 加,本次还没有日志)
5. cache 目录 `cache/smb/<server>/<share>/<path>` 见完整文件大小

## 已知行为

- phase 1 第一次失败时,UI 进度条会先卡住 500~1500ms(retry backoff),然后恢复
- phase 1 三次都失败,UI 显示 `下载失败: DiskShare has already been closed`(跟之前一样),retry 也救不回来的话需要查 NAS 端
- 局域网 SMB 播放应该完全无感(retry 第一发就过)

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 23.4 MB | `585d4343a3be24d7f0b2b2fc99baa142d74dee1b09bf606517f7695bf4b86690` |
| `app-armeabi-v7a-release.apk` | 22.6 MB | `19a7406c65138829fe1ed4055e83e3dbed1f5fddfe23e96cc2e8923e03efc576` |
| `app-x86-release.apk` | 23.8 MB | `ef23aeb5f5b705c381350549164379dbf6b5b8fc549e040730fddc4dfafc3fb7` |
| `app-x86_64-release.apk` | 24.6 MB | `367b417b8d35f580a0ec6de3d75bbab733f9d65b03044f353cc275eb21c76e7e` |
| `app-universal-release.apk` | 66.6 MB | `725584ed0fe4637964967764fcff77bc34f1b8527e5912075f0bc89965258578` |

## 致谢

- [smbj issue #180 - DiskShare closed fail-fast](https://github.com/hierynomus/smbj/issues/180) 给了 fail-fast 检查触发机制
- [smbj issue #660 - PipeShare has already been closed](https://gitmemories.com/index.php/hierynomus/smbj/issues/660) 给了 DFS referral 异常路径参考
- [Nova Video Player SMB 异常分析](https://blog.gitcode.com/72f1532c1aeb939dfac7309c30504b76.html) 给了 retry + isConnected 检查的标准模式
- [smbj maven 0.14.0](https://mvnrepository.com/artifact/com.hierynomus/smbj/0.14.0) - W31.26 升级版本

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>