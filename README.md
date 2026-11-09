# 方块世界工坊（BlockWorld Forge）

**面向 Android 手机的原生 Minecraft 基岩版地图规划与世界生成工具。**

> 当前版本：**0.1.1（中文界面预览版）**  
> 应用界面使用 Kotlin + Jetpack Compose 原生绘制，不使用 HTML、CSS、JavaScript、网页容器或 WebView 套壳。

## 当前已经完成

- [x] 原生 Android 启动入口与白底蓝色主题
- [x] 简体中文界面：工作台、地图、世界生成、资源库、导出中心
- [x] 工作台示例项目列表与新建项目草稿
- [x] 地图文件选择入口（使用 Android 系统文件选择器）
- [x] 世界尺寸选项：4K、6K、10K、15K、20K
- [x] 地形风格选项：真实地貌、自然探索、冒险群岛
- [x] 高程、水系、结构遗迹等配置开关
- [x] 群系、结构遗迹、地表材质资源库的规划页面
- [x] 基岩版 .mcworld 导出流程说明页面
- [x] GitHub Actions 自动构建 Android Debug APK

## 目前还没有实现

本版本是**原生界面与流程原型**，不是已经完成的地图生成器。以下能力仍在开发路线中：

- 地图文件解析、预览与坐标/区域选择
- 高德地图、开放街图等数据源适配器
- 高程、地表覆盖、河流、湖泊等数据获取与处理
- 地形、群系、区块及自然结构的实际生成
- 项目设置的本地持久化、重命名、复制和删除
- Minecraft 基岩版世界数据库写入与真实 .mcworld 打包
- 在 Android 版 Minecraft 中导入测试

**重要说明：** 当前导入按钮只会通过系统文件选择器选中一个文件并显示文件名；不会上传、解析或转换地图文件。配置开关只改变界面状态，不会启动地形生成。导出按钮目前只提示导出器尚未实现，不会生成虚假的世界文件。

## 安装 APK

1. 打开本仓库的 [Actions 构建记录](../../actions)。
2. 进入最新的 **Android APK** 工作流运行记录。
3. 在 Artifacts 区域下载 `BlockWorld-Forge-Android-debug`。
4. 解压 ZIP，找到 `app-debug.apk`。
5. 将 APK 传到 Android 手机安装。若系统拦截，请仅对本次安装来源授予“允许安装未知应用”权限。

这是 Debug 测试包，不是 Google Play 发布包。当前不依赖 Google Play 服务。

## 本地构建

- Android Studio
- JDK 17
- Android SDK（项目使用 compileSdk 35）

在仓库根目录执行：

```bash
gradle --no-daemon assembleDebug
```

APK 输出位置：

```
app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

- `app/src/main/java/com/fzdff/blockworldforge/`：Kotlin + Compose 原生界面
- `app/src/main/AndroidManifest.xml`：应用入口与启动器名称
- `app/src/main/res/`：图标与 Android 主题资源
- `.github/workflows/android-apk.yml`：自动构建 APK
- `docs/使用说明.md`：当前版本操作说明与功能边界
- `docs/ROADMAP.md`：后续开发计划

## 开发原则

1. 原生 Android 界面，不改成网页或 WebView。
2. 界面预览、地图数据、地形引擎和世界导出分层开发。
3. 未实现的能力必须明确标注，不把按钮或演示数据描述为真实功能。
4. 地图数据接入遵循供应商授权、数据许可和署名要求。
5. 导出的世界必须经过文件结构校验和实际游戏导入测试后，才标记为完成。
