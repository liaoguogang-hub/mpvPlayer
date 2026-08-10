v0.2.9+22 (2026-08-11)

Bug 修复

- W31.19: 横屏历史播放无法下滑 + 打开视频左上角文件名错乱
  - 横屏下滑: HomeScreen 外层 Column 用 `verticalScroll(rememberScrollState())`,
    但 `verticalArrangement = Arrangement.Center` 跟 `verticalScroll` 冲突,
    滚动被禁掉,屏幕高 720dp 时历史 + 按钮超出可视区就看不到
  - 修法: 把 `verticalArrangement` 改成 `Arrangement.Top`,verticalScroll 才生效
  - 左上角文件名错乱: MPV_EVENT_FILE_LOADED 时 `setPropertyString("media-title",
    fileName)` 被 mpv 自己异步加载的 metadata 覆盖,S02E10 这种文件名就退化成
    "S02E10" 的数字,显示成 3 位数
  - 修法: 在 `loadfile` 之前 `setOptionString("force-media-title", fileName)`,
    file-local option 优先于 metadata,真正生效。PlayerActivity onCreate /
    onNewIntent 走同一逻辑

- W31.20: 系统 SAF picker "最近"列表单行文件名被截断
  - 真因: 系统 DocumentsUI 是 com.android.documentsui 包的,我们 hook 不了
  - 修法: 主入口从 OpenDocument (单文件 SAF) 改成 OpenDocumentTree → 自家
    FilePickerScreen (FSAF 内置 picker),FileListing 已经支持 maxLines=2 +
    文件大小 + 修改时间,长文件名直接完整显示
  - 系统 SAF picker 降级为 TextButton "系统 SAF picker (高级)",留给需要跨
    app 共享 / SD 卡的场景
  - strings: home_browse_files "浏览视频文件" / home_open_saf_picker
    "系统 SAF picker (高级)"

- W31.21: 内置 FilePickerScreen 长文件名仍截断
  - FileListing Column 之前没 `weight(1f)`,没占用 Row 剩余宽度,Text 自然
    单行省略
  - 修法: Column 加 `Modifier.weight(1f)` + Text `maxLines = 2, overflow =
    Ellipsis`。毒枭：墨西哥.Narcos.Mexico.S02E10.end.中英字幕.WEB.1080P-人人影视.mp4
    这种 30+ 字符的真人片名直接分两行完整显示

技术栈

- Kotlin + Jetpack Compose
- libmpv (mpv-android build)
- minSdk 21 / targetSdk 36
- Room 2.7 (Migration 5→6)
- FSAF (内置文件浏览)

安装

- debug APK (arm64-v8a) — 已装 M4T0224612003168,真机验证通过:
  - 横屏 HomeScreen 完整可滚
  - 选带 `S02E10.end` 的文件,左上角显示完整原文件名
  - FilePickerScreen 浏览列表长文件名分两行完整显示
- 源码: https://github.com/liaoguogang-hub/mpvPlayer

注意

- 沿用 W31.18 take 持久 URI 权限 + isPlayable 预检,历史点开不再闪退
- release APK 用 debug keystore 签名 (个人 fork)