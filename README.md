# SPlayer for Android

一个面向 Android 手机和平板的 SPlayer 移植版，基于 `Vue 3 + Naive UI + Capacitor`，仅保留移动端构建链路与移动端运行所需源码。

当前版本：`3.0.0-dev.1`

## 项目说明

- 平台：仅支持 `Android 11.0+`
- 形态：优先适配平板，其次适配手机
- 技术栈：`Vue 3`、`TypeScript`、`Naive UI`、`Capacitor`
- 本地 API：内置 `nodejs-mobile-cordova`，默认监听 `127.0.0.1:1145`
- 应用名：`SPlayer for Android`

## 当前保留内容

- Android 壳工程 `android/`
- 移动端前端源码 `src/`
- Android 内置 API 源码 `API/`
- Android 构建脚本 `scripts/build-android-node.ts`、`scripts/prepare-android-embedded.ts`
- Capacitor / Vite 构建配置

桌面端 Electron 主进程、桌面打包配置、桌面歌词页面和文档目录已从当前仓库裁掉，仓库目标明确为 Android 版本。

## 播放方案

Android 端默认使用 `HTMLAudioElement` 直连播放，而不是桌面端那套完整 Web Audio 图。

这样做的原因：

- 更适合 Android WebView
- 后台播放更稳定
- 更容易控制功耗和卡顿

当前取舍：

- `equalizer` 不启用
- `spectrum` 不启用
- `setSinkId` 不启用

相关实现：

- `src/core/audio-player/AudioElementPlayer.ts`
- `src/core/audio-player/BaseAudioPlayer.ts`

## 构建方式

1. 安装依赖

```bash
pnpm install
```

2. 构建 Web 资源并同步 Android 工程

```bash
pnpm build:android
```

3. 生成调试 APK

```bash
cd android
./gradlew assembleDebug
```

生成产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Android Studio

1. 打开 Android 工程

```bash
npx cap open android
```

2. 等待 Gradle 同步完成
3. 选择真机或模拟器
4. 运行 `app`
5. 或使用 `Build > Build APK(s)`

## 致谢

本项目基于原项目 [SPlayer](https://github.com/imsyy/SPlayer) 进行 Android 方向迁移与裁剪，感谢原作者和贡献者提供的设计、功能与代码基础。

## 许可证

遵循上游项目许可证：`AGPL-3.0`
