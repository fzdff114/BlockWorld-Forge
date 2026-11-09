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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF356AE6)
private val AccentSoft = Color(0xFFEAF0FF)
private val Ink = Color(0xFF182235)
private val Muted = Color(0xFF8490A3)
private val DividerColor = Color(0xFFE9EDF3)
private val PageBackground = Color(0xFFF6F8FB)

private data class NavPage(val id: String, val title: String, val icon: ImageVector)

private val navPages = listOf(
    NavPage("projects", "项目", Icons.Outlined.FolderOpen),
    NavPage("map", "地图", Icons.Outlined.Map),
    NavPage("terrain", "地形", Icons.Outlined.Terrain),
    NavPage("assets", "素材", Icons.Outlined.Layers),
    NavPage("export", "导出", Icons.Outlined.FileUpload)
)

private val mapSizes = listOf(4000, 6000, 10000, 15000, 20000)

private data class WorldProject(val name: String, val size: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AtlasTheme { AtlasApp() } }
    }
}

@Composable
private fun AtlasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Color(0xFF6C88C8),
            background = PageBackground,
            surface = Color.White,
            onSurface = Ink,
            outline = DividerColor
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtlasApp() {
    val context = LocalContext.current
    var currentPage by rememberSaveable { mutableStateOf("projects") }
    var showNewProject by rememberSaveable { mutableStateOf(false) }
    val projects = remember { mutableStateListOf<WorldProject>() }

    var selectedFile by rememberSaveable { mutableStateOf("") }
    var worldSize by rememberSaveable { mutableIntStateOf(10000) }
    var terrainStyle by rememberSaveable { mutableStateOf("自然") }
    var useElevation by rememberSaveable { mutableStateOf(true) }
    var useWater by rememberSaveable { mutableStateOf(true) }
    var useStructures by rememberSaveable { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFile = getDisplayName(context, uri)
            Toast.makeText(context, "已选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Atlas",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        letterSpacing = (-0.5).sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 2.dp
            ) {
                navPages.forEach { page ->
                    NavigationBarItem(
                        selected = currentPage == page.id,
                        onClick = { currentPage = page.id },
                        icon = { Icon(page.icon, contentDescription = page.title) },
                        label = { Text(page.title, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = AccentSoft,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PageBackground)
        ) {
            when (currentPage) {
                "projects" -> ProjectsScreen(
                    projects = projects,
                    onCreate = { showNewProject = true }
                )
                "map" -> MapScreen(
                    selectedFile = selectedFile,
                    onPickFile = { filePicker.launch(arrayOf("*/*")) }
                )
                "terrain" -> TerrainScreen(
                    worldSize = worldSize,
                    onWorldSize = { worldSize = it },
                    terrainStyle = terrainStyle,
                    onTerrainStyle = { terrainStyle = it },
                    useElevation = useElevation,
                    onUseElevation = { useElevation = it },
                    useWater = useWater,
                    onUseWater = { useWater = it },
                    useStructures = useStructures,
                    onUseStructures = { useStructures = it }
                )
                "assets" -> AssetsScreen()
                "export" -> ExportScreen()
            }
        }
    }

    if (showNewProject) {
        NewProjectDialog(
            onDismiss = { showNewProject = false },
            onCreate = { name, size ->
                projects.add(0, WorldProject(name.ifBlank { "未命名项目" }, size))
                worldSize = size
                showNewProject = false
            }
        )
    }
}

private fun getDisplayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
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
private fun ProjectsScreen(
    projects: List<WorldProject>,
    onCreate: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("项目", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onCreate,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AccentSoft,
                    contentColor = Accent
                )
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建", fontSize = 13.sp)
            }
        }

        if (projects.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = Color.White,
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 38.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF9AA8BC),
                        modifier = Modifier.size(30.dp)
                    )
                    Text("暂无项目", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("新建一个项目开始编辑", fontSize = 11.sp, color = Muted)
                    TextButton(onClick = onCreate) { Text("新建项目") }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { project ->
                    ProjectRow(project)
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: WorldProject) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    project.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${project.size / 1000} km × ${project.size / 1000} km",
                    fontSize = 10.sp,
                    color = Muted
                )
            }
            Text("草稿", fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun MapScreen(selectedFile: String, onPickFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("地图", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Accent)
                    Spacer(Modifier.width(9.dp))
                    Text("地图文件", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFD6DFEF))
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("选择文件")
                }
                if (selectedFile.isNotBlank()) {
                    Text(
                        selectedFile,
                        fontSize = 11.sp,
                        color = Accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text("地图文件解析尚未接入", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun TerrainScreen(
    worldSize: Int,
    onWorldSize: (Int) -> Unit,
    terrainStyle: String,
    onTerrainStyle: (String) -> Unit,
    useElevation: Boolean,
    onUseElevation: (Boolean) -> Unit,
    useWater: Boolean,
    onUseWater: (Boolean) -> Unit,
    useStructures: Boolean,
    onUseStructures: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("地形", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionLabel("地图尺寸")
            Text(
                "${worldSize / 1000} km × ${worldSize / 1000} km",
                fontSize = 13.sp,
                color = Ink,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mapSizes.forEach { size ->
                    FilterChip(
                        selected = worldSize == size,
                        onClick = { onWorldSize(size) },
                        label = { Text("${size / 1000}K", fontSize = 11.sp) }
                    )
                }
            }
        }

        HorizontalDivider(color = DividerColor)

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionLabel("地形风格")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("真实", "自然", "群岛").forEach { style ->
                    FilterChip(
                        selected = terrainStyle == style,
                        onClick = { onTerrainStyle(style) },
                        label = { Text(style, fontSize = 11.sp) }
                    )
                }
            }
        }

        HorizontalDivider(color = DividerColor)

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("生成选项")
            SwitchRow("高程数据", useElevation, onUseElevation)
            SwitchRow("河流与湖泊", useWater, onUseWater)
            SwitchRow("结构与遗迹", useStructures, onUseStructures)
        }

        Text("地形生成引擎尚未接入", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF59677B))
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Ink)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun AssetsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("素材", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        AssetRow(Icons.Outlined.Forest, "群系")
        AssetRow(Icons.Outlined.AccountTree, "结构与遗迹")
        AssetRow(Icons.Outlined.Layers, "地表规则")
    }
}

@Composable
private fun AssetRow(icon: ImageVector, title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Ink)
            Text("开发中", fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun ExportScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("导出", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("基岩版世界", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(".mcworld", fontSize = 10.sp, color = Muted)
                }
                Text("开发中", fontSize = 10.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableIntStateOf(10000) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建项目", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true
                )
                SectionLabel("地图尺寸")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    mapSizes.forEach { option ->
                        FilterChip(
                            selected = size == option,
                            onClick = { size = option },
                            label = { Text("${option / 1000}K", fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, size) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = Color.White
    )
}
