# Atlas

Android 原生地图与 Minecraft 基岩版世界制作工具。

**当前版本：0.2.0**

## 本版：现实地形选区

- 原生 MapLibre 地图控件，非网页地图
- OpenTopoMap 地形底图，可缩放、平移
- 点击地图逐点圈选多边形区域
- 输入纬度、经度定位
- 撤销、清空边界点
- 按圈选范围采集 20 × 20 高程网格
- 高程来源：Copernicus DEM GLO-90（约 90 米），通过 Open-Meteo Elevation API 查询
- 将边界坐标和高程网格附加到 Atlas 项目草稿

## 使用

1. 打开底部“地图”。
2. 拖动、缩放到现实世界的目标地形；也可以输入坐标定位。
3. 依次点击地图添加边界点，至少 3 个点形成区域。
4. 点击“采集高程”，等待数据读取完成。
5. 点击“存为项目”，输入项目名称并创建。

项目列表会显示圈选区域尺寸与采集到的海拔范围。

## 数据与限制

- OpenTopoMap 底图数据：© OpenStreetMap contributors、SRTM；地图样式 © OpenTopoMap（CC-BY-SA）。
- 高程：Copernicus DEM GLO-90，约 90 米网格；按 API 每批最多 100 个坐标分批请求，并按选区裁剪为 20 × 20 网格。
- 地图瓦片和高程请求会向对应的公共服务发送瓦片坐标或所选区域坐标；应用不会上传本地地图文件。
- 项目名称、区域边界和高程网格会保存到 Atlas 应用本地；卸载应用或清除应用数据会删除这些项目。
- 当前实现保存的是现实地形边界与高程数据草稿；Minecraft 区块生成、河流/群系生成和真实 `.mcworld` 导出尚未完成。

## 构建

JDK 17、Android SDK 35：

```bash
gradle --no-daemon assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`
