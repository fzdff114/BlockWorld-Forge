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
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
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
    val maximumElevation: Double
)

internal object BedrockWorldExporter {
    private const val MIN_Y = -64
    private const val MAX_Y = 319
    private const val SEA_LEVEL = 8
    private const val CHUNK_VERSION = 41
    private const val SUB_CHUNK_VERSION = 9
    private const val BLOCK_STATE_VERSION = 18168865
    private const val MAX_COLUMNS = 262144L
    private const val MAX_EDGE_BLOCKS = 512
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

    private data class Point(val longitude: Double, val latitude: Double)
    private data class TerrainInput(
        val polygon: List<Point>,
        val west: Double,
        val east: Double,
        val south: Double,
        val north: Double,
        val rows: Int,
        val columns: Int,
        val values: Array<Double?>,
        val minimum: Double,
        val maximum: Double,
        val widthMeters: Double,
        val depthMeters: Double
    )

    internal data class GridSize(val width: Int, val depth: Int)

    fun export(
        context: Context,
        worldName: String,
        regionJson: String,
        metersPerBlock: Int,
        outputUri: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): BedrockExportReport {
        require(metersPerBlock in listOf(1, 2, 4, 8, 16)) { "不支持的地图比例" }
        val input = parseRegion(regionJson)
        val dimensions = scaledDimensions(input.widthMeters, input.depthMeters, metersPerBlock)
        val columnCount = dimensions.width.toLong() * dimensions.depth.toLong()
        require(dimensions.width <= MAX_EDGE_BLOCKS && dimensions.depth <= MAX_EDGE_BLOCKS && columnCount <= MAX_COLUMNS) {
            "当前比例会生成过大的地图（" + dimensions.width + "×" + dimensions.depth +
                " 格）。请改用更大的比例尺，例如 1:8 或 1:16。"
        }

        val tempRoot = File(context.cacheDir, "atlas-export-" + UUID.randomUUID().toString())
        val worldFolder = File(tempRoot, "world")
        val dbFolder = File(worldFolder, "db")
        val archive = File(tempRoot, "Atlas.mcworld")
        if (!dbFolder.mkdirs()) throw IOException("无法创建临时世界数据库目录")

        try {
            val chunkCount = writeWorldDatabase(
                dbFolder = dbFolder,
                input = input,
                size = dimensions,
                metersPerBlock = metersPerBlock,
                onProgress = onProgress
            )

            File(worldFolder, "levelname.txt").writeText(worldName.take(64), Charsets.UTF_8)
            val spawn = chooseSpawn(input, metersPerBlock)
            val levelDat = encodeLevelDat(
                worldName = worldName,
                spawnX = dimensions.width / 2,
                spawnY = spawn,
                spawnZ = dimensions.depth / 2
            )
            File(worldFolder, "level.dat").writeBytes(levelDat)
            validateLevelDat(File(worldFolder, "level.dat"))
            createMcWorldArchive(worldFolder, archive)
            validateMcWorldArchive(archive)

            val output = context.contentResolver.openOutputStream(outputUri, "w")
                ?: throw IOException("无法打开目标文件")
            output.use { target ->
                BufferedInputStream(FileInputStream(archive)).use { source ->
                    source.copyTo(target, 64 * 1024)
                }
            }

            return BedrockExportReport(
                widthBlocks = dimensions.width,
                depthBlocks = dimensions.depth,
                chunkCount = chunkCount,
                archiveBytes = archive.length(),
                metersPerBlock = metersPerBlock,
                minimumElevation = input.minimum,
                maximumElevation = input.maximum
            )
        } finally {
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
        nbt.writeIntTag("NetworkVersion", 594)
        nbt.writeStringTag("LevelName", worldName.take(64))
        nbt.writeLongTag("RandomSeed", System.currentTimeMillis())
        nbt.writeIntTag("SpawnX", spawnX)
        nbt.writeIntTag("SpawnY", spawnY)
        nbt.writeIntTag("SpawnZ", spawnZ)
        nbt.writeLongTag("Time", 0L)
        nbt.writeLongTag("LastPlayed", System.currentTimeMillis())
        nbt.writeIntTag("Generator", 1)
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
        val totalChunks = chunksX * chunksZ
        var chunksWritten = 0
        val verticalMetersPerBlock = max(
            metersPerBlock.toDouble(),
            max(0.0, input.maximum) / 280.0
        )
        val sink = DatabaseBatchSink(db)

        try {
            for (chunkZ in 0 until chunksZ) {
                for (chunkX in 0 until chunksX) {
                    val sections = HashMap<Int, IntArray>()
                    val heightMap = IntArray(256) { MIN_Y }
                    var columnsInChunk = 0

                    for (localZ in 0 until 16) {
                        val worldZ = chunkZ * 16 + localZ
                        if (worldZ >= size.depth) continue
                        for (localX in 0 until 16) {
                            val worldX = chunkX * 16 + localX
                            if (worldX >= size.width) continue
                            val point = coordinateAt(input, size, worldX, worldZ)
                            if (!insidePolygon(point, input.polygon)) continue
                            val elevation = sampleElevation(input, point)
                            val surfaceY = (SEA_LEVEL + (elevation / verticalMetersPerBlock).roundToInt())
                                .coerceIn(MIN_Y + 4, MAX_Y - 5)

                            setBlock(sections, localX, MIN_Y, localZ, BEDROCK)
                            for (y in MIN_Y + 1 until surfaceY - 3) {
                                setBlock(sections, localX, y, localZ, STONE)
                            }
                            for (y in surfaceY - 3 until surfaceY) {
                                setBlock(sections, localX, y, localZ, DIRT)
                            }
                            setBlock(sections, localX, surfaceY, localZ, if (elevation < 0.0) SAND else GRASS)
                            if (surfaceY < SEA_LEVEL) {
                                for (y in surfaceY + 1..SEA_LEVEL) {
                                    setBlock(sections, localX, y, localZ, WATER)
                                }
                                heightMap[localZ * 16 + localX] = SEA_LEVEL + 1
                            } else {
                                heightMap[localZ * 16 + localX] = surfaceY + 1
                            }
                            columnsInChunk++
                        }
                    }

                    if (columnsInChunk > 0) {
                        val versionKey = chunkKey(chunkX, chunkZ, TAG_VERSION)
                        val data3dKey = chunkKey(chunkX, chunkZ, TAG_DATA_3D)
                        val finalizedKey = chunkKey(chunkX, chunkZ, TAG_FINALIZED)
                        sink.put(versionKey, byteArrayOf(CHUNK_VERSION.toByte()))
                        sink.put(finalizedKey, intBytesLE(2))
                        sink.put(data3dKey, encodeData3D(heightMap))
                        if (firstVersionKey == null) firstVersionKey = versionKey

                        for ((sectionY, blocks) in sections.toSortedMap()) {
                            val subKey = chunkKey(chunkX, chunkZ, TAG_SUB_CHUNK, sectionY)
                            val encoded = encodeSubChunk(sectionY, blocks)
                            sink.put(subKey, encoded)
                            if (firstSubChunkKey == null) firstSubChunkKey = subKey
                        }
                        chunksWritten++
                    }
                    if ((chunksWritten % 8 == 0) || (chunkZ == chunksZ - 1 && chunkX == chunksX - 1)) {
                        onProgress(chunkZ * chunksX + chunkX + 1, totalChunks)
                    }
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

        private fun flush() {
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

    private fun coordinateAt(input: TerrainInput, size: GridSize, x: Int, z: Int): Point {
        val longitude = input.west + ((x + 0.5) / size.width.toDouble()) * (input.east - input.west)
        val latitude = input.north - ((z + 0.5) / size.depth.toDouble()) * (input.north - input.south)
        return Point(longitude, latitude)
    }

    private fun sampleElevation(input: TerrainInput, point: Point): Double {
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

    private fun encodeData3D(heightMap: IntArray): ByteArray {
        val output = ByteArrayOutputStream(540)
        for (height in heightMap) output.writeShortLE(height)
        // Uniform plains biome palette: one 4x4 storage, followed by 23 "same as below" sections.
        output.write(1)
        output.writeIntLE(1)
        repeat(23) { output.write(0xFF) }
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

    private fun encodePaletteEntry(blockId: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeCompoundStart("")
        output.writeStringTag("name", blockName(blockId))
        output.writeCompoundStart("states")
        if (blockId == WATER) output.writeIntTag("liquid_depth", 0)
        output.writeEnd()
        output.writeIntTag("version", BLOCK_STATE_VERSION)
        output.writeEnd()
        return output.toByteArray()
    }

    private fun blockName(blockId: Int): String = when (blockId) {
        AIR -> "minecraft:air"
        BEDROCK -> "minecraft:bedrock"
        STONE -> "minecraft:stone"
        DIRT -> "minecraft:dirt"
        GRASS -> "minecraft:grass_block"
        SAND -> "minecraft:sand"
        WATER -> "minecraft:water"
        else -> throw IllegalArgumentException("未知方块 ID：" + blockId)
    }

    private fun chooseSpawn(input: TerrainInput, metersPerBlock: Int): Int {
        val point = Point((input.west + input.east) / 2.0, (input.south + input.north) / 2.0)
        val elevation = sampleElevation(input, point)
        val verticalMetersPerBlock = max(metersPerBlock.toDouble(), max(0.0, input.maximum) / 280.0)
        return (SEA_LEVEL + (elevation / verticalMetersPerBlock).roundToInt() + 1)
            .coerceIn(MIN_Y + 4, MAX_Y - 4)
    }

    private fun createMcWorldArchive(worldFolder: File, archive: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { zip ->
            val files = worldFolder.walkTopDown().filter { it.isFile }.toList()
            require(File(worldFolder, "db/CURRENT").exists()) { "LevelDB 没有生成 CURRENT 文件" }
            for (file in files) {
                val relative = file.absolutePath.substring(worldFolder.absolutePath.length + 1)
                    .replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(relative))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
        }
    }

    private fun validateMcWorldArchive(archive: File) {
        ZipFile(archive).use { zip ->
            require(zip.getEntry("level.dat") != null) { "世界包缺少 level.dat" }
            require(zip.getEntry("levelname.txt") != null) { "世界包缺少 levelname.txt" }
            require(zip.getEntry("db/CURRENT") != null) { "世界包缺少 LevelDB CURRENT" }
            val hasManifest = zip.entries().asSequence().any { it.name.startsWith("db/MANIFEST-") }
            require(hasManifest) { "世界包缺少 LevelDB MANIFEST" }
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
