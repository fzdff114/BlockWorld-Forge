# Atlas

Android 原生地图与 Minecraft 基岩版世界制作工具。

当前版本：0.3.0 测试版

## 本版功能

- 原生地图圈选现实区域
- 采集高程并预览网格
- 新建项目时选择现实比例：1:1、1:2、1:4、1:8、1:16
- 将高程网格重采样为 Minecraft 方块高度
- 写入 Bedrock LevelDB 区块与子区块记录
- 生成带 db/ 和 level.dat 的 .mcworld 世界包
- 导出前重新打开数据库检查关键记录，并检查世界包结构

## 使用

1. 在“地图”页圈选现实地形并点击“采集高程”。
2. 点击“存为项目”，选择比例（例如 1:4 = 1 个方块代表 4 米）。
3. 进入“导出”，在项目旁点击“生成世界”。
4. 在 Android 文件保存器中选择位置并保存 .mcworld。
5. 点击保存的文件，交由 Minecraft 基岩版导入。

## 当前转换范围

本版先生成基础地形层：基岩、石头、泥土、草方块；低于海平面的区域使用沙底和水层。输出为标准 LevelDB 世界包。

当前尚未生成道路、建筑、树木、真实河网、群系、洞穴和遗迹。原始高程为 20×20 样本网格，插值只会平滑网格，不会创造原始数据中不存在的地形细节。水平比例由项目设置控制；若真实高程超出基岩版可用高度，则垂直比例会自动压缩以放进世界高度范围。

为保护手机内存，当前转换限制为每个方向最多 512 个方块、总计最多 262,144 个地表格。选区过大时请调高比例（如 1:8、1:16）。

## 数据格式参考

- osm-to-bedrock：现实坐标投影、地形填充、Bedrock LevelDB / mcworld 管线。
- Arnis：现代 Bedrock 子区块调色板与 NBT 编码参考。
- HiveGamesOSS/leveldb-mcpe-java：支持 Minecraft PE 压缩方式的 Java LevelDB 库（Apache-2.0）。

详细格式说明见 docs/数据库转换说明.md。

## 安装

在 GitHub Releases 下载最新 Atlas 测试版 APK。最低 Android 8.0（API 26），不依赖 Google Play 服务。

## 本地构建

JDK 17、Android SDK 35：

gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
