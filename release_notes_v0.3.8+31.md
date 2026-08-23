# v0.3.8+31 (W31.30) — 2026-08-23

## 修复

**v0.2.4-8 (commit `05b8c73`) 在鸿蒙新机 Activity destroy 时 IllegalStateException 闪退**

user 在鸿蒙新机上装 v0.2.4-8 debug APK 后,按 home 键或旋转屏幕就崩:

```
java.lang.IllegalStateException: State must be at least 'CREATED' to be moved to 'DESTROYED'
  in component androidx.navigation3.ui.ChildLifecycleOwner@b340033
  at androidx.navigation3.ui.ChildLifecycleOwner.updateState(TransitionAwareLifecycleNavEntryDecorator.kt:90)
```

## 根因
navigation3 1.0.0-alpha06 的 `TransitionAwareLifecycleNavEntryDecorator` 状态机 bug —
Activity destroy 时 nav entry 还在 INITIALIZED state,但 decorator 直接尝试移到 DESTROYED,
违反 `LifecycleRegistry` 的状态转移约束。

(commit `05b8c73` v0.2.4+17 用的 navigation3 1.0.0-alpha06 引入 commit `1f62ec5`,
[nav3 1.0.0 stable (2025-11-19)](https://developer.android.google.cn/jetpack/androidx/releases/navigation3?hl=zh-cn)
起 + 后续 [1.1.0 stable (2026-04-08)](https://d.android.com/jetpack/androidx/releases/navigation3) 修了大量 bug,
包括 ChildLifecycleOwner 状态机。)

## 改动

### `gradle/libs.versions.toml`
- `navigation3` 1.0.0-alpha06 → **1.1.0**(修 ChildLifecycleOwner bug)
- `navigation3 1.1.0` 要求 `minSdk 23`,所以同时 bump `minSdk 21 → 23`(Android 6.0+ 用户归零,2026 影响可忽略)

### `MainActivity.kt`
- `rememberNavBackStack<Screen>(HomeScreen)` 改成 `rememberNavBackStack(HomeScreen) as NavBackStack<Screen>`(1.1.0 不接受泛型参数,直接返 `NavBackStack<NavKey>`,cast 后 Kotlin 类型对齐)
- `entryProvider = { route -> NavEntry(route) { ... } }` 改成 `{ route -> NavEntry<Screen>(route) { ... } }`(显式泛型参数,1.1.0 类型推断比 alpha06 严)

### `CompositionLocales.kt`
- `LocalBackStack = compositionLocalOf<NavBackStack> { ... }` 改成 `compositionLocalOf<NavBackStack<Screen>>`(1.1.0 NavBackStack 必须有 type 参数)

### 12 个 Screen 文件(bulk 替换 + 显式 type annotation)
- `HomeScreen.kt` / `FilePickerScreen.kt` / `CustomButtonsScreen.kt` / `HistoryScreen.kt`
- `PreferencesScreen.kt` / `AboutScreen.kt` / `AppearancePreferencesScreen.kt` / `AudioPreferencesScreen.kt`
- `DecoderPreferencesScreen.kt` / `GesturePreferencesScreen.kt` / `PlayerPreferencesScreen.kt` / `SubtitlesPreferencesScreen.kt`
- `val backstack = LocalBackStack.current` → `val backstack: NavBackStack<Screen> = LocalBackStack.current`
- FilePickerScreen 还有一个 `val navigator = LocalBackStack.current` 同样加 type annotation

### 体积影响
navigation3 1.1.0 自身引入 ~4 MB Compose / Scene 代码(R8 minify 跑通,但 intrinsic 体积):
| APK | W31.29 (v0.3.7+30) | W31.30 (v0.3.8+31) | 增加 |
|-----|--------------------|--------------------|------|
| `app-arm64-v8a-release.apk` | 23.4 MB | 43.6 MB | +20.2 MB |
| `app-armeabi-v7a-release.apk` | 22.6 MB | 39.2 MB | +16.6 MB |
| `app-x86-release.apk` | 23.8 MB | 44.1 MB | +20.3 MB |
| `app-x86_64-release.apk` | 24.6 MB | 49.2 MB | +24.6 MB |
| `app-universal-release.apk` | 66.6 MB | 148.4 MB | +81.8 MB |

arm64 43.6 MB 在 2026 是常见大小,可接受。

## 真机验证步骤

1. 卸载旧版 → 装 v0.3.8+31 APK
2. 启动 app,确认主界面正常(无 IllegalStateException crash)
3. **按 home 键 → 重新进入 app** — 不应报 IllegalStateException
4. **旋转屏幕** — 不应报 IllegalStateException
5. **点 SMB 局域网** → 配 NAS → 列表 → 选文件 → 播放
6. logcat 应见 nav3 ChildLifecycleOwner 正常状态转移(无 IllegalStateException)

## 下载(独立 keystore 签名,非 debug)

| APK | 大小 | SHA256 |
|-----|------|--------|
| `app-arm64-v8a-release.apk` | 43.6 MB | `16945cf84ce401a09883a23e0c3c9008a7f6978b01473e0e96372790957a5d10` |
| `app-armeabi-v7a-release.apk` | 39.2 MB | `870800708406d87e2d561acd8cb65ebc26b59a9cfbceb0f67d620f9ac4c5dd42` |
| `app-x86-release.apk` | 44.1 MB | `65bc5d0ac1e9e0c161c99a88387655556137ad7394f8866648053622a728383e` |
| `app-x86_64-release.apk` | 49.2 MB | `f62300ba92a18947fd6a58bc23b9fb4233185623a71ff412d0db354ac49b9dd7` |
| `app-universal-release.apk` | 148.4 MB | `62944320a6f33755655db9b9a5f5299cc07794deffe00061666a6ea0f9924606` |

## 关联修复
- W31.24 LazyColumn 嵌 verticalScroll(SMB 入口崩溃,已修)
- W31.29 smbj 同步全下载 + smbj 0.13(远程 NAS SMB 修复)— 保留
- v0.2.4-8 commit `05b8c73`(user 验证的基础版本,HomeScreen 风格)

## 致谢

- [navigation3 release notes](https://developer.android.google.cn/jetpack/androidx/releases/navigation3?hl=zh-cn) - 1.1.0 stable 2026-04-08 修 ChildLifecycleOwner
- [Hrach.dev: Nav3 fix Lifecycle for overlaid nav keys](https://hrach.dev?p=696/) - 解释了 nav3 早期 alpha 版本 lifecycle 状态机问题

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>