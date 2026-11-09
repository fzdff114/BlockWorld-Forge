package com.fzdff.blockworldforge

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.*

private val Accent = Color(0xFF356AE6)
private val AccentSoft = Color(0xFFEAF0FF)
private val Ink = Color(0xFF182235)
private val Muted = Color(0xFF8490A3)
private val DividerColor = Color(0xFFE9EDF3)
private val PageBackground = Color(0xFFF6F8FB)

private const val AREA_FILL_SOURCE = "atlas-area-fill"
private const val AREA_LINE_SOURCE = "atlas-area-line"
private const val AREA_POINTS_SOURCE = "atlas-area-points"
private const val EMPTY_FEATURES = """{"type":"FeatureCollection","features":[]}"""

private const val BASE_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

private data class NavPage(val id: String, val title: String, val icon: ImageVector)

private val navPages = listOf(
    NavPage("terrain", "生成器", Icons.Outlined.AutoAwesome),
    NavPage("projects", "项目", Icons.Outlined.FolderOpen),
    NavPage("export", "导出", Icons.Outlined.FileUpload)
)

private val mapSizes = listOf(4000, 6000, 10000, 15000, 20000)

internal data class GeoCoordinate(val latitude: Double, val longitude: Double)
internal data class OsmBuilding(
    val polygon: List<GeoCoordinate>,
    val heightMeters: Double,
    val minHeightMeters: Double,
    val name: String?,
    val source: String = "OpenStreetMap"
)

private data class ElevationGrid(
    val rows: Int,
    val columns: Int,
    val values: List<List<Double?>>,
    val minimum: Double,
    val maximum: Double,
    val average: Double,
    val sampleCount: Int,
    val resolutionMeters: Double,
    val demZoom: Int,
    val buildings: List<OsmBuilding>,
    val buildingError: String? = null
) {
    val summary: String
        get() = "海拔 ${minimum.roundToInt()}–${maximum.roundToInt()} m · 起伏 ${(maximum - minimum).roundToInt()} m"
}

private data class RegionDraft(
    val points: List<GeoCoordinate>,
    val widthKm: Double,
    val heightKm: Double,
    val elevation: ElevationGrid?
) {
    val summary: String
        get() = String.format(Locale.US, "%.2f × %.2f km", widthKm, heightKm)
}

private data class WorldProject(
    val name: String,
    val size: Int,
    val regionSummary: String? = null,
    val elevationSummary: String? = null,
    val regionDataJson: String? = null,
    val metersPerBlock: Int = 4,
    val verticalExaggeration: Double = 1.0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtlasTheme {
                AtlasApp()
            }
        }
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
    val scope = rememberCoroutineScope()
    var currentPage by rememberSaveable { mutableStateOf("terrain") }
    var generatorProjectName by rememberSaveable { mutableStateOf("壮丽世界") }
    var settingsJson by rememberSaveable {
        mutableStateOf(ProceduralWorldSettings().toJson().toString())
    }
    val settings = remember(settingsJson) { ProceduralWorldSettings.fromJson(settingsJson) }
    val projects = remember(context) {
        mutableStateListOf<WorldProject>().apply { addAll(loadProjects(context)) }
    }
    var pendingExportProject by remember { mutableStateOf<WorldProject?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }

    fun updateSettings(value: ProceduralWorldSettings) {
        settingsJson = value.normalized().toJson().toString()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val project = pendingExportProject
        pendingExportProject = null
        if (uri != null && project != null && !project.regionDataJson.isNullOrBlank()) {
            exportBusy = true
            exportProgress = 0f
            exportStatus = "准备生成区块…"
            scope.launch {
                try {
                    val report = withContext(Dispatchers.IO) {
                        BedrockWorldExporter.export(
                            context = context,
                            worldName = project.name,
                            regionJson = project.regionDataJson!!,
                            metersPerBlock = project.metersPerBlock,
                            verticalExaggeration = project.verticalExaggeration,
                            outputUri = uri
                        ) { completed, total ->
                            scope.launch {
                                exportProgress = completed.toFloat() / total.coerceAtLeast(1)
                                exportStatus = "生成并写入区块：" + completed + " / " + total
                            }
                        }
                    }
                    exportProgress = 1f
                    exportStatus = "完成：" + report.widthBlocks + " × " + report.depthBlocks +
                        " 方块 · " + report.chunkCount + " 个区块 · " + formatBytes(report.archiveBytes)
                } catch (error: Exception) {
                    exportStatus = "生成失败：" + (error.localizedMessage ?: "未知错误")
                } finally {
                    exportBusy = false
                }
            }
        }
    }

    fun launchExport(project: WorldProject) {
        pendingExportProject = project
        val safeName = project.name.filter {
            it.isLetterOrDigit() || it == '-' || it == '_' || it == ' '
        }.trim().ifBlank { "BlockWorld" }
        exportLauncher.launch(safeName + ".mcworld")
        currentPage = "export"
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("BLOCKWORLD FORGE", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = 1.sp)
                        Text("程序化世界 · 地形 · 群系 · 结构", fontSize = 10.sp, color = Muted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
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
        Box(Modifier.fillMaxSize().padding(innerPadding).background(PageBackground)) {
            when (currentPage) {
                "terrain" -> TerrainScreen(
                    projectName = generatorProjectName,
                    onProjectName = { generatorProjectName = it },
                    settings = settings,
                    onSettings = ::updateSettings,
                    onCreateProject = {
                        val snapshot = settings.normalized()
                        val project = WorldProject(
                            name = generatorProjectName.trim().ifBlank { "壮丽世界" },
                            size = snapshot.worldSize,
                            regionSummary = "程序生成 · " + snapshot.worldSize + " × " + snapshot.worldSize + " 方块",
                            elevationSummary = snapshot.biomeCount.toString() + " 种群系 · 洞穴/矿脉/树木/遗迹",
                            regionDataJson = snapshot.toJson().toString(),
                            metersPerBlock = 1,
                            verticalExaggeration = 1.0
                        )
                        projects.add(0, project)
                        saveProjects(context, projects)
                        currentPage = "projects"
                    }
                )
                "projects" -> ProjectsScreen(
                    projects = projects,
                    onCreate = { currentPage = "terrain" }
                )
                "export" -> ExportScreen(
                    projects = projects,
                    exporting = exportBusy,
                    progress = exportProgress,
                    status = exportStatus,
                    onExport = { launchExport(it) }
                )
            }
        }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF9AA8BC),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("暂无项目", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { project -> ProjectRow(project) }
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
                if (project.regionSummary != null) {
                    Text(
                        "范围 " + project.regionSummary + " · 比例 1:" + project.metersPerBlock +
                            " · 地形起伏 " + project.verticalExaggeration + "×",
                        fontSize = 10.sp,
                        color = Color(0xFF60728D)
                    )
                } else {
                    Text(
                        "比例 1:" + project.metersPerBlock + " · 未设置区域",
                        fontSize = 10.sp,
                        color = Muted
                    )
                }
                project.elevationSummary?.let {
                    Text(it, fontSize = 10.sp, color = Color(0xFF60728D))
                }
            }
            Text("草稿", fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun MapScreen(
    mapView: MapView,
    map: MapLibreMap?,
    selectedPoints: List<GeoCoordinate>,
    elevationGrid: ElevationGrid?,
    importedBuildings: List<OsmBuilding>,
    onAddPoint: (GeoCoordinate) -> Unit,
    onSetPoints: (List<GeoCoordinate>) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onElevationReady: (ElevationGrid) -> Unit,
    onSaveRegion: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var coordinateQuery by rememberSaveable { mutableStateOf("深圳") }
    var locating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var captureStatus by remember { mutableStateOf("") }
    var captureNotice by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var show3d by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf("矩形") }
    var terrainSampleSpacing by rememberSaveable { mutableIntStateOf(30) }
    var rectangleFirstCorner by remember { mutableStateOf<GeoCoordinate?>(null) }

    DisposableEffect(map, selectionMode, rectangleFirstCorner) {
        if (map == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnMapClickListener { location ->
                val clicked = GeoCoordinate(location.latitude, location.longitude)
                if (selectionMode == "矩形") {
                    val first = rectangleFirstCorner
                    if (first == null) {
                        rectangleFirstCorner = clicked
                        onSetPoints(listOf(clicked))
                        errorMessage = "已记录第一个角点，再点矩形对角位置"
                    } else {
                        val south = min(first.latitude, clicked.latitude)
                        val north = max(first.latitude, clicked.latitude)
                        val west = min(first.longitude, clicked.longitude)
                        val east = max(first.longitude, clicked.longitude)
                        if (haversineMeters(south, west, north, east) < 30.0) {
                            errorMessage = "矩形范围太小，请选择更远的对角点"
                        } else {
                            onSetPoints(
                                listOf(
                                    GeoCoordinate(north, west),
                                    GeoCoordinate(north, east),
                                    GeoCoordinate(south, east),
                                    GeoCoordinate(south, west)
                                )
                            )
                            errorMessage = ""
                            rectangleFirstCorner = null
                        }
                    }
                } else if (selectedPoints.size < 50) {
                    onAddPoint(clicked)
                    errorMessage = ""
                } else {
                    android.widget.Toast.makeText(context, "最多选择 50 个边界点", android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            }
            map.addOnMapClickListener(listener)
            onDispose { map.removeOnMapClickListener(listener) }
        }
    }

    LaunchedEffect(map, selectedPoints.toList()) {
        if (map != null) updateSelectionOverlay(map, selectedPoints)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = coordinateQuery,
                onValueChange = { coordinateQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("地点名称或 纬度, 经度", fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp)
            )
            FilledTonalButton(
                onClick = {
                    val query = coordinateQuery.trim()
                    if (query.isBlank()) {
                        android.widget.Toast.makeText(context, "输入地点名称或坐标", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        locating = true
                        scope.launch {
                            try {
                                val target = withContext(Dispatchers.IO) {
                                    val values = query.split(',', '，').mapNotNull { it.trim().toDoubleOrNull() }
                                    if (values.size == 2 && values[0] in -85.0..85.0 && values[1] in -180.0..180.0) {
                                        GeoCoordinate(values[0], values[1])
                                    } else {
                                        geocodePlace(query)
                                    }
                                }
                                val camera = CameraPosition.Builder()
                                    .target(LatLng(target.latitude, target.longitude))
                                    .zoom(16.0)
                                    .tilt(if (show3d) 55.0 else 0.0)
                                    .build()
                                map?.animateCamera(CameraUpdateFactory.newCameraPosition(camera))
                            } catch (error: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    error.localizedMessage ?: "没有找到该地点",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                locating = false
                            }
                        }
                    }
                },
                enabled = !locating,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (locating) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(if (locating) "搜索中" else "定位", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = {
                    show3d = !show3d
                    map?.let {
                        val current = it.cameraPosition
                        val camera = CameraPosition.Builder()
                            .target(current.target)
                            .zoom(current.zoom)
                            .bearing(current.bearing)
                            .tilt(if (show3d) 55.0 else 0.0)
                            .build()
                        it.animateCamera(CameraUpdateFactory.newCameraPosition(camera))
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (show3d) "平面" else "3D", fontSize = 12.sp)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(13.dp))
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )
            if (map == null) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.92f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("地图加载中", fontSize = 12.sp, color = Ink)
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (selectionMode == "矩形") {
                        if (rectangleFirstCorner == null) "矩形模式：依次点击两个对角点"
                        else "已选第一个角点：再点击对角位置"
                    } else {
                        "多边形模式：依次点击边界点"
                    },
                    Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    color = Ink
                )
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
            shadowElevation = 3.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selectionMode == "矩形") "矩形两点圈选" else "边界点 ${selectedPoints.size}/50",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (selectionMode == "矩形" && rectangleFirstCorner != null) {
                                rectangleFirstCorner = null
                                onClear()
                                errorMessage = "已取消第一个角点"
                            } else if (selectionMode == "多边形") {
                                onUndo()
                            } else {
                                onClear()
                            }
                        },
                        enabled = rectangleFirstCorner != null || selectedPoints.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("撤销", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            rectangleFirstCorner = null
                            errorMessage = ""
                            onClear()
                        },
                        enabled = selectedPoints.isNotEmpty() || rectangleFirstCorner != null,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("清空", fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = selectionMode == "矩形",
                        onClick = {
                            selectionMode = "矩形"
                            rectangleFirstCorner = null
                            errorMessage = ""
                            onClear()
                        },
                        label = { Text("两点矩形", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectionMode == "多边形",
                        onClick = {
                            selectionMode = "多边形"
                            rectangleFirstCorner = null
                            errorMessage = ""
                            onClear()
                        },
                        label = { Text("多边形", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("高程采样", fontSize = 11.sp, color = Muted)
                    FilterChip(
                        selected = terrainSampleSpacing == 30,
                        onClick = { terrainSampleSpacing = 30 },
                        label = { Text("快速 30m", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = terrainSampleSpacing == 20,
                        onClick = { terrainSampleSpacing = 20 },
                        label = { Text("精细 20m", fontSize = 10.sp) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val snapshot = selectedPoints.toList()
                            loading = true
                            errorMessage = ""
                            captureNotice = ""
                            scope.launch {
                                try {
                                    captureStatus = "读取地图底图中的建筑矢量…"
                                    val mapReadResult = runCatching {
                                        if (map == null) emptyList()
                                        else RenderedMapBuildingReader.readVisibleBuildings(
                                            map = map,
                                            mapView = mapView,
                                            boundary = snapshot
                                        ).buildings
                                    }
                                    val renderedMapBuildings = mapReadResult.getOrDefault(emptyList())
                                    val mapReadWarning = mapReadResult.exceptionOrNull()?.localizedMessage
                                    captureStatus = "底图已读出 ${renderedMapBuildings.size} 栋建筑；下载高程瓦片…"
                                    val result = withContext(Dispatchers.IO) {
                                        fetchElevationGrid(context, snapshot, terrainSampleSpacing) { phase, completed, total ->
                                            scope.launch {
                                                captureStatus = if (total > 1) {
                                                    phase + " " + completed + "/" + total
                                                } else {
                                                    phase
                                                }
                                            }
                                        }
                                    }
                                    // Source priority: imported China building-height data,
                                    // then polygons already rendered by the basemap, then Overpass.
                                    val basemapAndOsm = mergeBuildingSources(renderedMapBuildings, result.buildings)
                                    val mergedBuildings = mergeBuildingSources(importedBuildings, basemapAndOsm)
                                    val combined = result.copy(
                                        buildings = mergedBuildings,
                                        buildingError = if (mergedBuildings.isEmpty()) result.buildingError else null
                                    )
                                    onElevationReady(combined)
                                    val basemapCount = mergedBuildings.count {
                                        it.source.startsWith("OpenFreeMap")
                                    }
                                    val chinaCount = mergedBuildings.count {
                                        it.source.contains("3D-GloBFP") ||
                                            it.source.contains("China Urban Building Footprint Height Dataset")
                                    }
                                    val osmCount = mergedBuildings.count { it.source.startsWith("OpenStreetMap") }
                                    if (combined.buildingError != null) {
                                        errorMessage = "地形已采集，但三种来源均未取得建筑：" +
                                            combined.buildingError +
                                            "。请把选区移动到视口中央，缩放到建筑可见的街区级别，等待楼块显示后重新采集。"
                                    } else {
                                        captureNotice = "读取完成：地图底图 ${basemapCount} 栋 · 中国数据 ${chinaCount} 栋 · OSM ${osmCount} 栋 · 合计 ${mergedBuildings.size} 栋"
                                        if (!mapReadWarning.isNullOrBlank()) {
                                            captureNotice += "（底图查询提示：" + mapReadWarning + "）"
                                        }
                                    }
                                } catch (error: Exception) {
                                    errorMessage = "采集失败：" + (error.localizedMessage ?: "请检查网络后重试")
                                } finally {
                                    loading = false
                                    captureStatus = ""
                                }
                            }
                        },
                        enabled = selectedPoints.size >= 3 && !loading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Outlined.Terrain, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            if (loading) captureStatus.ifBlank { "采集中" } else "采集地形+地图建筑",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onSaveRegion,
                        enabled = selectedPoints.size >= 3,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("存为项目", fontSize = 12.sp)
                    }
                }

                elevationGrid?.let { grid ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(grid.summary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            val basemapCount = grid.buildings.count { it.source.startsWith("OpenFreeMap") }
                            val chinaCount = grid.buildings.count {
                                it.source.contains("3D-GloBFP") ||
                                    it.source.contains("China Urban Building Footprint Height Dataset")
                            }
                            val osmCount = grid.buildings.count { it.source.startsWith("OpenStreetMap") }
                            Text(
                                "平均 ${grid.average.roundToInt()} m · ${grid.sampleCount} 点 · ${grid.resolutionMeters.roundToInt()} m 网格",
                                fontSize = 10.sp,
                                color = Muted
                            )
                            Text(
                                "建筑：底图 ${basemapCount} · 中国 ${chinaCount} · OSM ${osmCount}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (grid.buildings.isEmpty()) MaterialTheme.colorScheme.error else Accent
                            )
                        }
                        TerrainGridPreview(grid, Modifier.width(112.dp).height(58.dp))
                    }
                } ?: run {
                    if (selectedPoints.size >= 3) {
                        val dimensions = regionDimensions(selectedPoints)
                        Text(
                            String.format(Locale.US, "范围 %.2f × %.2f km", dimensions.first, dimensions.second),
                            fontSize = 10.sp,
                            color = Muted
                        )
                    }
                }

                if (captureNotice.isNotBlank()) {
                    Text(captureNotice, color = Accent, fontSize = 10.sp)
                }
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }
                Text(
                    "OpenFreeMap © OpenMapTiles · OpenStreetMap / Nominatim · DEM © Mapzen",
                    fontSize = 9.sp,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun TerrainGridPreview(grid: ElevationGrid, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFFF0F3F7))
    ) {
        val cellWidth = size.width / grid.columns
        val cellHeight = size.height / grid.rows
        val range = (grid.maximum - grid.minimum).takeIf { it > 0.01 } ?: 1.0
        grid.values.forEachIndexed { row, values ->
            values.forEachIndexed { column, value ->
                if (value != null) {
                    val fraction = ((value - grid.minimum) / range).toFloat().coerceIn(0f, 1f)
                    drawRect(
                        color = terrainColor(fraction),
                        topLeft = Offset(column * cellWidth, row * cellHeight),
                        size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                    )
                }
            }
        }
    }
}

private fun terrainColor(value: Float): Color {
    val low = Color(0xFF79B8A0)
    val middle = Color(0xFFD5C17D)
    val high = Color(0xFF9D8069)
    return if (value < 0.55f) {
        blendColor(low, middle, value / 0.55f)
    } else {
        blendColor(middle, high, (value - 0.55f) / 0.45f)
    }
}

private fun blendColor(start: Color, end: Color, amount: Float): Color {
    val f = amount.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = 1f
    )
}

private fun updateSelectionOverlay(map: MapLibreMap, points: List<GeoCoordinate>) {
    val style = map.style ?: return
    (style.getSource(AREA_FILL_SOURCE) as? GeoJsonSource)?.setGeoJson(polygonGeoJson(points))
    (style.getSource(AREA_LINE_SOURCE) as? GeoJsonSource)?.setGeoJson(lineGeoJson(points))
    (style.getSource(AREA_POINTS_SOURCE) as? GeoJsonSource)?.setGeoJson(pointGeoJson(points))
}

private fun polygonGeoJson(points: List<GeoCoordinate>): String {
    if (points.size < 3) return EMPTY_FEATURES
    val closed = points + points.first()
    val coordinates = closed.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[$coordinates]]}}"""
}

private fun lineGeoJson(points: List<GeoCoordinate>): String {
    if (points.size < 2) return EMPTY_FEATURES
    val path = if (points.size >= 3) points + points.first() else points
    val coordinates = path.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

private fun pointGeoJson(points: List<GeoCoordinate>): String {
    if (points.isEmpty()) return EMPTY_FEATURES
    val features = points.mapIndexed { index, point ->
        """{"type":"Feature","properties":{"index":$index},"geometry":{"type":"Point","coordinates":[${point.longitude},${point.latitude}]}}"""
    }.joinToString(",")
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun regionDimensions(points: List<GeoCoordinate>): Pair<Double, Double> {
    if (points.isEmpty()) return 0.0 to 0.0
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    val centerLat = (south + north) / 2.0
    val centerLon = (west + east) / 2.0
    val width = haversineMeters(centerLat, west, centerLat, east) / 1000.0
    val height = haversineMeters(south, centerLon, north, centerLon) / 1000.0
    return width to height
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return radius * 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun isInsidePolygon(point: GeoCoordinate, polygon: List<GeoCoordinate>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].longitude
        val yi = polygon[i].latitude
        val xj = polygon[j].longitude
        val yj = polygon[j].latitude
        val crosses = ((yi > point.latitude) != (yj > point.latitude)) &&
            (point.longitude < (xj - xi) * (point.latitude - yi) / ((yj - yi) + 1e-12) + xi)
        if (crosses) inside = !inside
        j = i
    }
    return inside
}

private fun geocodePlace(query: String): GeoCoordinate {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val endpoint = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=$encoded")
    val connection = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 15000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "BlockWorld-Forge-Android/0.4.4 (https://github.com/fzdff114/BlockWorld-Forge)")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("地点搜索失败 HTTP $status")
        val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        val first = results.optJSONObject(0) ?: throw IOException("没有找到这个地点，请换一个名称")
        val latitude = first.optString("lat").toDoubleOrNull()
            ?: throw IOException("地点坐标无效")
        val longitude = first.optString("lon").toDoubleOrNull()
            ?: throw IOException("地点坐标无效")
        if (latitude !in -85.0..85.0 || longitude !in -180.0..180.0) {
            throw IOException("地点坐标超出地图范围")
        }
        return GeoCoordinate(latitude, longitude)
    } finally {
        connection.disconnect()
    }
}

private data class TerrariumPixel(
    val row: Int,
    val column: Int,
    val tileX: Int,
    val tileY: Int,
    val pixelX: Int,
    val pixelY: Int
)

private fun terrariumTilePosition(longitude: Double, latitude: Double, zoom: Int): Triple<Int, Int, Pair<Int, Int>> {
    val tileCount = 1 shl zoom
    val safeLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
    val longitudeWorld = ((longitude + 180.0) / 360.0 * tileCount).coerceIn(0.0, tileCount - 1e-9)
    val latRadians = Math.toRadians(safeLatitude)
    val mercator = (1.0 - ln(tan(latRadians) + 1.0 / cos(latRadians)) / Math.PI) / 2.0
    val latitudeWorld = (mercator * tileCount).coerceIn(0.0, tileCount - 1e-9)
    val tileX = floor(longitudeWorld).toInt()
    val tileY = floor(latitudeWorld).toInt()
    val px = ((longitudeWorld - tileX) * 256.0).toInt().coerceIn(0, 255)
    val py = ((latitudeWorld - tileY) * 256.0).toInt().coerceIn(0, 255)
    return Triple(tileX, tileY, px to py)
}

internal fun decodeTerrariumElevation(red: Int, green: Int, blue: Int): Double {
    return (red * 256.0 + green + blue / 256.0) - 32768.0
}

internal fun terrariumTilePixelIndex(pixel256: Int, tileDimension: Int): Int {
    val hasBuffer = tileDimension == 260 || tileDimension == 516
    val buffer = if (hasBuffer) 2 else 0
    val usefulPixels = tileDimension - buffer * 2
    return (buffer + (pixel256.coerceIn(0, 255) / 256.0 * usefulPixels))
        .toInt()
        .coerceIn(0, tileDimension - 1)
}

internal fun chooseTerrariumZoom(
    west: Double,
    east: Double,
    south: Double,
    north: Double,
    maximumTiles: Int = 196
): Int {
    require(west < east && south < north)
    require(maximumTiles in 1..256)
    // Do not silently fall back to very coarse z8 tiles: the resulting DEM would
    // create broad plateaus. If a large region cannot fit a useful zoom, ask for
    // a smaller selection instead of creating misleadingly flat terrain.
    for (zoom in 14 downTo 12) {
        val corners = listOf(
            terrariumTilePosition(west, north, zoom),
            terrariumTilePosition(east, north, zoom),
            terrariumTilePosition(west, south, zoom),
            terrariumTilePosition(east, south, zoom)
        )
        val tileWidth = corners.maxOf { it.first } - corners.minOf { it.first } + 1
        val tileHeight = corners.maxOf { it.second } - corners.minOf { it.second } + 1
        if (tileWidth.toLong() * tileHeight.toLong() <= maximumTiles.toLong()) return zoom
    }
    throw IOException("此区域过大，无法在保留地形细节的情况下采集。请缩小地图范围后重试。")
}

private fun downloadTerrariumTile(
    context: android.content.Context,
    x: Int,
    y: Int,
    zoom: Int
): Bitmap {
    val cacheRoot = File(context.filesDir, "dem-tile-cache").apply { mkdirs() }
    val cacheFile = File(cacheRoot, "${zoom}_${x}_${y}.png")

    if (cacheFile.isFile && cacheFile.length() > 128L) {
        val cached = BitmapFactory.decodeFile(cacheFile.absolutePath)
        if (cached != null && cached.width >= 256 && cached.height >= 256) return cached
        cached?.recycle()
        cacheFile.delete()
    }

    val tempFile = File(cacheRoot, cacheFile.name + ".part")
    val endpoint = URL("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$zoom/$x/$y.png")
    val connection = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 20000
        readTimeout = 30000
        setRequestProperty("Accept", "image/png")
        setRequestProperty("User-Agent", "BlockWorld-Forge-Android/0.4.4 (terrain tile cache)")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw IOException("DEM 瓦片请求失败 HTTP $status（z=$zoom x=$x y=$y）")
        }
        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output, 64 * 1024) }
        }
        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            ?: throw IOException("DEM 瓦片无法解码（z=$zoom x=$x y=$y）")
        if (bitmap.width < 256 || bitmap.height < 256) {
            bitmap.recycle()
            throw IOException("DEM 瓦片尺寸不正确")
        }
        if (!tempFile.renameTo(cacheFile)) {
            tempFile.copyTo(cacheFile, overwrite = true)
            tempFile.delete()
        }
        trimDemTileCache(cacheRoot)
        return bitmap
    } catch (error: Exception) {
        tempFile.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

@Synchronized
private fun trimDemTileCache(cacheRoot: File) {
    val files = cacheRoot.listFiles()?.filter { it.isFile && it.extension == "png" }.orEmpty()
    var bytes = files.sumOf { it.length() }
    val limit = 384L * 1024L * 1024L
    for (file in files.sortedBy { it.lastModified() }) {
        if (bytes <= limit) break
        val length = file.length()
        if (file.delete()) bytes -= length
    }
}

private fun parseOsmHeight(tags: JSONObject, key: String): Double? {
    val raw = tags.optString(key).trim()
    if (raw.isBlank() || raw == "null") return null
    val number = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").find(raw.replace(',', '.'))?.value
        ?.toDoubleOrNull()
    return number?.takeIf { it.isFinite() && it >= 0.0 }
}

private fun fetchOsmBuildings(
    context: android.content.Context,
    west: Double,
    east: Double,
    south: Double,
    north: Double
): List<OsmBuilding> {
    val cacheRoot = File(context.filesDir, "osm-building-cache").apply { mkdirs() }
    val cacheKey = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", west, east, south, north)
    val digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val cacheFile = File(cacheRoot, "osm-$digest.json")
    val cacheFresh = cacheFile.isFile &&
        cacheFile.length() > 2L &&
        System.currentTimeMillis() - cacheFile.lastModified() < 7L * 24L * 60L * 60L * 1000L

    val query = "[out:json][timeout:25][maxsize:33554432];(" +
        "way[\"building\"]($south,$west,$north,$east);" +
        "relation[\"building\"]($south,$west,$north,$east);" +
        "way[\"building:part\"]($south,$west,$north,$east);" +
        "relation[\"building:part\"]($south,$west,$north,$east);" +
        ");out tags geom qt;"
    var body: String? = if (cacheFresh) runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull() else null

    if (body == null) {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        var lastError: Exception? = null
        for (endpointUrl in endpoints) {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12000
                readTimeout = 35000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "BlockWorld-Forge-Android/0.4.4 (OpenStreetMap building import)")
            }
            try {
                val request = ("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(request) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val details = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IOException("OSM 建筑服务 HTTP $status " + details.take(180))
                }
                body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body!!)
                if (root.has("remark")) throw IOException(root.optString("remark"))
                cacheFile.writeText(body!!, Charsets.UTF_8)
                break
            } catch (error: Exception) {
                lastError = error
                body = null
            } finally {
                connection.disconnect()
            }
        }
        if (body == null) {
            throw IOException(lastError?.localizedMessage ?: "OSM 建筑数据暂不可用")
        }
    }

    val elements = JSONObject(body!!).optJSONArray("elements") ?: JSONArray()
    val buildings = ArrayList<OsmBuilding>()
    val seenFootprints = HashSet<String>()
    for (i in 0 until elements.length()) {
        if (buildings.size >= 20000) break
        val element = elements.optJSONObject(i) ?: continue
        val tags = element.optJSONObject("tags") ?: JSONObject()
        val height = parseOsmHeight(tags, "height")
            ?: (parseOsmHeight(tags, "building:levels")?.times(3.2))
            ?: 8.0
        val minHeight = parseOsmHeight(tags, "min_height")
            ?: (parseOsmHeight(tags, "building:min_level")?.times(3.2))
            ?: 0.0
        val name = tags.optString("name").takeIf { it.isNotBlank() && it != "null" }
        val geometries = mutableListOf<JSONArray>()
        val direct = element.optJSONArray("geometry")
        if (direct != null) {
            geometries.add(direct)
        } else {
            val members = element.optJSONArray("members") ?: continue
            for (memberIndex in 0 until members.length()) {
                val member = members.optJSONObject(memberIndex) ?: continue
                if (member.optString("type") != "way" || member.optString("role") == "inner") continue
                member.optJSONArray("geometry")?.let { geometries.add(it) }
            }
        }
        for (geometry in geometries) {
            if (buildings.size >= 20000) break
            val polygon = (0 until geometry.length()).mapNotNull { pointIndex ->
                val point = geometry.optJSONObject(pointIndex) ?: return@mapNotNull null
                val lat = point.optDouble("lat", Double.NaN)
                val lon = point.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) null else GeoCoordinate(lat, lon)
            }
            if (polygon.size >= 4) {
                val footprintKey = polygon.joinToString("|") {
                    String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude)
                }
                if (seenFootprints.add(footprintKey)) {
                    buildings.add(
                        OsmBuilding(
                            polygon = polygon,
                            heightMeters = height.coerceAtLeast(3.0),
                            minHeightMeters = minHeight.coerceAtLeast(0.0),
                            name = name
                        )
                    )
                }
            }
        }
    }
    return buildings
}

private fun fetchElevationGrid(
    context: android.content.Context,
    boundary: List<GeoCoordinate>,
    sampleSpacingMeters: Int = 30,
    onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
): ElevationGrid {
    require(boundary.size >= 3) { "At least three boundary points are required" }
    val south = boundary.minOf { it.latitude }
    val north = boundary.maxOf { it.latitude }
    val west = boundary.minOf { it.longitude }
    val east = boundary.maxOf { it.longitude }
    val centerLatitude = (north + south) / 2.0
    val centerLongitude = (east + west) / 2.0
    val widthMeters = haversineMeters(centerLatitude, west, centerLatitude, east)
    val depthMeters = haversineMeters(south, centerLongitude, north, centerLongitude)

    require(sampleSpacingMeters == 20 || sampleSpacingMeters == 30) { "采样间距只能为 20m 或 30m" }
    // 30 m keeps the fast default light; 20 m captures smoother slopes without
    // pretending the source DEM has more native accuracy than it really does.
    val maxGridSide = if (sampleSpacingMeters == 20) 384 else 320
    val columns = ceil(widthMeters / sampleSpacingMeters.toDouble()).toInt().coerceIn(2, maxGridSide)
    val rows = ceil(depthMeters / sampleSpacingMeters.toDouble()).toInt().coerceIn(2, maxGridSide)
    val resolutionMeters = max(widthMeters / columns, depthMeters / rows)
    val zoom = chooseTerrariumZoom(west, east, south, north)
    val matrix = MutableList(rows) { MutableList<Double?>(columns) { null } }
    val pixels = ArrayList<TerrariumPixel>()

    for (row in 0 until rows) {
        val latitude = north - ((row + 0.5) / rows.toDouble()) * (north - south)
        for (column in 0 until columns) {
            val longitude = west + ((column + 0.5) / columns.toDouble()) * (east - west)
            // Sample the complete bounding grid; export still masks every voxel to the chosen polygon.
            // Neighbor samples just outside the boundary prevent expensive nearest-point fallbacks at its edge.
            val tile = terrariumTilePosition(longitude, latitude, zoom)
            pixels.add(TerrariumPixel(row, column, tile.first, tile.second, tile.third.first, tile.third.second))
        }
    }
    if (pixels.isEmpty()) throw IOException("圈选范围内没有有效地形采样点")

    val tileKeys = pixels.map { it.tileX to it.tileY }.distinct()
    if (tileKeys.size > 196) throw IOException("当前区域需要下载过多 DEM 瓦片，请缩小圈选范围以保留地形细节")
    // Fetch OSM data in parallel with terrain tiles instead of waiting for two serial network phases.
    val executor = Executors.newFixedThreadPool(8)
    try {
        val buildingsFuture = executor.submit(Callable {
            try {
                fetchOsmBuildings(context, west, east, south, north) to null
            } catch (error: Exception) {
                emptyList<OsmBuilding>() to (error.localizedMessage ?: "建筑数据服务不可用")
            }
        })
        val samplesByTile = pixels.groupBy { it.tileX to it.tileY }
        val allValues = ArrayList<Double>(pixels.size)
        var completedTiles = 0
        // Eight downloads overlap; each decoded bitmap is released immediately after sampling.
        for (tileBatch in tileKeys.chunked(8)) {
            val futures = tileBatch.map { key ->
                key to executor.submit(Callable { downloadTerrariumTile(context, key.first, key.second, zoom) })
            }
            for ((key, future) in futures) {
                val bitmap = future.get()
                try {
                    for (sample in samplesByTile[key].orEmpty()) {
                        val pixelX = terrariumTilePixelIndex(sample.pixelX, bitmap.width)
                        val pixelY = terrariumTilePixelIndex(sample.pixelY, bitmap.height)
                        val pixel = bitmap.getPixel(pixelX, pixelY)
                        if (AndroidColor.alpha(pixel) == 0) continue
                        val value = decodeTerrariumElevation(
                            AndroidColor.red(pixel),
                            AndroidColor.green(pixel),
                            AndroidColor.blue(pixel)
                        )
                        if (value.isFinite()) {
                            matrix[sample.row][sample.column] = value
                            allValues.add(value)
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                completedTiles++
                onProgress("下载高程瓦片", completedTiles, tileKeys.size)
            }
        }
        if (allValues.isEmpty()) throw IOException("DEM 瓦片没有返回有效高程数据")
        onProgress("整理建筑轮廓", 0, 1)
        val buildingsAndError = buildingsFuture.get()
        val buildingWarning = buildingsAndError.second
            ?: if (buildingsAndError.first.isEmpty()) "该区域没有匹配到 OSM 建筑轮廓；导出前请确认是否接受无建筑地图" else null
        onProgress("采集完成", 1, 1)
        return ElevationGrid(
            rows = rows,
            columns = columns,
            values = matrix.map { it.toList() },
            minimum = allValues.minOrNull() ?: 0.0,
            maximum = allValues.maxOrNull() ?: 0.0,
            average = allValues.average(),
            sampleCount = allValues.size,
            resolutionMeters = resolutionMeters,
            demZoom = zoom,
            buildings = buildingsAndError.first,
            buildingError = buildingWarning
        )
    } finally {
        executor.shutdownNow()
    }
}


private const val PROJECT_PREFS = "atlas_local_projects"
private const val PROJECTS_KEY = "projects_json"

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> bytes.toString() + " B"
}

private fun loadProjects(context: android.content.Context): List<WorldProject> {
    return try {
        val raw = context.getSharedPreferences(PROJECT_PREFS, android.content.Context.MODE_PRIVATE)
            .getString(PROJECTS_KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            WorldProject(
                name = item.optString("name", "未命名项目"),
                size = item.optInt("size", 10000),
                regionSummary = item.optString("regionSummary").takeIf { it.isNotBlank() && it != "null" },
                elevationSummary = item.optString("elevationSummary").takeIf { it.isNotBlank() && it != "null" },
                regionDataJson = item.optString("regionDataJson").takeIf { it.isNotBlank() && it != "null" },
                metersPerBlock = item.optInt("metersPerBlock", 4).let { if (it in listOf(1, 2, 4, 8, 16)) it else 4 },
                verticalExaggeration = item.optDouble("verticalExaggeration", 1.0).coerceIn(1.0, 3.0)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveProjects(context: android.content.Context, projects: List<WorldProject>) {
    val array = JSONArray()
    projects.forEach { project ->
        val item = JSONObject()
            .put("name", project.name)
            .put("size", project.size)
            .put("regionSummary", project.regionSummary ?: JSONObject.NULL)
            .put("elevationSummary", project.elevationSummary ?: JSONObject.NULL)
            .put("regionDataJson", project.regionDataJson ?: JSONObject.NULL)
            .put("metersPerBlock", project.metersPerBlock)
            .put("verticalExaggeration", project.verticalExaggeration)
        array.put(item)
    }
    context.getSharedPreferences(PROJECT_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(PROJECTS_KEY, array.toString())
        .apply()
}

private fun mergeBuildingSources(
    preferred: List<OsmBuilding>,
    fallback: List<OsmBuilding>
): List<OsmBuilding> {
    val output = ArrayList<OsmBuilding>()
    val centroids = HashSet<String>()
    fun key(building: OsmBuilding): String {
        val longitude = building.polygon.map { it.longitude }.average()
        val latitude = building.polygon.map { it.latitude }.average()
        return String.format(Locale.US, "%.5f,%.5f", longitude, latitude)
    }
    for (building in preferred) {
        if (building.polygon.size >= 3 && output.size < 20_000 && centroids.add(key(building))) {
            output.add(building)
        }
    }
    for (building in fallback) {
        if (output.size >= 20_000) break
        if (building.polygon.size >= 3 && centroids.add(key(building))) output.add(building)
    }
    return output
}

private fun regionDraftJson(region: RegionDraft): String {
    val root = JSONObject()
    root.put("format", "atlas-region-heightmap-v1")
    root.put("elevationSource", "Mapzen Terrain Tiles (Terrarium) via AWS")
    region.elevation?.let { grid ->
        root.put("elevationResolutionMeters", grid.resolutionMeters)
        root.put("elevationZoom", grid.demZoom)
        root.put(
            "buildingSource",
            grid.buildings.map { it.source }.distinct().joinToString(" + ").ifBlank { "OpenStreetMap" }
        )
        val buildings = JSONArray()
        grid.buildings.forEach { building ->
            val item = JSONObject()
            val polygon = JSONArray()
            building.polygon.forEach { point ->
                polygon.put(JSONArray().put(point.longitude).put(point.latitude))
            }
            item.put("boundaryLonLat", polygon)
            item.put("heightMeters", building.heightMeters)
            item.put("minHeightMeters", building.minHeightMeters)
            item.put("name", building.name ?: JSONObject.NULL)
            item.put("source", building.source)
            buildings.put(item)
        }
        root.put("osmBuildings", buildings)
        root.put("buildingCount", grid.buildings.size)
    }
    root.put("regionWidthKm", region.widthKm)
    root.put("regionHeightKm", region.heightKm)

    val boundary = JSONArray()
    region.points.forEach { point ->
        boundary.put(JSONArray().put(point.longitude).put(point.latitude))
    }
    root.put("boundaryLonLat", boundary)

    region.elevation?.let { grid ->
        root.put("rows", grid.rows)
        root.put("columns", grid.columns)
        root.put("minimumElevationMeters", grid.minimum)
        root.put("maximumElevationMeters", grid.maximum)
        root.put("averageElevationMeters", grid.average)
        root.put("sampleCount", grid.sampleCount)
        val matrix = JSONArray()
        grid.values.forEach { row ->
            val jsonRow = JSONArray()
            row.forEach { value -> jsonRow.put(value ?: JSONObject.NULL) }
            matrix.put(jsonRow)
        }
        root.put("elevationMeters", matrix)
    }
    return root.toString()
}

@Composable
private fun TerrainScreen(
    projectName: String,
    onProjectName: (String) -> Unit,
    settings: ProceduralWorldSettings,
    onSettings: (ProceduralWorldSettings) -> Unit,
    onCreateProject: () -> Unit
) {
    val preview = remember(settings) { ProceduralWorldGenerator.previewHeights(settings, 52) }
    val estimate = settings.estimatedArchiveBytes()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF142842),
            shape = RoundedCornerShape(17.dp)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PROCEDURAL WORLD ENGINE", color = Color(0xFFB8CCF3), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("壮丽结构生成器", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("完全自行构建地形，不再依赖地图、外部高程或建筑采集。", color = Color(0xFFD5DFEF), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallStatusPill("29 种群系", Color(0xFF2A4A72))
                    SmallStatusPill("洞穴与矿脉", Color(0xFF2A4A72))
                    SmallStatusPill("多种树形", Color(0xFF2A4A72))
                }
            }
        }
        SettingsCard("世界地形预览", "根据当前种子实时重绘高度分布") {
            ProceduralTerrainPreview(preview)
            Text("种子：" + settings.seed + " · 起伏：" + settings.relief + " · 山脉崎岖：" + settings.mountainRuggedness, fontSize = 10.sp, color = Muted)
        }
        SettingsCard("世界基本参数", "边长与细节档会影响生成时间和压缩包体积") {
            OutlinedTextField(
                value = projectName,
                onValueChange = onProjectName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("世界名称") },
                singleLine = true
            )
            var seedText by remember(settings.seed) { mutableStateOf(settings.seed.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || it == '-' }.take(20)
                        seedText = cleaned
                        cleaned.toLongOrNull()?.let { onSettings(settings.copy(seed = it)) }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("世界种子 Seed") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedButton(onClick = {
                    val seed = System.currentTimeMillis() xor (System.nanoTime() shl 1)
                    seedText = seed.toString()
                    onSettings(settings.copy(seed = seed))
                }) { Text("随机种子") }
            }
            Text("世界边长：" + settings.worldSize + " 方块", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(128, 192, 256, 320, 384, 512).forEach { size ->
                    FilterChip(
                        selected = settings.worldSize == size,
                        onClick = { onSettings(settings.copy(worldSize = size)) },
                        label = { Text(size.toString() + "²", fontSize = 10.sp) }
                    )
                }
            }
            Text("细节档位 / 文件大小", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("compact" to "低体积", "standard" to "标准", "high" to "高细节").forEach { item ->
                    FilterChip(
                        selected = settings.detailPreset == item.first,
                        onClick = { onSettings(settings.copy(detailPreset = item.first)) },
                        label = { Text(item.second, fontSize = 10.sp) }
                    )
                }
            }
            Text("预计 .mcworld：" + formatBytes(estimate) + "（估算值）", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Bold)
            Text("实际文件大小会随地形、洞穴、植被和结构数量变化。", fontSize = 9.sp, color = Muted)
        }
        SettingsCard("山脉与高原", "调整地形的壮阔程度和轮廓") {
            IntSetting("高度起伏", settings.relief, "平缓", "巨型山脉") { onSettings(settings.copy(relief = it)) }
            IntSetting("山脉崎岖度", settings.mountainRuggedness, "圆润山丘", "尖峭山脊") { onSettings(settings.copy(mountainRuggedness = it)) }
            IntSetting("高原比例", settings.plateauAmount, "连续山脉", "桌状高原") { onSettings(settings.copy(plateauAmount = it)) }
            IntSetting("海平面", settings.waterLevel, "低水位", "高水位", 16, 64) { onSettings(settings.copy(waterLevel = it)) }
            SwitchRow("海洋与湖泊", settings.enableWater) { onSettings(settings.copy(enableWater = it)) }
        }
        SettingsCard("群系与森林", "至少 20 种，最多启用完整群系列表") {
            Text("启用群系数量：" + settings.biomeCount + " / " + ProceduralWorldGenerator.BIOMES.size, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Slider(
                value = settings.biomeCount.toFloat(),
                onValueChange = { onSettings(settings.copy(biomeCount = it.roundToInt())) },
                valueRange = 20f..ProceduralWorldGenerator.BIOMES.size.toFloat(),
                steps = (ProceduralWorldGenerator.BIOMES.size - 21).coerceAtLeast(0)
            )
            IntSetting("树木密度", settings.treeDensity, "稀疏林地", "茂密森林") { onSettings(settings.copy(treeDensity = it)) }
            SwitchRow("生成树木与不同树冠", settings.enableTrees) { onSettings(settings.copy(enableTrees = it)) }
            Text("橡树、白桦、云杉、巨型云杉、丛林树、金合欢、深色橡木、红树、樱花树等造型按群系分布。", fontSize = 10.sp, color = Muted)
        }
        SettingsCard("地下洞穴与矿脉", "生成地下通道、洞室和分层矿石") {
            IntSetting("洞穴密度", settings.caveDensity, "少量洞穴", "密集洞系") { onSettings(settings.copy(caveDensity = it)) }
            IntSetting("矿脉丰富度", settings.oreRichness, "稀有矿物", "富矿区域") { onSettings(settings.copy(oreRichness = it)) }
            SwitchRow("生成洞穴", settings.enableCaves) { onSettings(settings.copy(enableCaves = it)) }
            SwitchRow("生成矿石", settings.enableOres) { onSettings(settings.copy(enableOres = it)) }
            Text("包括煤、铁、铜、金、红石、青金石、钻石和绿宝石。", fontSize = 10.sp, color = Muted)
        }
        SettingsCard("遗迹与大型结构", "原创结构设计，采用基岩版原版方块风格") {
            IntSetting("结构出现频率", settings.structureDensity, "稀少遗迹", "遗迹密集") { onSettings(settings.copy(structureDensity = it)) }
            SwitchRow("生成遗迹与结构", settings.enableStructures) { onSettings(settings.copy(enableStructures = it)) }
            val options = listOf(
                "village" to "村庄群落",
                "desert_temple" to "沙漠神殿",
                "jungle_temple" to "丛林神庙",
                "ruined_portal" to "破损传送门",
                "mineshaft" to "地下矿道",
                "stronghold" to "地下要塞",
                "ocean_ruin" to "海底遗迹",
                "watchtower" to "山地瞭望塔"
            )
            options.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = settings.structureTypes.contains(item.first),
                            onClick = {
                                val next = settings.structureTypes.toMutableSet()
                                if (!next.add(item.first)) next.remove(item.first)
                                onSettings(settings.copy(structureTypes = next))
                            },
                            label = { Text(item.second, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth(), color = AccentSoft, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("生成方案", fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold)
                Text("配置完成后保存生成方案，再进入“导出”写入 LevelDB。世界由本地程序直接构建，不需要地图服务。", fontSize = 10.sp, color = Ink)
                Button(onClick = onCreateProject, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("保存世界生成方案")
                }
            }
        }
    }
}

@Composable
private fun ProceduralTerrainPreview(heights: DoubleArray) {
    Canvas(
        Modifier.fillMaxWidth().height(150.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF1C3148))
    ) {
        val side = kotlin.math.sqrt(heights.size.toDouble()).toInt().coerceAtLeast(1)
        val cellWidth = size.width / side
        val cellHeight = size.height / side
        heights.forEachIndexed { index, height ->
            val level = ((height + 42.0) / 238.0).toFloat().coerceIn(0f, 1f)
            val color = when {
                level < 0.18f -> Color(0xFF183B59)
                level < 0.32f -> Color(0xFF397A83)
                level < 0.48f -> Color(0xFF6E976B)
                level < 0.66f -> Color(0xFF9E9C68)
                level < 0.82f -> Color(0xFF8B806C)
                level < 0.94f -> Color(0xFFB4B6B0)
                else -> Color(0xFFE4E8EC)
            }
            val x = index % side
            val y = index / side
            drawRect(color, Offset(x * cellWidth, y * cellHeight), Size(cellWidth + 0.5f, cellHeight + 0.5f))
        }
    }
}

@Composable
private fun SmallStatusPill(label: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(20.dp)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(description, fontSize = 10.sp, color = Muted)
            HorizontalDivider(color = DividerColor)
            content()
        }
    }
}

@Composable
private fun IntSetting(
    title: String,
    value: Int,
    lowLabel: String,
    highLabel: String,
    minimum: Int = 0,
    maximum: Int = 100,
    onValue: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(value.toString(), fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.toFloat(), onValueChange = { onValue(it.roundToInt().coerceIn(minimum, maximum)) }, valueRange = minimum.toFloat()..maximum.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lowLabel, fontSize = 9.sp, color = Muted)
            Text(highLabel, fontSize = 9.sp, color = Muted)
        }
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
private fun AssetsScreen(
    importedCount: Int,
    importStatus: String,
    selectedDataset: String,
    onSelectedDataset: (String) -> Unit,
    importing: Boolean,
    onImportChinaBuildings: () -> Unit,
    onClearImported: () -> Unit
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("素材与建筑数据", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("中国逐栋建筑高度数据", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    if (selectedDataset == "3D-GloBFP") {
                        "3D-GloBFP：中国省级逐栋建筑轮廓与估算高度（米），CC BY 4.0。"
                    } else {
                        "中国十城 2018：北京、长沙、成都、杭州、南京、上海、沈阳、天津、武汉、西安的逐栋建筑面和估算高度；请遵循数据门户使用条款。"
                    },
                    fontSize = 11.sp,
                    color = Muted
                )
                Text(
                    "步骤：先在“地图”页圈选目标范围；下载对应省份/城市的单个 Shapefile 图层；把同名 .shp、.dbf、.shx、.prj 放进一个 ZIP；返回此处导入。不要下载几十 GB 的整个全球合包。",
                    fontSize = 11.sp,
                    color = Ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedDataset == "3D-GloBFP",
                        onClick = { onSelectedDataset("3D-GloBFP") },
                        label = { Text("3D-GloBFP（推荐）", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedDataset == "China Urban Building Footprint Height Dataset (2018)",
                        onClick = { onSelectedDataset("China Urban Building Footprint Height Dataset (2018)") },
                        label = { Text("中国十城 2018", fontSize = 10.sp) }
                    )
                }
                Button(
                    onClick = onImportChinaBuildings,
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (importing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("正在导入…")
                    } else {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("导入中国建筑高度 ZIP")
                    }
                }
                Text(
                    "当前已载入：${importedCount} 栋",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (importedCount > 0) Accent else Muted
                )
                Text(importStatus, fontSize = 10.sp, color = if (importStatus.startsWith("导入失败")) MaterialTheme.colorScheme.error else Muted)
                if (importedCount > 0) {
                    TextButton(onClick = onClearImported) { Text("清除已导入建筑") }
                }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://doi.org/10.5281/zenodo.15459025"))) }) {
                    Text("打开 3D-GloBFP 数据集与省级文件索引")
                }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.geodata.cn/main/face_science_detail?guid=135869909769675"))) }) {
                    Text("中国十城建筑高度数据（2018）")
                }
                Text(
                    "提示：十城数据页面未明确列出与 3D-GloBFP 相同的 CC BY 许可；请先核对数据门户使用条款。导入功能只读取本地 ZIP，不会把该数据上传或随 APK 再分发。",
                    fontSize = 10.sp,
                    color = Muted
                )
            }
        }
        AssetRow(Icons.Outlined.Forest, "群系")
        AssetRow(Icons.Outlined.AccountTree, "结构与遗迹")
        AssetRow(Icons.Outlined.Layers, "地表规则")
        Text("中国建筑数据导入后，采集高程并保存项目，建筑轮廓和高度会写入最终 .mcworld。", fontSize = 11.sp, color = Muted)
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
        }
    }
}

@Composable
private fun ExportScreen(
    projects: List<WorldProject>,
    exporting: Boolean,
    progress: Float,
    status: String,
    onExport: (WorldProject) -> Unit
) {
    val available = projects.filter { !it.regionDataJson.isNullOrBlank() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("导出", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (available.isEmpty()) {
            Text("先在生成器配置世界，再保存生成方案。", fontSize = 12.sp, color = Muted)
        } else {
            available.forEach { project ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, DividerColor)
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Terrain, contentDescription = null, tint = Accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(project.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                            "比例 1:" + project.metersPerBlock + " · 地形起伏 " + project.verticalExaggeration + "×",
                            fontSize = 10.sp,
                            color = Muted
                        )
                        }
                        OutlinedButton(
                            onClick = { onExport(project) },
                            enabled = !exporting,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("生成世界", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (exporting) {
            LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Text(
                "按 8×8 区块逐批生成并提交，进度 " + (progress * 100).toInt() + "%",
                fontSize = 10.sp,
                color = Muted
            )
        }
        if (status.isNotBlank()) {
            Text(status, fontSize = 11.sp, color = if (status.startsWith("导出失败")) MaterialTheme.colorScheme.error else Accent)
        }
    }
}

@Composable
private fun NewProjectDialog(
    regionSummary: String?,
    hasElevation: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, Double) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableIntStateOf(10000) }
    var metersPerBlock by rememberSaveable { mutableIntStateOf(4) }
    var verticalExaggeration by rememberSaveable { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建项目", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true
                )
                SectionLabel("现实比例")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(1, 2, 4, 8, 16).forEach { scale ->
                        FilterChip(
                            selected = metersPerBlock == scale,
                            onClick = { metersPerBlock = scale },
                            label = { Text("1:" + scale, fontSize = 10.sp) }
                        )
                    }
                }
                Text("1 个方块代表 " + metersPerBlock + " 米", fontSize = 10.sp, color = Muted)
                SectionLabel("垂直地形起伏")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1f, 1.5f, 2f, 3f).forEach { factor ->
                        FilterChip(
                            selected = verticalExaggeration == factor,
                            onClick = { verticalExaggeration = factor },
                            label = { Text("${factor}×", fontSize = 10.sp) }
                        )
                    }
                }
                Text(
                    "默认 1×：水平和垂直按同一现实米/方块比例；仅当地形高差超过游戏高度范围时自动压缩",
                    fontSize = 10.sp,
                    color = Muted
                )
                if (regionSummary != null) {
                    Text(
                        if (hasElevation) "已附加区域与高程：" + regionSummary else "已附加区域：" + regionSummary,
                        fontSize = 10.sp,
                        color = Accent
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, size, metersPerBlock, verticalExaggeration.toDouble()) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = Color.White
    )
}
