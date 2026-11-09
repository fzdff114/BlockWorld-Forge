package com.fzdff.blockworldforge

import android.content.Context
import android.net.Uri
import org.iq80.leveldb.CompressionType
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.WriteBatch
import org.iq80.leveldb.impl.Iq80DBFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class BedrockExportReport(
    val widthBlocks: Int,
    val depthBlocks: Int,
    val chunkCount: Int,
    val archiveBytes: Long,
    val metersPerBlock: Int,
    val minimumElevation: Double,
    val maximumElevation: Double,
    val verticalMetersPerBlock: Double,
    val buildingCount: Int
)

internal object BedrockWorldExporter {
    private const val MIN_Y = -64
    private const val MAX_Y = 319
    private const val SEA_LEVEL = 8
    private const val CHUNK_VERSION = 41
    private const val SUB_CHUNK_VERSION = 9
    private const val BLOCK_STATE_VERSION = 18168865
    private const val NETWORK_VERSION = 2193 // Bedrock 1.26.52.3
    internal const val WORLD_GENERATOR_INFINITE = 1
    // A 5,044 × 4,759 map is about 94,168 Minecraft chunks.
    // Keep a mobile-safe ceiling, but do not reject normal large maps at 512 blocks.
    private const val MAX_CHUNKS = 100_000L
    private const val MAX_EDGE_BLOCKS = 8192
    private const val TILE_CHUNKS = 8
    private const val TAG_DATA_3D: Byte = 0x2B
    private const val TAG_VERSION: Byte = 0x2C
    private const val TAG_SUB_CHUNK: Byte = 0x2F
    private const val TAG_FINALIZED: Byte = 0x36

    private const val AIR = 0
    private const val BEDROCK = 1
    private const val STONE = 2
    private const val DIRT = 3
    private const val GRASS = 4
    private const val SAND = 5
    private const val WATER = 6
    private const val BUILDING_WALL = 7
    private const val BUILDING_ROOF = 8
    private const val DEEPSLATE = WorldBlockId.DEEPSLATE
    private const val STONE_BRICKS = WorldBlockId.STONE_BRICKS
    private const val GRANITE = WorldBlockId.GRANITE
    private const val DIORITE = WorldBlockId.DIORITE
    private const val ANDESITE = WorldBlockId.ANDESITE
    private const val GRAVEL = WorldBlockId.GRAVEL
    private const val CLAY = WorldBlockId.CLAY
    private const val SNOW_BLOCK = WorldBlockId.SNOW_BLOCK
    private const val SANDSTONE = WorldBlockId.SANDSTONE
    private const val RED_SAND = WorldBlockId.RED_SAND
    private const val RED_SANDSTONE = WorldBlockId.RED_SANDSTONE
    private const val PODZOL = WorldBlockId.PODZOL
    private const val MYCELIUM = WorldBlockId.MYCELIUM
    private const val COARSE_DIRT = WorldBlockId.COARSE_DIRT
    private const val MUD = WorldBlockId.MUD
    private const val NETHERRACK = WorldBlockId.NETHERRACK
    private const val CALCITE = WorldBlockId.CALCITE
    private const val PACKED_ICE = WorldBlockId.PACKED_ICE
    private const val ICE = WorldBlockId.ICE
    private const val TUFF = WorldBlockId.TUFF
    private const val OBSIDIAN = WorldBlockId.OBSIDIAN
    private const val OAK_LOG = WorldBlockId.OAK_LOG
    private const val OAK_LEAVES = WorldBlockId.OAK_LEAVES
    private const val COAL_ORE = WorldBlockId.COAL_ORE
    private const val IRON_ORE = WorldBlockId.IRON_ORE
    private const val COPPER_ORE = WorldBlockId.COPPER_ORE
    private const val GOLD_ORE = WorldBlockId.GOLD_ORE
    private const val REDSTONE_ORE = WorldBlockId.REDSTONE_ORE
    private const val LAPIS_ORE = WorldBlockId.LAPIS_ORE
    private const val DIAMOND_ORE = WorldBlockId.DIAMOND_ORE
    private const val EMERALD_ORE = WorldBlockId.EMERALD_ORE
    private const val MOSSY_STONE_BRICKS = WorldBlockId.MOSSY_STONE_BRICKS
    private const val CRACKED_STONE_BRICKS = WorldBlockId.CRACKED_STONE_BRICKS
    private const val CHISELED_SANDSTONE = WorldBlockId.CHISELED_SANDSTONE
    private const val OBSIDIAN_CRYING = WorldBlockId.OBSIDIAN_CRYING
    private const val GOLD_BLOCK = WorldBlockId.GOLD_BLOCK
    private const val MOSSY_COBBLESTONE = WorldBlockId.MOSSY_COBBLESTONE
    private const val COBBLESTONE = WorldBlockId.COBBLESTONE
    private const val GLOWSTONE = WorldBlockId.GLOWSTONE

    private data class Point(val longitude: Double, val latitude: Double)
    private data class BlockPoint(val x: Double, val z: Double)
    private data class BuildingFootprint(
        val polygon: List<Point>,
        val heightMeters: Double,
        val minHeightMeters: Double,
        val source: String
    )
    private data class IndexedBuilding(
        val polygon: List<BlockPoint>,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
        val heightBlocks: Int,
        val baseOffsetBlocks: Int
    )
    private data class TerrainInput(
        val polygon: List<Point>,
        val west: Double,
        val east: Double,
        val south: Double,
        val north: Double,
        val rows: Int,
        val columns: Int,
        val values: Array<Double?>,
        val buildings: List<BuildingFootprint>,
        val minimum: Double,
        val maximum: Double,
        val widthMeters: Double,
        val depthMeters: Double,
        val proceduralSettings: ProceduralWorldSettings? = null
    )

    internal data class GridSize(val width: Int, val depth: Int)

    fun export(
        context: Context,
        worldName: String,
        regionJson: String,
        metersPerBlock: Int,
        verticalExaggeration: Double = 1.0,
        outputUri: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): BedrockExportReport {
        require(metersPerBlock in listOf(1, 2, 4, 8, 16)) { "不支持的地图比例" }
        require(verticalExaggeration in 1.0..3.0) { "垂直地形起伏倍率必须在 1× 到 3× 之间" }
        val input = parseRegion(regionJson)
        val dimensions = scaledDimensions(input.widthMeters, input.depthMeters, metersPerBlock)
        validateDimensions(dimensions)

        val tempRoot = File(context.cacheDir, "atlas-export-" + UUID.randomUUID().toString())
        val worldFolder = File(tempRoot, "world")
        val dbFolder = File(worldFolder, "db")
        if (!dbFolder.mkdirs()) throw IOException("无法创建临时世界数据库目录")
        var exportCompleted = false

        try {
            val chunkCount = writeWorldDatabase(
                dbFolder = dbFolder,
                input = input,
                size = dimensions,
                metersPerBlock = metersPerBlock,
                verticalExaggeration = verticalExaggeration,
                onProgress = onProgress
            )

            File(worldFolder, "levelname.txt").writeText(worldName.take(64), Charsets.UTF_8)
            val buildingSources = input.buildings.map { it.source }.distinct()
            val buildingAttribution = buildString {
                if (buildingSources.any { it.contains("3D-GloBFP") }) {
                    append("Building footprints and estimated heights: 3D-GloBFP, Che et al. (2025), DOI 10.5281/zenodo.15459025; CC BY 4.0.\n")
                    append("Attribution: Che, Y. et al. 3D-GloBFP: the first global three-dimensional building footprint dataset.\n")
                }
                if (buildingSources.any { it.contains("China Urban Building Footprint Height Dataset") }) {
                    append("Building footprints and estimated heights: China Urban Building Footprint Height Dataset (2018), National Earth System Science Data Center; reuse subject to the source portal's data terms.\\n")
                }
                if (buildingSources.any { it.contains("OpenStreetMap") }) {
                    append("OpenStreetMap contributors, ODbL 1.0.\n")
                }
            }
            File(worldFolder, "atlas_attribution.txt").writeText(
                "Terrain elevation: Mapzen Terrain Tiles, AWS Open Data; source data includes SRTM and other open datasets.\n" +
                    buildingAttribution +
                    "Map style: OpenFreeMap © OpenMapTiles; data © OpenStreetMap contributors.\n",
                Charsets.UTF_8
            )
            val spawn = chooseSpawn(input, metersPerBlock, verticalExaggeration)
            val levelDat = encodeLevelDat(
                worldName = worldName,
                spawnX = dimensions.width / 2,
                spawnY = spawn,
                spawnZ = dimensions.depth / 2
            )
            File(worldFolder, "level.dat").writeBytes(levelDat)
            validateLevelDat(File(worldFolder, "level.dat"))

            // Stream the finished world directly to the SAF destination. This avoids
            // keeping both a complete DB folder and a second complete .mcworld ZIP in cache.
            val output = context.contentResolver.openOutputStream(outputUri, "w")
                ?: throw IOException("无法打开目标文件")
            val archiveBytes = createMcWorldArchive(worldFolder, output)

            exportCompleted = true
            return BedrockExportReport(
                widthBlocks = dimensions.width,
                depthBlocks = dimensions.depth,
                chunkCount = chunkCount,
                archiveBytes = archiveBytes,
                metersPerBlock = metersPerBlock,
                minimumElevation = input.minimum,
                maximumElevation = input.maximum,
                verticalMetersPerBlock = verticalMetersPerBlock(
                    metersPerBlock, input.minimum, input.maximum, verticalExaggeration
                ),
                buildingCount = input.buildings.size
            )
        } finally {
            if (!exportCompleted) {
                // Best-effort cleanup of a partially written SAF document.
                try {
                    context.contentResolver.delete(outputUri, null, null)
                } catch (_: Exception) {
                    // Some document providers do not support deleting created files.
                }
            }
            tempRoot.deleteRecursively()
        }
    }

    internal fun scaledDimensions(widthMeters: Double, depthMeters: Double, metersPerBlock: Int): GridSize {
        require(widthMeters > 0.0 && depthMeters > 0.0)
        require(metersPerBlock > 0)
        return GridSize(
            width = ceil(widthMeters / metersPerBlock.toDouble()).toInt().coerceAtLeast(1),
            depth = ceil(depthMeters / metersPerBlock.toDouble()).toInt().coerceAtLeast(1)
        )
    }

    internal fun chunkCountForSize(size: GridSize): Long {
        val chunksX = ceil(size.width / 16.0).toLong()
        val chunksZ = ceil(size.depth / 16.0).toLong()
        return chunksX * chunksZ
    }

    internal fun validateDimensions(size: GridSize): Long {
        val chunkCount = chunkCountForSize(size)
        require(
            size.width <= MAX_EDGE_BLOCKS &&
                size.depth <= MAX_EDGE_BLOCKS &&
                chunkCount <= MAX_CHUNKS
        ) {
            "地图尺寸为 " + size.width + "×" + size.depth + " 格（约 " +
                chunkCount + " 个区块），超过移动设备分块导出的安全上限 " +
                MAX_CHUNKS + " 个区块。请缩小圈选区域或改用更大的比例尺。"
        }
        return chunkCount
    }

    internal fun surfaceYForElevation(
        elevationMeters: Double,
        minimumElevationMeters: Double,
        verticalMetersPerBlock: Double
    ): Int {
        require(elevationMeters.isFinite() && minimumElevationMeters.isFinite())
        require(verticalMetersPerBlock > 0.0)
        return (SEA_LEVEL + ((elevationMeters - minimumElevationMeters) / verticalMetersPerBlock).roundToInt())
            .coerceIn(MIN_Y + 4, MAX_Y - 5)
    }

    internal fun verticalMetersPerBlock(
        metersPerBlock: Int,
        minimumElevation: Double,
        maximumElevation: Double,
        verticalExaggeration: Double
    ): Double {
        require(metersPerBlock > 0)
        require(minimumElevation.isFinite() && maximumElevation.isFinite())
        require(verticalExaggeration in 1.0..3.0)
        require(maximumElevation >= minimumElevation) { "高程上下限顺序不正确" }
        val localReliefMeters = maximumElevation - minimumElevation
        // The local minimum is anchored at Y=8. Use the real local height range,
        // not absolute altitude above sea level: a plateau at 1,800 m with only
        // 60 m of relief must not be flattened merely because it is high above sea level.
        // Compress only when the region's own relief would exceed Bedrock build height.
        return max(metersPerBlock.toDouble() / verticalExaggeration, localReliefMeters / 280.0)
    }

    internal fun terrainFloorY(
        minimumElevation: Double,
        maximumElevation: Double,
        metersPerBlock: Int,
        verticalExaggeration: Double = 1.0
    ): Int {
        // The selected region's lowest point is the local datum at sea-level Y=8.
        // The actual 0 m sea plane is calculated independently during voxel generation.
        return SEA_LEVEL - 4
    }

    internal fun chunkKey(
        chunkX: Int,
        chunkZ: Int,
        tag: Byte,
        subChunkY: Int? = null
    ): ByteArray {
        val output = ByteArrayOutputStream(if (subChunkY == null) 9 else 10)
        output.writeIntLE(chunkX)
        output.writeIntLE(chunkZ)
        output.write(tag.toInt() and 0xFF)
        if (subChunkY != null) output.write(subChunkY and 0xFF)
        return output.toByteArray()
    }

    internal fun encodeSubChunk(sectionY: Int, blocks: IntArray): ByteArray {
        require(sectionY in -128..127)
        require(blocks.size == 4096)
        val output = ByteArrayOutputStream(4096)
        output.write(SUB_CHUNK_VERSION)
        output.write(1) // one block-storage layer
        output.write(sectionY and 0xFF)
        output.write(encodeBlockStorage(blocks))
        return output.toByteArray()
    }

    internal fun encodeLevelDat(worldName: String, spawnX: Int, spawnY: Int, spawnZ: Int): ByteArray {
        val nbt = ByteArrayOutputStream()
        nbt.writeCompoundStart("")
        nbt.writeIntTag("StorageVersion", 10)
        nbt.writeIntTag("NetworkVersion", NETWORK_VERSION)
        nbt.writeIntTag("WorldVersion", 1)
        nbt.writeStringTag("InventoryVersion", "1.21.120")
        nbt.writeIntListTag("lastOpenedWithVersion", intArrayOf(1, 26, 52, 3))
        nbt.writeIntListTag("MinimumCompatibleClientVersion", intArrayOf(1, 26, 52, 3))
        nbt.writeStringTag("LevelName", worldName.take(64))
        nbt.writeLongTag("RandomSeed", System.currentTimeMillis())
        nbt.writeIntTag("SpawnX", spawnX)
        nbt.writeIntTag("SpawnY", spawnY)
        nbt.writeIntTag("SpawnZ", spawnZ)
        nbt.writeLongTag("Time", 0L)
        nbt.writeLongTag("LastPlayed", System.currentTimeMillis())
        // Generator 1 = Infinite. The actual terrain is stored in LevelDB chunks below;
        // never mark these imported real-world maps as Flat (Generator 2).
        nbt.writeIntTag("Generator", WORLD_GENERATOR_INFINITE)
        nbt.writeIntTag("GameType", 1)
        nbt.writeIntTag("Difficulty", 1)
        nbt.writeByteTag("commandsEnabled", 1)
        nbt.writeByteTag("hasBeenLoadedInCreative", 1)
        nbt.writeByteTag("showcoordinates", 1)
        nbt.writeByteTag("spawnMobs", 1)
        nbt.writeByteTag("domobspawning", 1)
        nbt.writeByteTag("dodaylightcycle", 1)
        nbt.writeByteTag("doweathercycle", 1)
        nbt.writeByteTag("doentitydrops", 1)
        nbt.writeByteTag("keepinventory", 0)
        nbt.writeByteTag("naturalregeneration", 1)
        nbt.writeIntTag("PlayerPermissionsLevel", 2)
        nbt.writeIntTag("defaultPlayerPermissions", 2)
        nbt.writeEnd()
        val payload = nbt.toByteArray()
        val file = ByteArrayOutputStream(payload.size + 8)
        file.writeIntLE(10)
        file.writeIntLE(payload.size)
        file.write(payload)
        return file.toByteArray()
    }

    private fun parseRegion(raw: String): TerrainInput {
        val root = JSONObject(raw)
        if (root.optString("format") == "blockworld-procedural-v1") {
            val settings = ProceduralWorldSettings.fromJson(raw)
            val size = settings.worldSize
            return TerrainInput(
                polygon = listOf(Point(0.0, 1.0), Point(1.0, 1.0), Point(1.0, 0.0), Point(0.0, 0.0)),
                west = 0.0, east = 1.0, south = 0.0, north = 1.0,
                rows = 2, columns = 2,
                values = arrayOf(0.0, 0.0, 0.0, 0.0),
                buildings = emptyList(),
                minimum = -42.0, maximum = 196.0,
                widthMeters = size.toDouble(), depthMeters = size.toDouble(),
                proceduralSettings = settings
            )
        }
        require(root.optString("format") == "atlas-region-heightmap-v1") { "项目中没有可识别的地形数据" }

        val boundaryJson = root.optJSONArray("boundaryLonLat")
            ?: throw IllegalArgumentException("项目缺少圈选边界")
        val boundary = (0 until boundaryJson.length()).mapNotNull { index ->
            val pair = boundaryJson.optJSONArray(index) ?: return@mapNotNull null
            if (pair.length() < 2) return@mapNotNull null
            Point(pair.getDouble(0), pair.getDouble(1))
        }
        require(boundary.size >= 3) { "圈选区域至少需要 3 个边界点" }

        val elevationJson = root.optJSONArray("elevationMeters")
            ?: throw IllegalArgumentException("请先在地图页采集高程数据")
        val rows = root.optInt("rows", elevationJson.length())
        val columns = root.optInt("columns", elevationJson.optJSONArray(0)?.length() ?: 0)
        require(rows >= 2 && columns >= 2 && elevationJson.length() == rows) { "高程网格格式不正确" }

        val values = arrayOfNulls<Double>(rows * columns)
        val valid = ArrayList<Double>()
        for (row in 0 until rows) {
            val rowJson = elevationJson.optJSONArray(row)
                ?: throw IllegalArgumentException("高程网格第 " + (row + 1) + " 行损坏")
            require(rowJson.length() == columns) { "高程网格列数不一致" }
            for (column in 0 until columns) {
                if (!rowJson.isNull(column)) {
                    val value = rowJson.optDouble(column, Double.NaN)
                    if (value.isFinite()) {
                        values[row * columns + column] = value
                        valid.add(value)
                    }
                }
            }
        }
        require(valid.isNotEmpty()) { "高程网格没有有效采样值" }

        val west = boundary.minOf { it.longitude }
        val east = boundary.maxOf { it.longitude }
        val south = boundary.minOf { it.latitude }
        val north = boundary.maxOf { it.latitude }
        require(east > west && north > south && east - west < 180.0) { "当前选区范围无法转换，请重新圈选" }

        val buildingsJson = root.optJSONArray("osmBuildings") ?: JSONArray()
        val buildings = ArrayList<BuildingFootprint>()
        for (index in 0 until buildingsJson.length()) {
            val item = buildingsJson.optJSONObject(index) ?: continue
            val geometry = item.optJSONArray("boundaryLonLat") ?: continue
            val points = (0 until geometry.length()).mapNotNull { pointIndex ->
                val pair = geometry.optJSONArray(pointIndex) ?: return@mapNotNull null
                if (pair.length() < 2) return@mapNotNull null
                val longitude = pair.optDouble(0, Double.NaN)
                val latitude = pair.optDouble(1, Double.NaN)
                if (!longitude.isFinite() || !latitude.isFinite()) null else Point(longitude, latitude)
            }
            if (points.size >= 4) {
                buildings.add(
                    BuildingFootprint(
                        polygon = points,
                        heightMeters = item.optDouble("heightMeters", 8.0).coerceAtLeast(3.0),
                        minHeightMeters = item.optDouble("minHeightMeters", 0.0).coerceAtLeast(0.0),
                        source = item.optString("source", "OpenStreetMap").ifBlank { "OpenStreetMap" }
                    )
                )
                if (buildings.size >= 20000) break
            }
        }

        val centerLatitude = (north + south) / 2.0
        val centerLongitude = (east + west) / 2.0
        val widthMeters = root.optDouble("regionWidthKm", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            ?.times(1000.0) ?: haversine(centerLatitude, west, centerLatitude, east)
        val depthMeters = root.optDouble("regionHeightKm", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            ?.times(1000.0) ?: haversine(south, centerLongitude, north, centerLongitude)

        return TerrainInput(
            polygon = boundary,
            west = west,
            east = east,
            south = south,
            north = north,
            rows = rows,
            columns = columns,
            values = values,
            buildings = buildings,
            minimum = valid.minOrNull() ?: 0.0,
            maximum = valid.maxOrNull() ?: 0.0,
            widthMeters = widthMeters,
            depthMeters = depthMeters
        )
    }

    private fun writeWorldDatabase(
        dbFolder: File,
        input: TerrainInput,
        size: GridSize,
        metersPerBlock: Int,
        verticalExaggeration: Double,
        onProgress: (Int, Int) -> Unit
    ): Int {
        val options = Options()
            .createIfMissing(true)
            .compressionType(CompressionType.ZLIB_RAW)
            .writeBufferSize(8 * 1024 * 1024)
            .maxOpenFiles(128)
        val db = Iq80DBFactory.factory.open(dbFolder, options)
        var firstVersionKey: ByteArray? = null
        var firstSubChunkKey: ByteArray? = null
        val chunksX = ceil(size.width / 16.0).toInt()
        val chunksZ = ceil(size.depth / 16.0).toInt()
        val totalChunks = validateDimensions(size).toInt()
        var chunksWritten = 0
        var processedChunks = 0
        val verticalScale = verticalMetersPerBlock(
            metersPerBlock,
            input.minimum,
            input.maximum,
            verticalExaggeration
        )
        val proceduralSettings = input.proceduralSettings
        val worldFloorY = if (proceduralSettings != null) MIN_Y else
            terrainFloorY(input.minimum, input.maximum, metersPerBlock, verticalExaggeration)
        val seaDatum = proceduralSettings?.let { it.waterLevel - 8.0 } ?: 0.0
        val seaLevelY = (
            SEA_LEVEL + ((seaDatum - input.minimum) / verticalScale).roundToInt()
        ).coerceIn(MIN_Y + 1, MAX_Y - 1)
        val indexedBuildings = indexBuildings(input, size, verticalScale)
        val sink = DatabaseBatchSink(db)

        try {
            // Process an 8×8 group of Minecraft chunks at a time. Each group is
            // committed to LevelDB before moving on, so RAM use stays bounded.
            for (tileZ in 0 until chunksZ step TILE_CHUNKS) {
                for (tileX in 0 until chunksX step TILE_CHUNKS) {
                    val tileEndZ = min(chunksZ, tileZ + TILE_CHUNKS)
                    val tileEndX = min(chunksX, tileX + TILE_CHUNKS)
                    val tileMinX = tileX * 16
                    val tileMaxX = min(size.width - 1, tileEndX * 16 - 1)
                    val tileMinZ = tileZ * 16
                    val tileMaxZ = min(size.depth - 1, tileEndZ * 16 - 1)
                    val tileBuildings = indexedBuildings.filter { building ->
                        building.maxX >= tileMinX && building.minX <= tileMaxX &&
                            building.maxZ >= tileMinZ && building.minZ <= tileMaxZ
                    }

                    for (chunkZ in tileZ until tileEndZ) {
                        for (chunkX in tileX until tileEndX) {
                            processedChunks++
                            val sections = HashMap<Int, IntArray>()
                            val heightMap = IntArray(256) { worldFloorY }
                            val surfaceYMap = IntArray(256) { worldFloorY }
                            val hasSurface = BooleanArray(256)
                            val biomeIdsForChunk = IntArray(256) { 1 }
                            var columnsInChunk = 0

                            for (localZ in 0 until 16) {
                                val worldZ = chunkZ * 16 + localZ
                                if (worldZ >= size.depth) continue
                                for (localX in 0 until 16) {
                                    val worldX = chunkX * 16 + localX
                                    if (worldX >= size.width) continue
                                    val point = coordinateAt(input, size, worldX, worldZ)
                                    if (!insidePolygon(point, input.polygon)) continue
                                    val elevation = if (proceduralSettings != null) {
                                        ProceduralWorldGenerator.heightAt(worldX, worldZ, proceduralSettings)
                                    } else {
                                        sampleElevation(input, point)
                                    }
                                    val surfaceY = surfaceYForElevation(elevation, input.minimum, verticalScale)
                                    val biome = proceduralSettings?.let {
                                        ProceduralWorldGenerator.biomeAt(worldX, worldZ, elevation, it)
                                    }
                                    val columnIndex = localZ * 16 + localX
                                    biomeIdsForChunk[columnIndex] = biome?.bedrockId ?: 1

                                    setBlock(sections, localX, worldFloorY, localZ, BEDROCK)
                                    for (y in worldFloorY + 1 until surfaceY - 3) {
                                        var block = if (proceduralSettings != null && y < -8) DEEPSLATE else STONE
                                        if (proceduralSettings != null && proceduralSettings.enableCaves &&
                                            ProceduralWorldGenerator.caveAt(worldX, y, worldZ, proceduralSettings)
                                        ) {
                                            block = AIR
                                        } else if (proceduralSettings != null && proceduralSettings.enableOres) {
                                            val ore = ProceduralWorldGenerator.oreAt(
                                                worldX, y, worldZ, biome ?: ProceduralWorldGenerator.BIOMES.first(), proceduralSettings
                                            )
                                            if (ore != WorldBlockId.STONE) block = ore
                                        }
                                        setBlock(sections, localX, y, localZ, block)
                                    }
                                    val soil = biome?.soilBlock ?: DIRT
                                    val top = biome?.surfaceBlock ?: if (elevation < 0.0) SAND else GRASS
                                    for (y in surfaceY - 3 until surfaceY) setBlock(sections, localX, y, localZ, soil)
                                    setBlock(sections, localX, surfaceY, localZ, top)
                                    surfaceYMap[columnIndex] = surfaceY
                                    hasSurface[columnIndex] = true
                                    val underwater = if (proceduralSettings != null) {
                                        proceduralSettings.enableWater && elevation < proceduralSettings.waterLevel
                                    } else elevation < 0.0
                                    if (underwater && seaLevelY > surfaceY) {
                                        for (y in surfaceY + 1..seaLevelY) setBlock(sections, localX, y, localZ, WATER)
                                        heightMap[columnIndex] = seaLevelY + 1
                                    } else {
                                        heightMap[columnIndex] = surfaceY + 1
                                    }
                                    columnsInChunk++
                                }
                            }

                            if (columnsInChunk > 0) {
                                val chunkMinX = chunkX * 16
                                val chunkMaxX = min(size.width - 1, chunkMinX + 15)
                                val chunkMinZ = chunkZ * 16
                                val chunkMaxZ = min(size.depth - 1, chunkMinZ + 15)
                                if (tileBuildings.isNotEmpty()) {
                                    applyBuildings(
                                        sections = sections,
                                        heightMap = heightMap,
                                        surfaceYMap = surfaceYMap,
                                        hasSurface = hasSurface,
                                        buildings = tileBuildings,
                                        chunkMinX = chunkMinX,
                                        chunkMaxX = chunkMaxX,
                                        chunkMinZ = chunkMinZ,
                                        chunkMaxZ = chunkMaxZ
                                    )
                                }
                                if (proceduralSettings != null) {
                                    applyProceduralTrees(
                                        sections, heightMap, surfaceYMap, hasSurface,
                                        input, size, proceduralSettings, verticalScale,
                                        chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ
                                    )
                                    applyProceduralStructures(
                                        sections, heightMap, input, size, proceduralSettings, verticalScale,
                                        chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ
                                    )
                                }
                                val versionKey = chunkKey(chunkX, chunkZ, TAG_VERSION)
                                val data3dKey = chunkKey(chunkX, chunkZ, TAG_DATA_3D)
                                val finalizedKey = chunkKey(chunkX, chunkZ, TAG_FINALIZED)
                                sink.put(versionKey, byteArrayOf(CHUNK_VERSION.toByte()))
                                sink.put(finalizedKey, intBytesLE(2))
                                sink.put(data3dKey, encodeData3D(heightMap, biomeIdsForChunk))
                                if (firstVersionKey == null) firstVersionKey = versionKey

                                for ((sectionY, blocks) in sections.toSortedMap()) {
                                    val subKey = chunkKey(chunkX, chunkZ, TAG_SUB_CHUNK, sectionY)
                                    val encoded = encodeSubChunk(sectionY, blocks)
                                    sink.put(subKey, encoded)
                                    if (firstSubChunkKey == null) firstSubChunkKey = subKey
                                }
                                chunksWritten++
                            }
                        }
                    }

                    // Commit one tile and report progress, even when some chunks are outside
                    // an irregular selection polygon.
                    sink.flush()
                    onProgress(processedChunks, totalChunks)
                }
            }
            sink.finish()
        } finally {
            sink.close()
            db.close()
        }

        require(chunksWritten > 0) { "圈选区域没有生成任何地图方块，请重新选择区域" }
        val versionKey = firstVersionKey ?: throw IOException("没有生成区块版本记录")
        val subKey = firstSubChunkKey ?: throw IOException("没有生成子区块记录")
        verifyDatabase(dbFolder, versionKey, subKey)
        return chunksWritten
    }

    private class DatabaseBatchSink(private val db: DB) {
        private var batch: WriteBatch = db.createWriteBatch()
        private var queued = 0
        private var finished = false

        fun put(key: ByteArray, value: ByteArray) {
            check(!finished) { "LevelDB batch already closed" }
            batch.put(key, value)
            queued++
            if (queued >= 384) flush()
        }

        fun flush() {
            if (queued > 0) db.write(batch)
            batch.close()
            batch = db.createWriteBatch()
            queued = 0
        }

        fun finish() {
            if (!finished) {
                if (queued > 0) db.write(batch)
                batch.close()
                finished = true
            }
        }

        fun close() {
            if (!finished) {
                batch.close()
                finished = true
            }
        }
    }

    private fun verifyDatabase(dbFolder: File, versionKey: ByteArray, subChunkKey: ByteArray) {
        val verifyOptions = Options()
            .createIfMissing(false)
            .compressionType(CompressionType.ZLIB_RAW)
        val verify = Iq80DBFactory.factory.open(dbFolder, verifyOptions)
        try {
            val version = verify.get(versionKey)
                ?: throw IOException("LevelDB 校验失败：缺少区块版本记录")
            require(version.size == 1 && (version[0].toInt() and 0xFF) == CHUNK_VERSION) {
                "LevelDB 校验失败：区块版本不匹配"
            }
            val subChunk = verify.get(subChunkKey)
                ?: throw IOException("LevelDB 校验失败：缺少子区块记录")
            require(subChunk.size > 8 && (subChunk[0].toInt() and 0xFF) == SUB_CHUNK_VERSION) {
                "LevelDB 校验失败：子区块格式不正确"
            }
        } finally {
            verify.close()
        }
    }

    internal fun buildingHeightBlocks(heightMeters: Double, verticalMetersPerBlock: Double): Int {
        require(heightMeters >= 0.0 && verticalMetersPerBlock > 0.0)
        return ceil(heightMeters / verticalMetersPerBlock).toInt().coerceIn(2, 96)
    }

    private fun indexBuildings(
        input: TerrainInput,
        size: GridSize,
        verticalMetersPerBlock: Double
    ): List<IndexedBuilding> {
        return input.buildings.mapNotNull { building ->
            if (building.polygon.size < 4) return@mapNotNull null
            val polygon = building.polygon.map { point ->
                BlockPoint(
                    x = ((point.longitude - input.west) / (input.east - input.west)) * size.width,
                    z = ((input.north - point.latitude) / (input.north - input.south)) * size.depth
                )
            }
            val minX = floor(polygon.minOf { it.x }).toInt().coerceAtLeast(0)
            val maxX = ceil(polygon.maxOf { it.x }).toInt().coerceAtMost(size.width - 1)
            val minZ = floor(polygon.minOf { it.z }).toInt().coerceAtLeast(0)
            val maxZ = ceil(polygon.maxOf { it.z }).toInt().coerceAtMost(size.depth - 1)
            if (minX > maxX || minZ > maxZ) return@mapNotNull null
            IndexedBuilding(
                polygon = polygon,
                minX = minX,
                maxX = maxX,
                minZ = minZ,
                maxZ = maxZ,
                heightBlocks = buildingHeightBlocks(building.heightMeters, verticalMetersPerBlock),
                baseOffsetBlocks = floor(building.minHeightMeters / verticalMetersPerBlock)
                    .toInt().coerceIn(0, 96)
            )
        }
    }

    private fun applyBuildings(
        sections: MutableMap<Int, IntArray>,
        heightMap: IntArray,
        surfaceYMap: IntArray,
        hasSurface: BooleanArray,
        buildings: List<IndexedBuilding>,
        chunkMinX: Int,
        chunkMaxX: Int,
        chunkMinZ: Int,
        chunkMaxZ: Int
    ) {
        for (building in buildings) {
            val startX = max(chunkMinX, building.minX)
            val endX = min(chunkMaxX, building.maxX)
            val startZ = max(chunkMinZ, building.minZ)
            val endZ = min(chunkMaxZ, building.maxZ)
            if (startX > endX || startZ > endZ) continue

            for (worldZ in startZ..endZ) {
                for (worldX in startX..endX) {
                    if (!insideBlockPolygon(worldX + 0.5, worldZ + 0.5, building.polygon)) continue
                    val localX = worldX - chunkMinX
                    val localZ = worldZ - chunkMinZ
                    val index = localZ * 16 + localX
                    if (!hasSurface[index]) continue
                    val groundY = surfaceYMap[index]
                    if (groundY < SEA_LEVEL) continue
                    val baseY = groundY + 1 + building.baseOffsetBlocks
                    val roofY = groundY + building.heightBlocks
                    if (roofY <= baseY || roofY > MAX_Y - 1) continue
                    for (y in baseY until roofY) {
                        setBlock(sections, localX, y, localZ, BUILDING_WALL)
                    }
                    setBlock(sections, localX, roofY, localZ, BUILDING_ROOF)
                    heightMap[index] = max(heightMap[index], roofY + 1)
                }
            }
        }
    }

    private fun applyProceduralTrees(
        sections: MutableMap<Int, IntArray>,
        heightMap: IntArray,
        surfaceYMap: IntArray,
        hasSurface: BooleanArray,
        input: TerrainInput,
        size: GridSize,
        settings: ProceduralWorldSettings,
        verticalScale: Double,
        chunkMinX: Int,
        chunkMaxX: Int,
        chunkMinZ: Int,
        chunkMaxZ: Int
    ) {
        val cellSize = 7
        val startCellX = Math.floorDiv(chunkMinX - 8, cellSize)
        val endCellX = Math.floorDiv(chunkMaxX + 8, cellSize)
        val startCellZ = Math.floorDiv(chunkMinZ - 8, cellSize)
        val endCellZ = Math.floorDiv(chunkMaxZ + 8, cellSize)
        for (cellZ in startCellZ..endCellZ) {
            for (cellX in startCellX..endCellX) {
                val candidate = ProceduralWorldGenerator.treeCandidate(cellX, cellZ, settings) ?: continue
                val x = candidate.first
                val z = candidate.second
                if (x !in 2 until size.width - 2 || z !in 2 until size.depth - 2) continue
                val point = coordinateAt(input, size, x, z)
                val elevation = ProceduralWorldGenerator.heightAt(x, z, settings)
                if (settings.enableWater && elevation < settings.waterLevel - 7) continue
                val y = surfaceYForElevation(elevation, input.minimum, verticalScale)
                val biome = ProceduralWorldGenerator.biomeAt(x, z, elevation, settings)
                val profile = ProceduralWorldGenerator.treeProfile(biome, x, z, settings)
                if (profile.height <= 0 || y + profile.height + 3 >= MAX_Y) continue
                drawProceduralTree(
                    sections, heightMap, profile, x, y, z,
                    chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ
                )
            }
        }
    }

    private fun drawProceduralTree(
        sections: MutableMap<Int, IntArray>,
        heightMap: IntArray,
        profile: TreeProfile,
        x: Int,
        groundY: Int,
        z: Int,
        chunkMinX: Int,
        chunkMaxX: Int,
        chunkMinZ: Int,
        chunkMaxZ: Int
    ) {
        fun put(px: Int, py: Int, pz: Int, block: Int, onlyAir: Boolean = false) {
            if (px !in chunkMinX..chunkMaxX || pz !in chunkMinZ..chunkMaxZ) return
            if (py !in MIN_Y + 1 until MAX_Y) return
            val lx = px - chunkMinX
            val lz = pz - chunkMinZ
            val existing = getBlock(sections, lx, py, lz)
            if (onlyAir && existing != AIR && existing != profile.leafBlock) return
            setBlock(sections, lx, py, lz, block)
            val index = lz * 16 + lx
            heightMap[index] = max(heightMap[index], py + 1)
        }

        val trunkTop = groundY + profile.height
        for (dy in 1..profile.height) put(x, groundY + dy, z, profile.logBlock)
        if (profile.shape == "mangrove") {
            for (direction in listOf(-1, 1)) {
                for (step in 1..2) {
                    put(x + direction * step, groundY + 1, z, profile.logBlock)
                    put(x, groundY + 1, z + direction * step, profile.logBlock)
                }
            }
        }

        val crownBase = when (profile.shape) {
            "cone", "giant_cone" -> trunkTop - (if (profile.shape == "giant_cone") 7 else 5)
            "flat" -> trunkTop - 2
            "umbrella" -> trunkTop - 3
            else -> trunkTop - 3
        }
        for (layerY in crownBase..trunkTop + 1) {
            val dy = layerY - crownBase
            val radius = when (profile.shape) {
                "cone" -> max(1, 3 - dy / 2)
                "giant_cone" -> max(1, 5 - dy / 2)
                "flat" -> if (dy == 0) 4 else if (dy == 1) 3 else 2
                "umbrella" -> if (dy == 0) 4 else if (dy < 3) 3 else 2
                "wide" -> if (dy == 0) 4 else if (dy < 3) 3 else 2
                "mangrove" -> if (dy < 2) 3 else 2
                "mushroom" -> if (dy == 0) 3 else 2
                else -> max(1, 3 - dy / 2)
            }
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val distance = dx * dx + dz * dz
                    val limit = radius * radius + if (profile.shape == "mushroom") 1 else -1
                    if (distance > limit) continue
                    if (profile.shape == "cone" && dy > 1 && distance < 2) continue
                    if (profile.shape == "mushroom" && dy == 0) continue
                    put(x + dx, layerY, z + dz, profile.leafBlock, onlyAir = true)
                }
            }
        }
        if (profile.shape == "mushroom") {
            for (dx in -3..3) for (dz in -3..3) {
                if (dx * dx + dz * dz <= 9) put(x + dx, trunkTop + 1, z + dz, profile.leafBlock, onlyAir = true)
            }
        }
    }

    private fun getBlock(sections: Map<Int, IntArray>, localX: Int, y: Int, localZ: Int): Int {
        val sectionY = Math.floorDiv(y, 16)
        val localY = Math.floorMod(y, 16)
        val blocks = sections[sectionY] ?: return AIR
        return blocks[localX * 256 + localZ * 16 + localY]
    }

    private fun applyProceduralStructures(
        sections: MutableMap<Int, IntArray>,
        heightMap: IntArray,
        input: TerrainInput,
        size: GridSize,
        settings: ProceduralWorldSettings,
        verticalScale: Double,
        chunkMinX: Int,
        chunkMaxX: Int,
        chunkMinZ: Int,
        chunkMaxZ: Int
    ) {
        val spacing = 112
        val startCellX = Math.floorDiv(chunkMinX - 24, spacing)
        val endCellX = Math.floorDiv(chunkMaxX + 24, spacing)
        val startCellZ = Math.floorDiv(chunkMinZ - 24, spacing)
        val endCellZ = Math.floorDiv(chunkMaxZ + 24, spacing)

        fun put(x: Int, y: Int, z: Int, block: Int) {
            if (x !in chunkMinX..chunkMaxX || z !in chunkMinZ..chunkMaxZ ||
                y !in MIN_Y + 1 until MAX_Y
            ) return
            val lx = x - chunkMinX
            val lz = z - chunkMinZ
            setBlock(sections, lx, y, lz, block)
            val column = lz * 16 + lx
            heightMap[column] = max(heightMap[column], y + 1)
        }

        for (cellZ in startCellZ..endCellZ) {
            for (cellX in startCellX..endCellX) {
                val anchor = ProceduralWorldGenerator.structureAnchor(cellX, cellZ, settings) ?: continue
                val cx = anchor.x
                val cz = anchor.z
                if (cx !in 8 until size.width - 8 || cz !in 8 until size.depth - 8) continue
                val groundElevation = ProceduralWorldGenerator.heightAt(cx, cz, settings)
                val water = settings.waterLevel
                if (anchor.type != "ocean_ruin" && groundElevation < water - 5) continue
                val surfaceBaseY = surfaceYForElevation(groundElevation, input.minimum, verticalScale) + 1
                val baseY = when (anchor.type) {
                    "mineshaft" -> max(MIN_Y + 8, surfaceBaseY - 24)
                    "stronghold" -> max(MIN_Y + 8, surfaceBaseY - 32)
                    else -> surfaceBaseY
                }
                when (anchor.type) {
                    "desert_temple" -> {
                        // Original stepped sandstone pyramid with a recessed central chamber.
                        for (layer in 0..4) {
                            val radius = 6 - layer
                            for (dx in -radius..radius) for (dz in -radius..radius) {
                                val edge = abs(dx) == radius || abs(dz) == radius
                                if (edge || layer == 0) put(cx + dx, baseY + layer, cz + dz, SANDSTONE)
                            }
                        }
                        for (y in baseY + 1..baseY + 5) {
                            for (dx in -2..2) for (dz in -2..2) {
                                if (abs(dx) == 2 || abs(dz) == 2) put(cx + dx, y, cz + dz, CHISELED_SANDSTONE)
                            }
                        }
                        put(cx, baseY + 2, cz, GOLD_BLOCK)
                        put(cx - 1, baseY + 2, cz, GOLD_BLOCK)
                        put(cx + 1, baseY + 2, cz, GOLD_BLOCK)
                        put(cx, baseY + 2, cz - 1, GOLD_BLOCK)
                        put(cx, baseY + 2, cz + 1, GOLD_BLOCK)
                    }
                    "jungle_temple" -> {
                        drawStructureHouse(
                            ::put, cx, baseY, cz, 11, 13, 8,
                            MOSSY_STONE_BRICKS, STONE_BRICKS, COBBLESTONE
                        )
                        for (dx in -4..4 step 4) {
                            put(cx + dx, baseY + 9, cz - 5, STONE_BRICKS)
                            put(cx + dx, baseY + 9, cz + 5, STONE_BRICKS)
                        }
                    }
                    "ruined_portal" -> {
                        for (y in 0..6) for (dx in -2..2) {
                            val frame = abs(dx) == 2 || y == 0 || y == 6
                            if (frame && ProceduralWorldGenerator.hash01(cx + dx, y, cz, settings.seed + 7001) > 0.12) {
                                put(cx + dx, baseY + y, cz, if ((dx + y) % 4 == 0) OBSIDIAN_CRYING else WorldBlockId.OBSIDIAN)
                            }
                        }
                        for (dx in -4..4) {
                            put(cx + dx, baseY, cz + 2, NETHERRACK)
                            if (dx % 2 == 0) put(cx + dx, baseY + 1, cz + 2, NETHERRACK)
                        }
                        put(cx + 3, baseY + 1, cz + 1, GLOWSTONE)
                    }
                    "village" -> {
                        drawStructureHouse(::put, cx - 7, baseY, cz - 4, 7, 7, 5, OAK_LOG, WorldBlockId.BRICKS, WorldBlockId.COBBLESTONE)
                        drawStructureHouse(::put, cx + 5, baseY + 1, cz - 2, 7, 7, 5, OAK_LOG, WorldBlockId.BRICKS, WorldBlockId.COBBLESTONE)
                        drawStructureHouse(::put, cx - 1, baseY, cz + 7, 7, 7, 5, OAK_LOG, WorldBlockId.BRICKS, WorldBlockId.COBBLESTONE)
                        for (step in -6..6) put(cx + step, baseY, cz + 3, WorldBlockId.GRAVEL)
                    }
                    "mineshaft" -> {
                        // Hollow a walkable tunnel first, then place supports and floor.
                        for (z in -10..10) {
                            for (dx in -2..2) for (dy in 1..3) put(cx + dx, baseY + dy, cz + z, AIR)
                            put(cx, baseY, cz + z, WorldBlockId.GRAVEL)
                            if (z % 4 == 0) {
                                put(cx - 2, baseY + 2, cz + z, OAK_LOG)
                                put(cx + 2, baseY + 2, cz + z, OAK_LOG)
                                put(cx - 2, baseY + 4, cz + z, OAK_LOG)
                                put(cx + 2, baseY + 4, cz + z, OAK_LOG)
                                for (dx in -2..2) put(cx + dx, baseY + 4, cz + z, OAK_LOG)
                            }
                        }
                    }
                    "stronghold" -> {
                        // Clear a large chamber so the stone-brick rooms are exposed underground.
                        for (dx in -8..8) for (dz in -8..8) for (dy in 1..8) {
                            put(cx + dx, baseY + dy, cz + dz, AIR)
                        }
                        drawStructureHouse(::put, cx, baseY, cz, 15, 15, 7, STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS)
                        for (dx in -6..6 step 6) for (dz in -6..6 step 6) {
                            for (dy in 1..9) put(cx + dx, baseY + dy, cz + dz, STONE_BRICKS)
                        }
                        for (dx in -2..2) for (dz in -2..2) put(cx + dx, baseY + 1, cz + dz, WorldBlockId.AMETHYST_BLOCK)
                    }
                    "ocean_ruin" -> {
                        val floor = surfaceYForElevation(groundElevation, input.minimum, verticalScale)
                        for (dx in -5..5) for (dz in -5..5) {
                            if (abs(dx) + abs(dz) < 8 && ProceduralWorldGenerator.hash01(cx + dx, 0, cz + dz, settings.seed + 7013) > 0.18) {
                                put(cx + dx, floor + 1, cz + dz, if ((dx + dz) % 3 == 0) MOSSY_STONE_BRICKS else STONE_BRICKS)
                            }
                        }
                        for (dy in 1..3) {
                            put(cx - 2, floor + dy, cz, STONE_BRICKS)
                            put(cx + 2, floor + dy, cz + 1, MOSSY_STONE_BRICKS)
                        }
                    }
                    "watchtower" -> {
                        for (dx in listOf(-3, 3)) for (dz in listOf(-3, 3)) {
                            for (dy in 1..11) put(cx + dx, baseY + dy, cz + dz, OAK_LOG)
                        }
                        for (dx in -4..4) for (dz in -4..4) {
                            if (abs(dx) == 4 || abs(dz) == 4 || baseY % 2 == 0) {
                                put(cx + dx, baseY + 10, cz + dz, STONE_BRICKS)
                                put(cx + dx, baseY + 12, cz + dz, WorldBlockId.BRICKS)
                            }
                        }
                        for (dy in 11..13) put(cx, baseY + dy, cz, OAK_LOG)
                    }
                }
            }
        }
    }

    private fun drawStructureHouse(
        put: (Int, Int, Int, Int) -> Unit,
        centerX: Int,
        baseY: Int,
        centerZ: Int,
        width: Int,
        depth: Int,
        height: Int,
        wall: Int,
        roof: Int,
        floor: Int
    ) {
        val halfX = width / 2
        val halfZ = depth / 2
        for (dx in -halfX..halfX) for (dz in -halfZ..halfZ) {
            put(centerX + dx, baseY, centerZ + dz, floor)
            for (dy in 1..height) {
                val boundary = abs(dx) == halfX || abs(dz) == halfZ
                if (boundary) put(centerX + dx, baseY + dy, centerZ + dz, if (dy == height) roof else wall)
            }
            if (abs(dx) == halfX || abs(dz) == halfZ) put(centerX + dx, baseY + height + 1, centerZ + dz, roof)
        }
    }

    private fun insideBlockPolygon(x: Double, z: Double, polygon: List<BlockPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val crosses = ((a.z > z) != (b.z > z)) &&
                (x < (b.x - a.x) * (z - a.z) / ((b.z - a.z) + 1e-12) + a.x)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun coordinateAt(input: TerrainInput, size: GridSize, x: Int, z: Int): Point {
        val longitude = input.west + ((x + 0.5) / size.width.toDouble()) * (input.east - input.west)
        val latitude = input.north - ((z + 0.5) / size.depth.toDouble()) * (input.north - input.south)
        return Point(longitude, latitude)
    }

    private fun sampleElevation(input: TerrainInput, point: Point): Double {
        input.proceduralSettings?.let { settings ->
            val x = (((point.longitude - input.west) / (input.east - input.west)) * (settings.worldSize - 1))
                .roundToInt().coerceIn(0, settings.worldSize - 1)
            val z = (((input.north - point.latitude) / (input.north - input.south)) * (settings.worldSize - 1))
                .roundToInt().coerceIn(0, settings.worldSize - 1)
            return ProceduralWorldGenerator.heightAt(x, z, settings)
        }
        val gx = (((point.longitude - input.west) / (input.east - input.west)) * (input.columns - 1))
            .coerceIn(0.0, (input.columns - 1).toDouble())
        val gy = (((input.north - point.latitude) / (input.north - input.south)) * (input.rows - 1))
            .coerceIn(0.0, (input.rows - 1).toDouble())
        val x0 = floor(gx).toInt()
        val y0 = floor(gy).toInt()
        val x1 = min(input.columns - 1, x0 + 1)
        val y1 = min(input.rows - 1, y0 + 1)
        val fx = gx - x0
        val fy = gy - y0

        val candidates = listOf(
            Triple(y0, x0, (1.0 - fx) * (1.0 - fy)),
            Triple(y0, x1, fx * (1.0 - fy)),
            Triple(y1, x0, (1.0 - fx) * fy),
            Triple(y1, x1, fx * fy)
        )
        var weighted = 0.0
        var weight = 0.0
        for ((row, column, w) in candidates) {
            val value = input.values[row * input.columns + column]
            if (value != null && w > 0.0) {
                weighted += value * w
                weight += w
            }
        }
        if (weight > 0.000001) return weighted / weight

        var nearest: Double? = null
        var nearestDistance = Double.MAX_VALUE
        for (row in 0 until input.rows) {
            for (column in 0 until input.columns) {
                val value = input.values[row * input.columns + column] ?: continue
                val dx = column - gx
                val dy = row - gy
                val distance = dx * dx + dy * dy
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearest = value
                }
            }
        }
        return nearest ?: throw IllegalArgumentException("选区内没有可用高程点")
    }

    private fun insidePolygon(point: Point, polygon: List<Point>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val crosses = ((a.latitude > point.latitude) != (b.latitude > point.latitude)) &&
                (point.longitude < (b.longitude - a.longitude) *
                    (point.latitude - a.latitude) / ((b.latitude - a.latitude) + 1e-12) + a.longitude)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun setBlock(
        sections: MutableMap<Int, IntArray>,
        localX: Int,
        y: Int,
        localZ: Int,
        blockId: Int
    ) {
        val sectionY = Math.floorDiv(y, 16)
        val localY = Math.floorMod(y, 16)
        val blocks = sections.getOrPut(sectionY) { IntArray(4096) }
        blocks[localX * 256 + localZ * 16 + localY] = blockId
    }

    internal fun encodeData3D(heightMap: IntArray): ByteArray =
        encodeData3D(heightMap, IntArray(256) { 1 })

    internal fun encodeData3D(heightMap: IntArray, biomeIds: IntArray): ByteArray {
        require(heightMap.size == 256 && biomeIds.size == 256)
        val output = ByteArrayOutputStream(512 + 24 * 32)
        for (height in heightMap) output.writeShortLE((height + 64).coerceIn(0, 65535))
        // Modern Bedrock stores one 3-D biome palette for each of the 24
        // subchunks (-4 through 19). The index order is XZY: Y changes fastest.
        repeat(24) {
            val indices = IntArray(4096)
            val palette = LinkedHashMap<Int, Int>()
            for (x in 0 until 16) for (z in 0 until 16) {
                val biome = biomeIds[z * 16 + x]
                val paletteIndex = palette.getOrPut(biome) { palette.size }
                for (y in 0 until 16) {
                    val index = x * 256 + z * 16 + y
                    indices[index] = paletteIndex
                }
            }
            if (palette.size == 1) {
                output.write(1) // palette version 1, zero bits per index
                output.writeIntLE(palette.keys.first())
            } else {
                val bits = bitsForPalette(palette.size)
                val perWord = 32 / bits
                output.write((bits shl 1) or 1)
                var start = 0
                while (start < indices.size) {
                    var word = 0
                    for (slot in 0 until perWord) {
                        val index = start + slot
                        if (index < indices.size) word = word or (indices[index] shl (bits * slot))
                    }
                    output.writeIntLE(word)
                    start += perWord
                }
                output.writeIntLE(palette.size)
                palette.keys.forEach { output.writeIntLE(it) }
            }
        }
        return output.toByteArray()
    }

    private fun encodeBlockStorage(blocks: IntArray): ByteArray {
        val palette = LinkedHashMap<Int, Int>()
        palette[AIR] = 0
        for (blockId in blocks) {
            if (!palette.containsKey(blockId)) palette[blockId] = palette.size
        }
        val paletteSize = palette.size
        val bits = bitsForPalette(paletteSize)
        val perWord = 32 / bits
        val output = ByteArrayOutputStream(1 + 4 * ((4096 + perWord - 1) / perWord) + 4 + paletteSize * 64)
        output.write(bits shl 1)

        val blockIndices = IntArray(4096) { index -> palette[blocks[index]] ?: 0 }
        var start = 0
        while (start < 4096) {
            var word = 0
            for (slot in 0 until perWord) {
                val index = start + slot
                if (index < 4096) {
                    word = word or (blockIndices[index] shl (bits * slot))
                }
            }
            output.writeIntLE(word)
            start += perWord
        }

        output.writeIntLE(paletteSize)
        for (blockId in palette.keys) {
            output.write(encodePaletteEntry(blockId))
        }
        return output.toByteArray()
    }

    private fun bitsForPalette(count: Int): Int {
        val valid = intArrayOf(1, 2, 3, 4, 5, 6, 8, 16)
        return valid.firstOrNull { count <= (1 shl it) } ?: 16
    }

    internal fun encodePaletteEntry(blockId: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeCompoundStart("")
        output.writeStringTag("name", blockName(blockId))
        output.writeCompoundStart("states")
        when {
            blockId == BEDROCK -> output.writeByteTag("infiniburn_bit", 0)
            blockId == WATER -> output.writeIntTag("liquid_depth", 0)
            logSpecies(blockId) != null -> output.writeStringTag("pillar_axis", "y")
            leafSpecies(blockId) != null -> {
                output.writeByteTag("persistent_bit", 1)
                output.writeByteTag("update_bit", 0)
            }
        }
        output.writeEnd()
        output.writeIntTag("version", BLOCK_STATE_VERSION)
        output.writeEnd()
        return output.toByteArray()
    }

    private fun logSpecies(blockId: Int): String? = when (blockId) {
        WorldBlockId.OAK_LOG -> "oak"
        WorldBlockId.BIRCH_LOG -> "birch"
        WorldBlockId.SPRUCE_LOG -> "spruce"
        WorldBlockId.JUNGLE_LOG -> "jungle"
        WorldBlockId.ACACIA_LOG -> "acacia"
        WorldBlockId.DARK_OAK_LOG -> "dark_oak"
        WorldBlockId.MANGROVE_LOG -> "mangrove"
        WorldBlockId.CHERRY_LOG -> "cherry"
        else -> null
    }

    private fun leafSpecies(blockId: Int): String? = when (blockId) {
        WorldBlockId.OAK_LEAVES -> "oak"
        WorldBlockId.BIRCH_LEAVES -> "birch"
        WorldBlockId.SPRUCE_LEAVES -> "spruce"
        WorldBlockId.JUNGLE_LEAVES -> "jungle"
        WorldBlockId.ACACIA_LEAVES -> "acacia"
        WorldBlockId.DARK_OAK_LEAVES -> "dark_oak"
        WorldBlockId.MANGROVE_LEAVES -> "mangrove"
        WorldBlockId.CHERRY_LEAVES -> "cherry"
        else -> null
    }

    private fun blockName(blockId: Int): String = when (blockId) {
        AIR -> "minecraft:air"
        BEDROCK -> "minecraft:bedrock"
        STONE -> "minecraft:stone"
        DIRT -> "minecraft:dirt"
        GRASS -> "minecraft:grass_block"
        SAND -> "minecraft:sand"
        WATER -> "minecraft:water"
        BUILDING_WALL -> "minecraft:brick_block"
        BUILDING_ROOF -> "minecraft:stone_bricks"
        WorldBlockId.DEEPSLATE -> "minecraft:deepslate"
        WorldBlockId.GRANITE -> "minecraft:granite"
        WorldBlockId.DIORITE -> "minecraft:diorite"
        WorldBlockId.ANDESITE -> "minecraft:andesite"
        WorldBlockId.GRAVEL -> "minecraft:gravel"
        WorldBlockId.CLAY -> "minecraft:clay"
        WorldBlockId.SNOW_BLOCK -> "minecraft:snow"
        WorldBlockId.SANDSTONE -> "minecraft:sandstone"
        WorldBlockId.RED_SAND -> "minecraft:red_sand"
        WorldBlockId.RED_SANDSTONE -> "minecraft:red_sandstone"
        WorldBlockId.MOSS_BLOCK -> "minecraft:moss_block"
        WorldBlockId.PODZOL -> "minecraft:podzol"
        WorldBlockId.MYCELIUM -> "minecraft:mycelium"
        WorldBlockId.COARSE_DIRT -> "minecraft:coarse_dirt"
        WorldBlockId.MUD -> "minecraft:mud"
        WorldBlockId.NETHERRACK -> "minecraft:netherrack"
        WorldBlockId.CALCITE -> "minecraft:calcite"
        WorldBlockId.PACKED_ICE -> "minecraft:packed_ice"
        WorldBlockId.ICE -> "minecraft:ice"
        WorldBlockId.TUFF -> "minecraft:tuff"
        WorldBlockId.OBSIDIAN -> "minecraft:obsidian"
        WorldBlockId.OAK_LOG -> "minecraft:oak_log"
        WorldBlockId.BIRCH_LOG -> "minecraft:birch_log"
        WorldBlockId.SPRUCE_LOG -> "minecraft:spruce_log"
        WorldBlockId.JUNGLE_LOG -> "minecraft:jungle_log"
        WorldBlockId.ACACIA_LOG -> "minecraft:acacia_log"
        WorldBlockId.DARK_OAK_LOG -> "minecraft:dark_oak_log"
        WorldBlockId.MANGROVE_LOG -> "minecraft:mangrove_log"
        WorldBlockId.CHERRY_LOG -> "minecraft:cherry_log"
        WorldBlockId.OAK_LEAVES -> "minecraft:oak_leaves"
        WorldBlockId.BIRCH_LEAVES -> "minecraft:birch_leaves"
        WorldBlockId.SPRUCE_LEAVES -> "minecraft:spruce_leaves"
        WorldBlockId.JUNGLE_LEAVES -> "minecraft:jungle_leaves"
        WorldBlockId.ACACIA_LEAVES -> "minecraft:acacia_leaves"
        WorldBlockId.DARK_OAK_LEAVES -> "minecraft:dark_oak_leaves"
        WorldBlockId.MANGROVE_LEAVES -> "minecraft:mangrove_leaves"
        WorldBlockId.CHERRY_LEAVES -> "minecraft:cherry_leaves"
        WorldBlockId.COAL_ORE -> "minecraft:coal_ore"
        WorldBlockId.IRON_ORE -> "minecraft:iron_ore"
        WorldBlockId.COPPER_ORE -> "minecraft:copper_ore"
        WorldBlockId.GOLD_ORE -> "minecraft:gold_ore"
        WorldBlockId.REDSTONE_ORE -> "minecraft:redstone_ore"
        WorldBlockId.LAPIS_ORE -> "minecraft:lapis_ore"
        WorldBlockId.DIAMOND_ORE -> "minecraft:diamond_ore"
        WorldBlockId.EMERALD_ORE -> "minecraft:emerald_ore"
        WorldBlockId.DEEPSLATE_COAL_ORE -> "minecraft:deepslate_coal_ore"
        WorldBlockId.DEEPSLATE_IRON_ORE -> "minecraft:deepslate_iron_ore"
        WorldBlockId.DEEPSLATE_COPPER_ORE -> "minecraft:deepslate_copper_ore"
        WorldBlockId.DEEPSLATE_GOLD_ORE -> "minecraft:deepslate_gold_ore"
        WorldBlockId.DEEPSLATE_REDSTONE_ORE -> "minecraft:deepslate_redstone_ore"
        WorldBlockId.DEEPSLATE_LAPIS_ORE -> "minecraft:deepslate_lapis_ore"
        WorldBlockId.DEEPSLATE_DIAMOND_ORE -> "minecraft:deepslate_diamond_ore"
        WorldBlockId.DEEPSLATE_EMERALD_ORE -> "minecraft:deepslate_emerald_ore"
        WorldBlockId.AMETHYST_BLOCK -> "minecraft:amethyst_block"
        WorldBlockId.MOSSY_STONE_BRICKS -> "minecraft:mossy_stone_bricks"
        WorldBlockId.CRACKED_STONE_BRICKS -> "minecraft:cracked_stone_bricks"
        WorldBlockId.CHISELED_SANDSTONE -> "minecraft:chiseled_sandstone"
        WorldBlockId.CUT_SANDSTONE -> "minecraft:cut_sandstone"
        WorldBlockId.SMOOTH_SANDSTONE -> "minecraft:smooth_sandstone"
        WorldBlockId.OBSIDIAN_CRYING -> "minecraft:crying_obsidian"
        WorldBlockId.GOLD_BLOCK -> "minecraft:gold_block"
        WorldBlockId.COPPER_BLOCK -> "minecraft:copper_block"
        WorldBlockId.MOSSY_COBBLESTONE -> "minecraft:mossy_cobblestone"
        WorldBlockId.COBBLESTONE -> "minecraft:cobblestone"
        WorldBlockId.IRON_BARS -> "minecraft:iron_bars"
        WorldBlockId.GLOWSTONE -> "minecraft:glowstone"
        WorldBlockId.GLASS -> "minecraft:glass"
        WorldBlockId.TERRACOTTA -> "minecraft:terracotta"
        WorldBlockId.WHITE_TERRACOTTA -> "minecraft:white_terracotta"
        WorldBlockId.ORANGE_TERRACOTTA -> "minecraft:orange_terracotta"
        WorldBlockId.RED_TERRACOTTA -> "minecraft:red_terracotta"
        WorldBlockId.BLUE_ICE -> "minecraft:blue_ice"
        WorldBlockId.DRIPSTONE_BLOCK -> "minecraft:dripstone_block"
        WorldBlockId.POINTED_DRIPSTONE -> "minecraft:pointed_dripstone"
        WorldBlockId.MUSHROOM_BLOCK -> "minecraft:brown_mushroom_block"
        WorldBlockId.MELON -> "minecraft:melon_block"
        WorldBlockId.HAY_BLOCK -> "minecraft:hay_block"
        else -> throw IllegalArgumentException("未知方块 ID：" + blockId)
    }

    private fun chooseSpawn(input: TerrainInput, metersPerBlock: Int, verticalExaggeration: Double): Int {
        val point = Point((input.west + input.east) / 2.0, (input.south + input.north) / 2.0)
        val elevation = sampleElevation(input, point)
        val verticalScale = verticalMetersPerBlock(
            metersPerBlock,
            input.minimum,
            input.maximum,
            verticalExaggeration
        )
        return (SEA_LEVEL + ((elevation - input.minimum) / verticalScale).roundToInt() + 1)
            .coerceIn(MIN_Y + 4, MAX_Y - 4)
    }

    private fun createMcWorldArchive(worldFolder: File, output: OutputStream): Long {
        val files = worldFolder.walkTopDown().filter { it.isFile }.toList()
        require(File(worldFolder, "db/CURRENT").exists()) { "LevelDB 没有生成 CURRENT 文件" }
        require(File(worldFolder, "level.dat").exists()) { "世界包缺少 level.dat" }
        require(File(worldFolder, "levelname.txt").exists()) { "世界包缺少 levelname.txt" }
        require(files.any { it.name.startsWith("MANIFEST-") && it.parentFile == File(worldFolder, "db") }) {
            "世界包缺少 LevelDB MANIFEST"
        }

        val counter = CountingOutputStream(BufferedOutputStream(output))
        ZipOutputStream(counter).use { zip ->
            // LevelDB SST files are already compressed; favor speed over recompressing them.
            zip.setLevel(Deflater.BEST_SPEED)
            for (file in files) {
                val relative = file.absolutePath.substring(worldFolder.absolutePath.length + 1)
                    .replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(relative))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
        }
        return counter.bytesWritten
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0L
            private set

        override fun write(value: Int) {
            out.write(value)
            bytesWritten++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            bytesWritten += length
        }
    }

    private fun validateLevelDat(file: File) {
        val bytes = file.readBytes()
        require(bytes.size > 16) { "level.dat 文件太小" }
        val headerStorageVersion = readIntLE(bytes, 0)
        val payloadSize = readIntLE(bytes, 4)
        require(headerStorageVersion == 10 && payloadSize == bytes.size - 8) {
            "level.dat 文件头或 NBT 长度不正确"
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        return radius * 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun ByteArrayOutputStream.writeStringPayload(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65535) { "NBT 字符串过长" }
        writeShortLE(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeTagHeader(type: Int, name: String) {
        write(type)
        writeStringPayload(name)
    }

    private fun ByteArrayOutputStream.writeCompoundStart(name: String) {
        writeTagHeader(10, name)
    }

    private fun ByteArrayOutputStream.writeStringTag(name: String, value: String) {
        writeTagHeader(8, name)
        writeStringPayload(value)
    }

    private fun ByteArrayOutputStream.writeIntTag(name: String, value: Int) {
        writeTagHeader(3, name)
        writeIntLE(value)
    }

    private fun ByteArrayOutputStream.writeLongTag(name: String, value: Long) {
        writeTagHeader(4, name)
        writeLongLE(value)
    }

    private fun ByteArrayOutputStream.writeIntListTag(name: String, values: IntArray) {
        writeTagHeader(9, name)
        write(3) // TAG_Int elements
        writeIntLE(values.size)
        values.forEach { writeIntLE(it) }
    }

    private fun ByteArrayOutputStream.writeByteTag(name: String, value: Int) {
        writeTagHeader(1, name)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeEnd() {
        write(0)
    }

    private fun ByteArrayOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLongLE(value: Long) {
        for (i in 0 until 8) write(((value ushr (8 * i)) and 0xFF).toInt())
    }

    private fun intBytesLE(value: Int): ByteArray {
        val output = ByteArrayOutputStream(4)
        output.writeIntLE(value)
        return output.toByteArray()
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
