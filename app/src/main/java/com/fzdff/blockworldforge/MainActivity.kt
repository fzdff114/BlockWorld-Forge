package com.fzdff.blockworldforge

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF2563EB)
private val PaleBlue = Color(0xFFEFF6FF)
private val Ink = Color(0xFF17263F)
private val Muted = Color(0xFF8795A8)
private val Line = Color(0xFFE7EDF5)
private val Canvas = Color(0xFFF7F9FC)
private val Green = Color(0xFF16A36A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ForgeTheme { ForgeApp() } }
    }
}

@Composable
private fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue, onPrimary = Color.White, secondary = Color(0xFF5C86C8),
            background = Canvas, surface = Color.White, onSurface = Ink, outline = Line
        ),
        content = content
    )
}

private data class Page(val id: String, val title: String, val icon: ImageVector)
private val pages = listOf(
    Page("home", "工作台", Icons.Outlined.Dashboard),
    Page("maps", "地图", Icons.Outlined.Map),
    Page("builder", "生成", Icons.Outlined.Terrain),
    Page("assets", "资源库", Icons.Outlined.Inventory2),
    Page("export", "导出", Icons.Outlined.FileUpload)
)
private data class WorldProject(val name: String, val size: Int, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgeApp() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("home") }
    var createDialog by rememberSaveable { mutableStateOf(false) }
    val projects = remember {
        mutableStateListOf(
            WorldProject("城市探索计划", 10000, "草稿 · 今天更新"),
            WorldProject("群岛与海岸线", 6000, "等待地图数据"),
            WorldProject("北境森林实验", 4000, "草稿 · 昨天更新")
        )
    }
    var selectedFile by rememberSaveable { mutableStateOf("尚未选择文件") }
    var worldSize by rememberSaveable { mutableIntStateOf(10000) }
    var terrain by rememberSaveable { mutableStateOf("真实地貌") }
    var heightData by rememberSaveable { mutableStateOf(true) }
    var water by rememberSaveable { mutableStateOf(true) }
    var structures by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedFile = displayName(context, uri)
            Toast.makeText(context, "已选择：$selectedFile", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BlockWorld Forge", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(pages.first { it.id == tab }.title, fontSize = 10.sp, color = Muted)
                    }
                },
                actions = {
                    IconButton(onClick = { Toast.makeText(context, "BlockWorld Forge 原生 Android 预览版", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "帮助", tint = Muted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = tab == page.id,
                        onClick = { tab = page.id },
                        icon = { Icon(page.icon, contentDescription = page.title) },
                        label = { Text(page.title, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Blue, selectedTextColor = Blue,
                            indicatorColor = PaleBlue, unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Canvas)) {
            when (tab) {
                "home" -> HomeScreen(projects, onCreate = { createDialog = true })
                "maps" -> MapsScreen(selectedFile) { picker.launch(arrayOf("*/*")) }
                "builder" -> BuilderScreen(
                    size = worldSize, onSize = { worldSize = it },
                    terrain = terrain, onTerrain = { terrain = it },
                    heightData = heightData, onHeight = { heightData = it },
                    water = water, onWater = { water = it },
                    structures = structures, onStructures = { structures = it }
                ) {
                    Toast.makeText(context, "配置已更新；生成引擎尚未接入。", Toast.LENGTH_LONG).show()
                }
                "assets" -> AssetsScreen()
                "export" -> ExportScreen {
                    Toast.makeText(context, "Bedrock 导出器尚未实现。", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (createDialog) {
        NewProjectDialog(
            onDismiss = { createDialog = false },
            onCreate = { name, size ->
                projects.add(0, WorldProject(name.ifBlank { "未命名世界" }, size, "新建草稿 · 刚刚"))
                createDialog = false
                Toast.makeText(context, "项目草稿已创建", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun displayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: (uri.lastPathSegment ?: "已选择文件")
    } catch (_: Exception) {
        uri.lastPathSegment ?: "已选择文件"
    }
}

@Composable
private fun HomeScreen(projects: List<WorldProject>, onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("YOUR WORLD, YOUR RULES")
            Text("世界工作台", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("从真实地理数据到属于你的方块世界，把构想变成可探索的地图。", fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
        }
        Hero(onCreate)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("世界项目", projects.size.toString().padStart(2, '0'), Icons.Outlined.Dashboard, Modifier.weight(1f))
            Metric("地图数据", "—", Icons.Outlined.Map, Modifier.weight(1f))
            Metric("生成任务", "—", Icons.Outlined.Terrain, Modifier.weight(1f))
        }
        Heading("最近项目", "继续你的世界构建")
        SurfaceCard {
            projects.take(5).forEachIndexed { index, project ->
                ProjectItem(project, index)
                if (index != projects.take(5).lastIndex) HorizontalDivider(color = Line)
            }
        }
        Heading("世界构建流程", "从数据到可探索世界")
        SurfaceCard {
            Step("01", "地图数据", "选择区域与数据来源", true)
            Spacer(Modifier.height(13.dp))
            Step("02", "地形与生态", "配置高度、群系与细节")
            Spacer(Modifier.height(13.dp))
            Step("03", "生成与导出", "构建并导出 Bedrock 世界")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(11.dp)) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("新建世界项目")
            }
        }
        Heading("模块状态", "当前版本开发进度")
        SurfaceCard {
            StateRow("界面与项目工作台", "已搭建", true)
            StateRow("地图数据接入", "规划中")
            StateRow("地形生成引擎", "规划中")
            StateRow("Bedrock 导出", "规划中")
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Hero(onCreate: () -> Unit) {
    Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Blue)) {
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF3479E8), Color(0xFF5C9CF2)))).padding(19.dp)
        ) {
            Column(Modifier.fillMaxWidth(.82f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = Color.White.copy(alpha = .15f), shape = CircleShape) {
                    Text("✦  BLOCKWORLD FORGE PREVIEW", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Text("把真实世界，\n锻造成方块世界。", fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("选择一片区域，配置地形与生态，再将它构建为 Minecraft Bedrock 世界。", fontSize = 9.sp, lineHeight = 15.sp, color = Color(0xFFE1EDFF))
                Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue), shape = RoundedCornerShape(9.dp)) {
                    Text("开始创建", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(14.dp))
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Public, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(54.dp))
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(13.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(17.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(subtitle, fontSize = 9.sp, color = Muted)
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { Column(Modifier.padding(13.dp), content = content) }
}

@Composable
private fun ProjectItem(project: WorldProject, index: Int) {
    val icon = listOf(Icons.Outlined.LocationOn, Icons.Outlined.Map, Icons.Outlined.Terrain)[index % 3]
    val tint = listOf(Color(0xFFE5F0FC), Color(0xFFDDF2F1), Color(0xFFE6F2E7))[index % 3]
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = tint, shape = RoundedCornerShape(9.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.padding(11.dp).size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(project.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Bedrock · ${project.size.toLocaleString()} × ${project.size.toLocaleString()}", fontSize = 8.sp, color = Muted)
            Text(project.status, fontSize = 8.sp, color = Color(0xFF8DA0B8))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFB8C2D0), modifier = Modifier.size(16.dp))
    }
}

private fun Int.toLocaleString(): String = "%,d".format(this)

@Composable
private fun Step(number: String, title: String, detail: String, active: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = if (active) PaleBlue else Color(0xFFF1F4F8), shape = RoundedCornerShape(7.dp)) {
            Text(number, Modifier.padding(horizontal = 9.dp, vertical = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) Blue else Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 8.sp, color = Muted)
        }
        if (active) Icon(Icons.Outlined.CheckCircle, null, tint = Green, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StateRow(title: String, state: String, done: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 9.sp, color = Color(0xFF5C6D84))
        Surface(color = if (done) Color(0xFFEAF8F0) else Color(0xFFF2F4F7), shape = CircleShape) {
            Text(state, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 8.sp, color = if (done) Green else Muted)
        }
    }
}

@Composable
private fun MapsScreen(selectedFile: String, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PageTitle("地图数据", "MAP DATA WORKSPACE", "选择地图来源，导入地理数据，为世界生成准备真实地形基础。")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = PaleBlue, shape = RoundedCornerShape(9.dp)) {
                    Icon(Icons.Outlined.CloudUpload, null, tint = Blue, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Column {
                    Text("导入本地地图文件", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("当前版本只选择文件，不会上传或解析。", fontSize = 9.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(112.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFBCD0EA))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null, tint = Blue, modifier = Modifier.size(23.dp))
                    Text("选择地图文件", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(".osm · .pbf · .geojson · .mbtiles · .zip", fontSize = 8.sp, color = Muted)
                }
            }
            Text(selectedFile, Modifier.padding(top = 9.dp), fontSize = 9.sp, color = Blue, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Heading("可接入的数据来源", "地图供应商适配器尚未实现")
        SourceCard("高", "高德地图", "可作为区域搜索与位置选择入口；数据使用范围需按授权接入。", Color(0xFFFFF0ED), Color(0xFFE45C4A))
        SourceCard("OSM", "OpenStreetMap", "后续用于道路、建筑与地物数据适配。", Color(0xFFEEF8E9), Color(0xFF609D4E))
        SourceCard("▲", "高程与地表数据", "为高度、坡度、水系与地表覆盖提供数据基础。", PaleBlue, Blue)
        InfoCard("不同地图服务的数据许可、缓存与衍生作品规则不同。正式接入前会确认数据来源与授权范围。")
    }
}

@Composable
private fun SourceCard(mark: String, title: String, desc: String, background: Color, foreground: Color) {
    SurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = background, shape = RoundedCornerShape(9.dp)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(mark, color = foreground, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(desc, fontSize = 8.sp, color = Muted, lineHeight = 14.sp)
                Text("适配器规划中", fontSize = 8.sp, color = Blue)
            }
        }
    }
}

@Composable
private fun BuilderScreen(
    size: Int, onSize: (Int) -> Unit,
    terrain: String, onTerrain: (String) -> Unit,
    heightData: Boolean, onHeight: (Boolean) -> Unit,
    water: Boolean, onWater: (Boolean) -> Unit,
    structures: Boolean, onStructures: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PageTitle("世界生成", "WORLD BUILDER", "设置世界尺寸、目标版本与地形风格。当前为原生配置界面预览。")
        SurfaceCard {
            Text("基础设置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("目标平台：Minecraft Bedrock", fontSize = 9.sp, color = Muted, modifier = Modifier.padding(top = 7.dp))
            Text("世界边长：${size.toLocaleString()} × ${size.toLocaleString()}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(4000, 6000, 10000, 15000, 20000).forEach { option ->
                    FilterChip(selected = size == option, onClick = { onSize(option) }, label = { Text("${option / 1000}K", fontSize = 8.sp) })
                }
            }
        }
        SurfaceCard {
            Text("地形风格", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("选择初始生成方向", fontSize = 9.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 7.dp))
            listOf("真实地貌" to "贴近现实高程与地物", "自然探索" to "森林、河流与遗迹", "冒险群岛" to "岛屿、海岸与深海").forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = terrain == item.first, onClick = { onTerrain(item.first) }, colors = RadioButtonDefaults.colors(selectedColor = Blue))
                    Column(Modifier.weight(1f)) {
                        Text(item.first, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.second, fontSize = 8.sp, color = Muted)
                    }
                }
            }
        }
        SurfaceCard {
            Text("生成选项", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Setting("地形高度数据", "为地表生成提供高程参考", heightData, onHeight)
            HorizontalDivider(color = Line)
            Setting("自然水系", "保留河流、湖泊与海岸线", water, onWater)
            HorizontalDivider(color = Line)
            Setting("结构与遗迹点位", "后续可接入结构资源库", structures, onStructures)
            Spacer(Modifier.height(7.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp)) {
                Text("保存配置")
                Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.padding(start = 5.dp).size(15.dp))
            }
        }
        InfoCard("当前版本只记录界面配置，不会启动真实地形生成。")
    }
}

@Composable
private fun Setting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 8.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White))
    }
}

@Composable
private fun AssetsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PageTitle("资源库", "ASSET LIBRARY", "管理群系规则、结构模板与可复用的世界资源。")
        AssetCard(Icons.Outlined.Forest, "群系预设", "森林、草原、针叶林、沙漠、雪原与自定义生态规则。", Color(0xFFEAF6E8), Color(0xFF4D9565))
        AssetCard(Icons.Outlined.AccountTree, "结构与遗迹", "规划遗迹、村庄、地牢与可探索点位；后续支持导入 .mcstructure。", Color(0xFFF0EEFF), Color(0xFF7566C4))
        AssetCard(Icons.Outlined.Layers, "地表材质规则", "地表方块映射、地貌过渡与材质分布规则。", PaleBlue, Blue)
    }
}

@Composable
private fun AssetCard(icon: ImageVector, title: String, description: String, background: Color, foreground: Color) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(color = background, shape = RoundedCornerShape(11.dp)) {
                Icon(icon, null, tint = foreground, modifier = Modifier.padding(12.dp).size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 8.sp, color = Muted, lineHeight = 14.sp)
                Text("资源系统规划中", fontSize = 8.sp, color = Blue)
            }
        }
    }
}

@Composable
private fun ExportScreen(onExport: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PageTitle("导出中心", "EXPORT CENTER", "管理世界构建产物，规划 Minecraft 可导入的文件格式。")
        SurfaceCard {
            Surface(color = PaleBlue, shape = RoundedCornerShape(11.dp)) {
                Icon(Icons.Outlined.FileUpload, null, tint = Blue, modifier = Modifier.padding(13.dp).size(28.dp))
            }
            Text("准备好后，导出你的世界", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("目标格式为 Minecraft Bedrock 世界包（.mcworld）。当前尚未实现实际导出。", fontSize = 9.sp, color = Muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(15.dp))
            Step("01", "完成地形生成", "生成区块、地形与生态")
            Spacer(Modifier.height(12.dp))
            Step("02", "写入世界数据", "整合区块、元数据与资源")
            Spacer(Modifier.height(12.dp))
            Step("03", "打包为 .mcworld", "校验目录结构并生成文件")
            Spacer(Modifier.height(17.dp))
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().height(43.dp), shape = RoundedCornerShape(10.dp)) {
                Text("导出 .mcworld")
                Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.padding(start = 5.dp).size(15.dp))
            }
        }
        InfoCard("兼容性目标：优先面向 Android 版 Minecraft Bedrock。正式导出前将检查世界数据库、清单与文件结构完整性。")
    }
}

@Composable
private fun PageTitle(title: String, eyebrow: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Eyebrow(eyebrow)
        Text(title, fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text(description, fontSize = 10.sp, color = Muted, lineHeight = 16.sp)
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, fontSize = 8.sp, letterSpacing = 1.3.sp, color = Color(0xFF7196CB), fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun InfoCard(text: String) {
    SurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = Color(0xFF6590C8), modifier = Modifier.size(16.dp))
            Text(text, fontSize = 8.sp, color = Muted, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("新世界项目") }
    var size by rememberSaveable { mutableIntStateOf(10000) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建世界项目", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("世界名称") }, singleLine = true)
                Text("地图规模", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4000, 6000, 10000, 15000, 20000).forEach { option ->
                        FilterChip(selected = size == option, onClick = { size = option }, label = { Text("${option / 1000}K", fontSize = 8.sp) })
                    }
                }
                Text("目标平台：Minecraft Bedrock", fontSize = 9.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, size) }) { Text("创建项目") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = Color.White
    )
}
