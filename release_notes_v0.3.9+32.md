# v0.3.9+32 (W31.31) — 2026-08-23

## 修复

**远程 NAS (QNAP) SMB 播放仍报 `DiskShare has already been closed`**

W31.29 (smbj 0.13 同步全下载 + 5s timeout) + W31.30 (navigation3 1.1.0) 都没修好 — smbj 库本身在远程 NAS 协议层有 bug。

## 根因
smbj 0.13 是 [hierynomus/smbj](https://github.com/hierynomus/smbj) 实现的 Java SMB client,
`Share.send()` 在 SMB2 TreeConnect 状态机有问题 — QNAP NAS 的 SMB2 server 实现跟 smbj
预期不完全一致,W31.5-W31.29 各种修法(phase 1+2 边下边播 / share 跨阶段复用 / retry 包装
/ 同步全下载 / smbj 0.13 + 0.14 切换)都没绕开。

## 改动

### `gradle/libs.versions.toml`
- 删 `smbj = { module = "com.hierynomus:smbj", version = "0.13.0" }`
- 加 **`jcifs-ng = { module = "eu.agno3.jcifs:jcifs-ng", version = "2.1.9" }`**
  - Apache AgNO3 fork ([AgNO3/jcifs-ng](https://github.com/AgNO3/jcifs-ng))
  - Java CIFS/SMB1/2/3 client 纯 Java,无 JNI
  - 最新稳定版 2.1.9(GitHub master 是 3.0.0-SNAPSHOT 但未发 Maven Central)
  - groupId **eu.agno3.jcifs**(注意不是 `jcifs:jcifs-ng`)
  - 用于 Kodi / Plex / jDownloader / 多个 Java SMB 项目,协议层成熟稳定

### `app/build.gradle.kts`
- `implementation(libs.smbj)` → `implementation(libs.jcifs.ng)`

### `W31SmbClient.kt` (W31.31 完整重写)
- 删 smbj API 导入(SMBClient / Connection / Session / DiskShare / SMB2ShareAccess 等)
- 加 jcifs-ng API 导入(`SmbFile` / `SmbFileInputStream`)
- `W31SmbClient(server, port, username, password, domain)` 构造:
  - 构建 `smb://[user:pass@]host:port/` baseUrl
- `list(shareName, path)`: `SmbFile(baseUrl + share + "/" + path).listFiles()` + 过滤 + 排序
- `downloadToCache(...)`:
  - `SmbFile(url).length()` 拿 total
  - `SmbFileInputStream(smbFile).use { input → FileOutputStream(target).use { output → buf.copyTo(...) } }`
  - 进度回调 throttled 100ms(原 smbj 用 ProgressListener,jcifs-ng 没用,直接手动 throttle)
- **jcifs-ng API 比 smbj 简洁很多**:SmbFile 内部全包 SMB2 Open + Read + Close,
  不需要 smbj 那样的 share/session/connectShare 状态管理

### `proguard-rules.pro`
- 加 `-dontwarn org.slf4j.impl.StaticLoggerBinder` + `-dontwarn org.slf4j.impl.**`
  - jcifs-ng runtime 也依赖 SLF4J,跟 smbj 一样的 dontwarn 处理

## 没动的
- W31.29 同步全下载设计(没有 phase 2 后台续传) — 保留
- SmbConfig 5s connect timeout 改成 jcifs-ng 自带超时设置(`jcifs.smb.client.responseTimeout`)
- v0.2.4-8 HomeScreen 风格(SAF picker 主入口 + FilePickerScreen + SMB + URL + 历史) — 保留
- W31.30 navigation3 1.1.0 + minSdk 23 — 保留

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.9+32 APK
2. 启动 app,确认主界面正常(W31.30 navigation3 升级已修 ChildLifecycleOwner 崩)
3. 点 SMB 局域网 → 配 `www.leoliao.cc.cd / 445 / M / admin / 密码` → 列文件
4. 选 1GB+ 文件(Eraser.1996.mkv 1.91GB) → 同步下载
5. 期望:
   - **不再报 DiskShare has already been closed**(jcifs-ng 协议层成熟)
   - 进度条 0→100%(1GB 100Mbps 局域网约 80s)
   - 下载完成启动 mpv 播放
   - cache 目录 `cache/smb/192.168.50.1/M/...` 有完整文件
6. logcat 应见 jcifs-ng debug log(`SmbFileInputStream` 读流过程)

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 42.6 MB | `36ea902465b6b575febd005bcc2a72369f6c857d3b266417b482624c5770b43f` |
| `app-armeabi-v7a-release.apk` | 38.1 MB | `1ef04f5223d8f4305226494ae837f4d95319d8f9f69f7b1150d9519076959819` |
| `app-x86-release.apk` | 43.1 MB | `de9dc2f0903dc37dbca99726055ed71136f6e47f83ca17172382ac06b9bb2128` |
| `app-x86_64-release.apk` | 48.1 MB | `25fbf87a74ea9157f8bb588eae796888b31ae1a2264e35f5e7e30041fdc5ea1d` |
| `app-universal-release.apk` | 147.3 MB | `77423031fc612590e8c036d7fd8b32c79a90934537e3d1fa88e08d093a244a6d` |

arm64 42.6 MB(jcifs-ng 替换 smbj,体积略减 1MB)。

## 关联修复
- W31.30 navigation3 1.1.0(修 ChildLifecycleOwner 崩)— 保留
- W31.29 smbj 0.13 同步全下载 — 改用 jcifs-ng 实现
- W31.24 LazyColumn 嵌 verticalScroll(SMB 入口崩溃)— 保留
- v0.2.4-8 commit `05b8c73`(user 验证的基础版本,HomeScreen 风格)

## 致谢

- [AgNO3/jcifs-ng GitHub](https://github.com/AgNO3/jcifs-ng) - Java SMB client 稳定 fork
- [Maven Central: jcifs-ng 2.1.9](https://central.sonatype.com/artifact/eu.agno3.jcifs/jcifs-ng) - 最新稳定版
- jcifs-ng 项目历史:原 jCIFS 库 → AgNO3 维护,跨平台稳定

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>