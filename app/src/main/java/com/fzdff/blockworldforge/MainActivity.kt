package com.fzdff.blockworldforge

import android.os.Bundle
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
    NavPage("projects", "项目", Icons.Outlined.FolderOpen),
    NavPage("map", "地图", Icons.Outlined.Map),
    NavPage("terrain", "地形", Icons.Outlined.Terrain),
    NavPage("assets", "素材", Icons.Outlined.Layers),
    NavPage("export", "导出", Icons.Outlined.FileUpload)
)

private val mapSizes = listOf(4000, 6000, 10000, 15000, 20000)

private data class GeoCoordinate(val latitude: Double, val longitude: Double)
private data class OsmBuilding(
    val polygon: List<GeoCoordinate>,
    val heightMeters: Double,
    val minHeightMeters: Double,
    val name: String?
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
        get() = "海拔 ${minimum.roundToInt()}–${maximum.roundToInt()} m"
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
    val metersPerBlock: Int = 4
)

class MainActivity : ComponentActivity() {
    private lateinit var atlasMapView: MapView
    private val atlasMapState = mutableStateOf<MapLibreMap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(applicationContext)
        atlasMapView = MapView(this)
        atlasMapView.onCreate(savedInstanceState)
        atlasMapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(35.8617, 104.1954))
                .zoom(4.2)
                .build()

            map.setStyle(Style.Builder().fromUri(BASE_STYLE_URI)) { style ->
                style.addSource(GeoJsonSource(AREA_FILL_SOURCE, EMPTY_FEATURES))
                style.addSource(GeoJsonSource(AREA_LINE_SOURCE, EMPTY_FEATURES))
                style.addSource(GeoJsonSource(AREA_POINTS_SOURCE, EMPTY_FEATURES))

                style.addLayer(
                    FillLayer("atlas-area-fill-layer", AREA_FILL_SOURCE).withProperties(
                        fillColor(AndroidColor.rgb(53, 106, 230)),
                        fillOpacity(0.18f)
                    )
                )
                style.addLayer(
                    LineLayer("atlas-area-line-layer", AREA_LINE_SOURCE).withProperties(
                        lineColor(AndroidColor.rgb(35, 92, 220)),
                        lineWidth(3.0f)
                    )
                )
                style.addLayer(
                    CircleLayer("atlas-area-points-layer", AREA_POINTS_SOURCE).withProperties(
                        circleRadius(5.5f),
                        circleColor(AndroidColor.rgb(35, 92, 220)),
                        circleStrokeColor(AndroidColor.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                )
                atlasMapState.value = map
            }
        }

        setContent {
            AtlasTheme {
                AtlasApp(
                    mapView = atlasMapView,
                    map = atlasMapState.value
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::atlasMapView.isInitialized) atlasMapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::atlasMapView.isInitialized) atlasMapView.onResume()
    }

    override fun onPause() {
        if (::atlasMapView.isInitialized) atlasMapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::atlasMapView.isInitialized) atlasMapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::atlasMapView.isInitialized) atlasMapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::atlasMapView.isInitialized) atlasMapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::atlasMapView.isInitialized) atlasMapView.onDestroy()
        super.onDestroy()
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
private fun AtlasApp(mapView: MapView, map: MapLibreMap?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPage by rememberSaveable { mutableStateOf("projects") }
    var showNewProject by rememberSaveable { mutableStateOf(false) }
    val projects = remember(context) {
        mutableStateListOf<WorldProject>().apply { addAll(loadProjects(context)) }
    }

    val selectedPoints = remember { mutableStateListOf<GeoCoordinate>() }
    var elevationGrid by remember { mutableStateOf<ElevationGrid?>(null) }
    var pendingRegion by remember { mutableStateOf<RegionDraft?>(null) }
    var pendingExportProject by remember { mutableStateOf<WorldProject?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val project = pendingExportProject
        pendingExportProject = null
        if (uri != null && project != null && !project.regionDataJson.isNullOrBlank()) {
            exportBusy = true
            exportProgress = 0f
            exportStatus = ""
            scope.launch {
                try {
                    val report = withContext(Dispatchers.IO) {
                        BedrockWorldExporter.export(
                            context = context,
                            worldName = project.name,
                            regionJson = project.regionDataJson!!,
                            metersPerBlock = project.metersPerBlock,
                            outputUri = uri
                        ) { completed, total ->
                            scope.launch {
                                exportProgress = completed.toFloat() / total.coerceAtLeast(1)
                                exportStatus = "分批写入区块：" + completed + " / " + total
                            }
                        }
                    }
                    exportProgress = 1f
                    exportStatus = "已导出：" + report.widthBlocks + " × " + report.depthBlocks +
                        " 格 · " + report.chunkCount + " 个区块 · " + report.buildingCount + " 栋建筑"
                } catch (error: Exception) {
                    exportStatus = "导出失败：" + (error.localizedMessage ?: "未知错误")
                } finally {
                    exportBusy = false
                }
            }
        }
    }

    var worldSize by rememberSaveable { mutableIntStateOf(10000) }
    var terrainStyle by rememberSaveable { mutableStateOf("自然") }
    var useElevation by rememberSaveable { mutableStateOf(true) }
    var useWater by rememberSaveable { mutableStateOf(true) }
    var useStructures by rememberSaveable { mutableStateOf(false) }

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
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PageBackground)
        ) {
            when (currentPage) {
                "projects" -> ProjectsScreen(
                    projects = projects,
                    onCreate = {
                        pendingRegion = null
                        showNewProject = true
                    }
                )
                "map" -> MapScreen(
                    mapView = mapView,
                    map = map,
                    selectedPoints = selectedPoints,
                    elevationGrid = elevationGrid,
                    onAddPoint = { point ->
                        if (selectedPoints.size < 50) {
                            selectedPoints.add(point)
                            elevationGrid = null
                        }
                    },
                    onSetPoints = { points ->
                        selectedPoints.clear()
                        selectedPoints.addAll(points)
                        elevationGrid = null
                    },
                    onUndo = {
                        if (selectedPoints.isNotEmpty()) selectedPoints.removeAt(selectedPoints.lastIndex)
                        elevationGrid = null
                    },
                    onClear = {
                        selectedPoints.clear()
                        elevationGrid = null
                    },
                    onElevationReady = { elevationGrid = it },
                    onSaveRegion = {
                        if (selectedPoints.size >= 3) {
                            val bounds = regionDimensions(selectedPoints)
                            pendingRegion = RegionDraft(
                                selectedPoints.toList(),
                                bounds.first,
                                bounds.second,
                                elevationGrid
                            )
                            showNewProject = true
                        }
                    }
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
                "export" -> ExportScreen(
                    projects = projects,
                    exporting = exportBusy,
                    progress = exportProgress,
                    status = exportStatus,
                    onExport = { project ->
                        pendingExportProject = project
                        val safeName = project.name.filter {
                            it.isLetterOrDigit() || it == '-' || it == '_' || it == ' '
                        }.trim().ifBlank { "Atlas" }
                        exportLauncher.launch(safeName + ".mcworld")
                    }
                )
            }
        }
    }

    if (showNewProject) {
        NewProjectDialog(
            regionSummary = pendingRegion?.summary,
            hasElevation = pendingRegion?.elevation != null,
            onDismiss = { showNewProject = false },
            onCreate = { name, size, metersPerBlock ->
                val region = pendingRegion
                projects.add(
                    0,
                    WorldProject(
                        name = name.ifBlank { "未命名项目" },
                        size = size,
                        regionSummary = region?.summary,
                        elevationSummary = region?.elevation?.summary,
                        regionDataJson = region?.let { regionDraftJson(it) },
                        metersPerBlock = metersPerBlock
                    )
                )
                saveProjects(context, projects)
                worldSize = size
                pendingRegion = null
                showNewProject = false
                currentPage = "projects"
            }
        )
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
                        "范围 " + project.regionSummary + " · 比例 1:" + project.metersPerBlock,
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
    var errorMessage by remember { mutableStateOf("") }
    var show3d by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf("矩形") }
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val snapshot = selectedPoints.toList()
                            loading = true
                            errorMessage = ""
                            scope.launch {
                                try {
                                    captureStatus = "准备下载高程瓦片…"
                                    val result = withContext(Dispatchers.IO) {
                                        fetchElevationGrid(snapshot) { phase, completed, total ->
                                            scope.launch {
                                                captureStatus = if (total > 1) {
                                                    phase + " " + completed + "/" + total
                                                } else {
                                                    phase
                                                }
                                            }
                                        }
                                    }
                                    onElevationReady(result)
                                    if (result.buildingError != null) {
                                        errorMessage = "地形已采集；建筑数据未取得：" + result.buildingError
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
                            if (loading) captureStatus.ifBlank { "采集中" } else "采集地形+建筑",
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
                            Text(
                                "平均 ${grid.average.roundToInt()} m · ${grid.sampleCount} 点 · ${grid.resolutionMeters.roundToInt()} m 网格 · 建筑 ${grid.buildings.size} 栋",
                                fontSize = 10.sp,
                                color = Muted
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
        setRequestProperty("User-Agent", "Atlas-Android/0.4.1 (https://github.com/fzdff114/BlockWorld-Forge)")
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

private fun chooseTerrariumZoom(west: Double, east: Double, south: Double, north: Double): Int {
    // Zoom 14 is enough for a ~30 m terrain grid; avoid downloading over-zoomed tiles.
    for (zoom in 14 downTo 8) {
        val corners = listOf(
            terrariumTilePosition(west, north, zoom),
            terrariumTilePosition(east, north, zoom),
            terrariumTilePosition(west, south, zoom),
            terrariumTilePosition(east, south, zoom)
        )
        val tileWidth = corners.maxOf { it.first } - corners.minOf { it.first } + 1
        val tileHeight = corners.maxOf { it.second } - corners.minOf { it.second } + 1
        if (tileWidth.toLong() * tileHeight.toLong() <= 64L) return zoom
    }
    return 8
}

private fun downloadTerrariumTile(x: Int, y: Int, zoom: Int): Bitmap {
    val endpoint = URL("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/$zoom/$x/$y.png")
    val connection = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 20000
        readTimeout = 30000
        setRequestProperty("Accept", "image/png")
        setRequestProperty("User-Agent", "Atlas-Android/0.4.1 (real-world Bedrock map)")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw IOException("DEM 瓦片请求失败 HTTP $status（z=$zoom x=$x y=$y）")
        }
        val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
            ?: throw IOException("DEM 瓦片无法解码（z=$zoom x=$x y=$y）")
        if (bitmap.width < 256 || bitmap.height < 256) {
            bitmap.recycle()
            throw IOException("DEM 瓦片尺寸不正确")
        }
        return bitmap
    } finally {
        connection.disconnect()
    }
}

private fun parseOsmHeight(tags: JSONObject, key: String): Double? {
    val raw = tags.optString(key).trim()
    if (raw.isBlank() || raw == "null") return null
    val number = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").find(raw.replace(',', '.'))?.value
        ?.toDoubleOrNull()
    return number?.takeIf { it.isFinite() && it >= 0.0 }
}

private fun fetchOsmBuildings(west: Double, east: Double, south: Double, north: Double): List<OsmBuilding> {
    val query = "[out:json][timeout:25][maxsize:33554432];(" +
        "way[\"building\"]($south,$west,$north,$east);" +
        "relation[\"building\"]($south,$west,$north,$east);" +
        ");out tags geom qt;"
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
            setRequestProperty("User-Agent", "Atlas-Android/0.4.1 (OpenStreetMap building import)")
        }
        try {
            val request = ("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(request) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val details = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("OSM 建筑服务 HTTP $status " + details.take(180))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val elements = JSONObject(body).optJSONArray("elements") ?: JSONArray()
            val buildings = ArrayList<OsmBuilding>()
            for (i in 0 until elements.length()) {
                if (buildings.size >= 10000) break
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
            return buildings
        } catch (error: Exception) {
            lastError = error
        } finally {
            connection.disconnect()
        }
    }
    throw IOException(lastError?.localizedMessage ?: "OSM 建筑数据暂不可用")
}

private fun fetchElevationGrid(
    boundary: List<GeoCoordinate>,
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

    // Source data is mainly ~30 m SRTM away from higher-resolution regional datasets.
    // A 30 m grid avoids spending time on oversampling the same native elevation cells.
    val columns = ceil(widthMeters / 30.0).toInt().coerceIn(2, 320)
    val rows = ceil(depthMeters / 30.0).toInt().coerceIn(2, 320)
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
    if (tileKeys.size > 64) throw IOException("当前区域需要下载过多 DEM 瓦片，请缩小圈选范围")
    // Fetch OSM data in parallel with terrain tiles instead of waiting for two serial network phases.
    val executor = Executors.newFixedThreadPool(8)
    try {
        val buildingsFuture = executor.submit(Callable {
            try {
                fetchOsmBuildings(west, east, south, north) to null
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
                key to executor.submit(Callable { downloadTerrariumTile(key.first, key.second, zoom) })
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
            buildingError = buildingsAndError.second
        )
    } finally {
        executor.shutdownNow()
    }
}


private const val PROJECT_PREFS = "atlas_local_projects"
private const val PROJECTS_KEY = "projects_json"

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
                metersPerBlock = item.optInt("metersPerBlock", 4).let { if (it in listOf(1, 2, 4, 8, 16)) it else 4 }
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
        array.put(item)
    }
    context.getSharedPreferences(PROJECT_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(PROJECTS_KEY, array.toString())
        .apply()
}

private fun regionDraftJson(region: RegionDraft): String {
    val root = JSONObject()
    root.put("format", "atlas-region-heightmap-v1")
    root.put("elevationSource", "Mapzen Terrain Tiles (Terrarium) via AWS")
    region.elevation?.let { grid ->
        root.put("elevationResolutionMeters", grid.resolutionMeters)
        root.put("elevationZoom", grid.demZoom)
        root.put("buildingSource", "OpenStreetMap / Overpass API")
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
                "${worldSize / 1000}K × ${worldSize / 1000}K",
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
        Text("素材编辑暂未开放", fontSize = 11.sp, color = Muted)
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
    val available = projects.filter {
        !it.regionDataJson.isNullOrBlank() && it.regionDataJson!!.contains("elevationMeters")
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("导出", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (available.isEmpty()) {
            Text("先圈选现实区域并采集高程", fontSize = 12.sp, color = Muted)
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
                            Text("比例 1:" + project.metersPerBlock, fontSize = 10.sp, color = Muted)
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
    onCreate: (String, Int, Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableIntStateOf(10000) }
    var metersPerBlock by rememberSaveable { mutableIntStateOf(4) }

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
            TextButton(onClick = { onCreate(name, size, metersPerBlock) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = Color.White
    )
}
