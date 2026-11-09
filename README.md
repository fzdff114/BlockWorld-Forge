# BlockWorld-Forge / Atlas

Android 原生地图采集与 Minecraft Bedrock 世界生成工具。无需 Google Play 服务。

当前版本：**0.4.2（预发布）**

## 0.4.2 更新
- 地图选区：两点矩形与多边形圈选；地点名称 / 经纬度定位。
- 高程采集：快速 30 m 与精细 20 m 两档；DEM 瓦片并发下载。
- 持久缓存：相同 DEM 瓦片保存在应用私有目录，重复采集和相邻区域可复用；缓存上限约 384 MiB。
- 建筑数据：OpenStreetMap / Overpass 查询建筑轮廓、建筑分部（building:part）、楼层 / 高度标签；缓存 7 天并去重。
- 地形垂直比例：1× / 1.5× / 2× / 3×；默认 2×，让低起伏地区在 Minecraft 中更容易辨认。1× 是不额外夸张的参考比例。
- Bedrock 导出：写入 LevelDB、Data3D、高度图和 SubChunk v9，并打包为 .mcworld；导出前重开数据库校验。
- 地图范围太大、无法在合理 DEM 级别内保留细节时会明确要求缩小选区，不再静默退化为极粗分辨率高程瓦片。

## 使用
1. 打开“地图”，搜索城市 / 地点或输入纬度、经度。
2. 选择“两点矩形”后依次点击地图上的两个对角点；不规则范围可使用“多边形”。
3. 选择快速 30 m 或精细 20 m，点击“采集地形+建筑”。
4. 查看海拔范围、地形起伏、采样间距和建筑数量；如果没有 OSM 建筑，界面会显示警告。
5. 点击“存为项目”，选择水平比例与垂直起伏倍率。
6. 进入“导出”，生成并保存 .mcworld，再用 Minecraft Bedrock 打开。

## 地形与建筑说明
- 高程来源为 Mapzen Terrarium DEM 瓦片（AWS Open Data 上的公开数据）。20 m / 30 m 是应用的采样间距，不等同于源 DEM 的原生精度；源数据质量随地区变化。
- 垂直起伏倍率只改变 Minecraft 中的垂直显示比例，不会创造源高程数据中不存在的山脊、沟谷或等高线细节。
- 建筑轮廓来自 OpenStreetMap。只有已绘制并被 Overpass 返回的建筑才能导入；缺少 OSM 标注的楼不会自动出现。高度缺失时按估算高度生成。
- 道路表面、真实河网、湖泊边界、植被群系、洞穴与遗迹尚未完成，不应把本版描述为完整城市数字孪生。
- 导出世界使用 Bedrock Infinite 生成器类型，并直接写入已生成的区块；不是 Flat / 超平坦世界预设。

## 系统要求
- Android 8.0（API 26）或更高版本。
- 不依赖 Google Play 服务。
- 建议保留充足空间：大区域导出会占用较多临时文件和世界存储。

## 本地构建
需要 JDK 17、Android SDK 35 与 Gradle 8.14.3：

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```
