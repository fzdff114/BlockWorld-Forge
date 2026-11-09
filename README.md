# Atlas

Android 原生地图与 Minecraft 基岩版世界制作工具。

**当前版本：0.1.2**

## 当前功能

- 原生 Android 界面（Kotlin + Jetpack Compose）
- 项目草稿与地图尺寸设置
- 本地文件选择入口
- 地形参数界面
- 群系、结构、地表素材分类
- 基岩版世界导出入口

## 当前限制

当前版本为界面原型。地图文件尚未解析，地形尚未生成，项目尚未持久化，`.mcworld` 尚未实际导出。素材分类仅为入口展示。

## 安装

在 [Actions](../../actions) 中打开最新的 **Android APK** 构建，下载 `Atlas-Android-debug`，解压后安装 `app-debug.apk`。

Debug 测试包，最低支持 Android 7.0（API 24），不依赖 Google Play 服务。

## 本地构建

使用 JDK 17 和 Android SDK 35：

```bash
gradle --no-daemon assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`
