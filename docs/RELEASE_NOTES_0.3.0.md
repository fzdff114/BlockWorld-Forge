# Atlas 0.3.0 — 地形数据库转换测试版

## 新增

- 现实区域比例选择：1:1、1:2、1:4、1:8、1:16。
- 将已采集的现实高程网格转换为 Minecraft 地表高度。
- 写入 Bedrock LevelDB 区块记录、Data3D、高度图和 SubChunk v9 调色板。
- 生成小端 NBT 的 level.dat。
- 打包可供 Minecraft 导入的 mcworld 文件。
- 导出前重开 LevelDB 校验关键键值，并检查 ZIP 目录结构。
- 新增 JVM 单元测试。

## 当前地形内容

基岩、石头、泥土、草方块；负海拔区域加沙底和水层。高程源仍为 20×20 网格，插值不会增加原始采样数据没有的地形细节。

## 兼容性

- 目标版本：Minecraft Bedrock 1.26.52.3（NetworkVersion 2193、Chunk Version 41、SubChunk v9）。
- 最低系统：Android 8.0（API 26），用于兼容 LevelDB Java 11 字节码中的 MethodHandle 调用。

## 测试限制

- 每个方向最多 512 方块，总计最多 262,144 个地表格。
- 当前尚无道路、建筑、树木、河网、洞穴、完整群系或遗迹。
- CI 验证：7 项 JVM 格式测试通过，Android APK 构建与上传通过。已做数据库结构和压缩读写检查，尚未完成 Android 版 Minecraft 真机导入验收。请把导出的 mcworld 导入 Minecraft 并反馈结果。
