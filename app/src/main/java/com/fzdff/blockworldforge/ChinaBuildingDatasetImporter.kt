package com.fzdff.blockworldforge

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln

internal data class ImportedBuildingPolygon(
    val coordinates: List<Pair<Double, Double>>, // longitude, latitude
    val heightMeters: Double,
    val name: String?,
    val source: String
)

internal data class ChinaBuildingImportResult(
    val buildings: List<ImportedBuildingPolygon>,
    val sourceFiles: Int,
    val recordsRead: Int,
    val recordsWithHeight: Int,
    val missingHeight: Int,
    val truncated: Boolean
)

internal object ChinaBuildingDatasetImporter {
    private const val MAX_ARCHIVE_BYTES = 1_500L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 900L * 1024L * 1024L
    private const val MAX_FEATURES = 20_000
    private const val EARTH_RADIUS = 6_378_137.0
    private val HEIGHT_FIELDS = setOf(
        "height", "heightm", "heightmeter", "heightmeters", "heightinm",
        "heightinmeter", "heightest", "buildingheight", "buildingheightm",
        "buildingh", "hgt", "hgtm", "bheight", "bldgheight"
    )

    fun importZip(
        context: Context,
        uri: Uri,
        west: Double,
        east: Double,
        south: Double,
        north: Double,
        sourceLabel: String = "3D-GloBFP",
        onProgress: (String) -> Unit = {}
    ): ChinaBuildingImportResult {
        require(west < east && south < north) { "请先在地图上圈选一个有效区域" }
        val root = File(context.cacheDir, "china-building-import-${System.nanoTime()}")
        if (!root.mkdirs()) throw IOException("无法创建建筑数据临时目录")

        try {
            onProgress("解压建筑数据包…")
            val extracted = extractShapeFiles(context, uri, root, onProgress)
            val shapefiles = extracted.filter { it.extension.equals("shp", true) }
            if (shapefiles.isEmpty()) {
                throw IOException("压缩包里没有 .shp 文件。请把同一图层的 .shp、.dbf、.shx、.prj 一起放进 ZIP。")
            }

            val buildings = ArrayList<ImportedBuildingPolygon>()
            var totalRecords = 0
            var recordsWithHeight = 0
            var missingHeight = 0
            var truncated = false
            var parsedFiles = 0

            shapefiles.forEachIndexed { fileIndex, shp ->
                if (buildings.size >= MAX_FEATURES) {
                    truncated = true
                    return@forEachIndexed
                }
                val dbf = File(shp.parentFile, shp.nameWithoutExtension + ".dbf")
                    .takeIf { it.isFile }
                    ?: File(shp.parentFile, shp.nameWithoutExtension + ".DBF").takeIf { it.isFile }
                    ?: throw IOException("缺少与 ${shp.name} 对应的 .dbf 属性表")
                val prj = listOf(
                    File(shp.parentFile, shp.nameWithoutExtension + ".prj"),
                    File(shp.parentFile, shp.nameWithoutExtension + ".PRJ")
                ).firstOrNull { it.isFile }?.readText(Charsets.UTF_8).orEmpty()

                onProgress("读取建筑轮廓 ${fileIndex + 1}/${shapefiles.size}：${shp.name}")
                val result = parseShapefile(
                    shp = shp,
                    dbf = dbf,
                    projection = prj,
                    west = west,
                    east = east,
                    south = south,
                    north = north,
                    remaining = MAX_FEATURES - buildings.size,
                    sourceLabel = sourceLabel
                )
                totalRecords += result.recordsRead
                recordsWithHeight += result.recordsWithHeight
                missingHeight += result.missingHeight
                buildings.addAll(result.buildings)
                truncated = truncated || result.truncated
                parsedFiles++
            }

            if (buildings.isEmpty()) {
                throw IOException(
                    "已读取 ${totalRecords} 条建筑记录，但没有在当前选区内找到带有效 height 字段的建筑。请确认导入的是目标省份/城市的建筑高度 Shapefile。"
                )
            }
            return ChinaBuildingImportResult(
                buildings = buildings,
                sourceFiles = parsedFiles,
                recordsRead = totalRecords,
                recordsWithHeight = recordsWithHeight,
                missingHeight = missingHeight,
                truncated = truncated
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun extractShapeFiles(
        context: Context,
        uri: Uri,
        root: File,
        onProgress: (String) -> Unit
    ): List<File> {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选 ZIP 文件")
        val extracted = ArrayList<File>()
        var totalBytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val normalized = entry.name.replace('\\', '/')
                if (normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
                    throw IOException("压缩包包含不安全路径")
                }
                val extension = normalized.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (extension !in setOf("shp", "dbf", "prj", "shx")) {
                    zip.closeEntry()
                    continue
                }
                val target = File(root, normalized)
                val canonicalRoot = root.canonicalPath + File.separator
                if (!target.canonicalPath.startsWith(canonicalRoot)) {
                    throw IOException("压缩包路径越界")
                }
                target.parentFile?.mkdirs()
                var entryBytes = 0L
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_ARCHIVE_BYTES) {
                            throw IOException("建筑数据包解压后超过 1.5 GB。请只下载当前省份/城市的单个 Shapefile 图层。")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                if (entryBytes > 0L) extracted.add(target)
                zip.closeEntry()
            }
        }
        if (extracted.none { it.extension.equals("shp", true) }) {
            onProgress("未找到 Shapefile 图层")
        }
        return extracted
    }

    private data class DbfField(
        val name: String,
        val offset: Int,
        val length: Int
    )

    private data class DbfLayout(
        val recordCount: Int,
        val headerLength: Int,
        val recordLength: Int,
        val fields: List<DbfField>,
        val heightField: DbfField,
        val nameField: DbfField?
    )

    private data class ShapeParseResult(
        val buildings: List<ImportedBuildingPolygon>,
        val recordsRead: Int,
        val recordsWithHeight: Int,
        val missingHeight: Int,
        val truncated: Boolean
    )

    private fun parseShapefile(
        shp: File,
        dbf: File,
        projection: String,
        west: Double,
        east: Double,
        south: Double,
        north: Double,
        remaining: Int,
        sourceLabel: String
    ): ShapeParseResult {
        val layout = readDbfLayout(dbf)
        val output = ArrayList<ImportedBuildingPolygon>()
        var recordsRead = 0
        var recordsWithHeight = 0
        var missingHeight = 0
        var truncated = false

        RandomAccessFile(dbf, "r").use { dbfFile ->
        RandomAccessFile(shp, "r").use { shape ->
            if (shape.length() < 100L) throw IOException("Shapefile 文件头损坏：${shp.name}")
            shape.seek(32L)
            val fileShapeType = readIntLE(shape)
            if (fileShapeType !in setOf(5, 15, 25)) {
                throw IOException("暂不支持 ${shp.name} 的几何类型 $fileShapeType；需要 Polygon / PolygonZ / PolygonM。")
            }
            shape.seek(100L)
            val recordLimit = minOf(layout.recordCount.toLong(), 2_000_000L)

            while (shape.filePointer + 8L <= shape.length() && recordsRead.toLong() < recordLimit) {
                val recordNumber = readIntBE(shape)
                val contentBytes = readIntBE(shape).toLong() * 2L
                val contentStart = shape.filePointer
                val contentEnd = contentStart + contentBytes
                if (recordNumber <= 0 || contentBytes < 4L || contentEnd > shape.length()) {
                    throw IOException("Shapefile 记录长度无效：${shp.name}")
                }
                val shapeType = readIntLE(shape)
                recordsRead++
                val dbfRecord = recordsRead - 1

                if (shapeType == 0) {
                    shape.seek(contentEnd)
                    continue
                }
                if (shapeType !in setOf(5, 15, 25)) {
                    shape.seek(contentEnd)
                    continue
                }

                // Polygon record: bbox, part count, point count, part indexes, XY points.
                if (shape.filePointer + 40L > contentEnd) {
                    shape.seek(contentEnd)
                    continue
                }
                val minX = readDoubleLE(shape)
                val minY = readDoubleLE(shape)
                val maxX = readDoubleLE(shape)
                val maxY = readDoubleLE(shape)
                val partCount = readIntLE(shape)
                val pointCount = readIntLE(shape)
                if (partCount <= 0 || pointCount < 4 || partCount > 10_000 ||
                    pointCount > 500_000 || shape.filePointer + partCount * 4L + pointCount * 16L > contentEnd
                ) {
                    shape.seek(contentEnd)
                    continue
                }

                val rawBounds = listOf(
                    toWgs84(minX, minY, projection),
                    toWgs84(maxX, maxY, projection)
                )
                val recordWest = minOf(rawBounds[0].first, rawBounds[1].first)
                val recordEast = maxOf(rawBounds[0].first, rawBounds[1].first)
                val recordSouth = minOf(rawBounds[0].second, rawBounds[1].second)
                val recordNorth = maxOf(rawBounds[0].second, rawBounds[1].second)
                if (recordEast < west || recordWest > east || recordNorth < south || recordSouth > north) {
                    shape.seek(contentEnd)
                    continue
                }

                val parts = IntArray(partCount) { readIntLE(shape) }
                val points = ArrayList<Pair<Double, Double>>(pointCount)
                repeat(pointCount) {
                    val x = readDoubleLE(shape)
                    val y = readDoubleLE(shape)
                    points.add(toWgs84(x, y, projection))
                }
                shape.seek(contentEnd)

                val height = readDbfNumber(dbfFile, layout, dbfRecord, layout.heightField)
                if (height == null || height !in 1.5..500.0) {
                    missingHeight++
                    continue
                }
                recordsWithHeight++

                // One building is expected to have one exterior ring. For multipart
                // records, retain the largest ring and ignore inner courtyard rings.
                val rings = parts.indices.map { index ->
                    val start = parts[index]
                    val end = if (index == parts.lastIndex) pointCount else parts[index + 1]
                    if (start < 0 || end > pointCount || end - start < 4) emptyList()
                    else points.subList(start, end)
                }.filter { it.size >= 4 }
                val polygon = rings.maxByOrNull { abs(signedArea(it)) } ?: continue
                val coordinates = if (polygon.first() == polygon.last()) polygon.dropLast(1) else polygon
                if (coordinates.size < 3) continue
                if (coordinates.any { it.first !in -180.0..180.0 || it.second !in -90.0..90.0 }) continue

                val name = layout.nameField?.let { readDbfText(dbfFile, layout, dbfRecord, it) }
                    ?.takeIf { it.isNotBlank() }
                output.add(
                    ImportedBuildingPolygon(
                        coordinates = coordinates,
                        heightMeters = height,
                        name = name,
                        source = if (sourceLabel == "China Urban Building Footprint Height Dataset (2018)") {
                            sourceLabel
                        } else {
                            "3D-GloBFP (CC BY 4.0)"
                        }
                    )
                )
                if (output.size >= remaining) {
                    truncated = true
                    break
                }
            }
        }
        }
        return ShapeParseResult(output, recordsRead, recordsWithHeight, missingHeight, truncated)
    }

    private fun readDbfLayout(file: File): DbfLayout {
        RandomAccessFile(file, "r").use { dbf ->
            if (dbf.length() < 33L) throw IOException("DBF 属性表损坏：${file.name}")
            dbf.seek(4L)
            val records = readIntLE(dbf)
            val headerLength = readUnsignedShortLE(dbf)
            val recordLength = readUnsignedShortLE(dbf)
            if (records < 0 || headerLength < 33 || recordLength < 2 || headerLength > dbf.length()) {
                throw IOException("DBF 文件头无效：${file.name}")
            }
            val fields = ArrayList<DbfField>()
            var offset = 1
            dbf.seek(32L)
            while (dbf.filePointer < headerLength - 1L) {
                val first = dbf.read()
                if (first < 0 || first == 0x0D) break
                val nameBytes = ByteArray(10)
                dbf.readFully(nameBytes)
                val name = (byteArrayOf(first.toByte()) + nameBytes)
                    .takeWhile { it.toInt() != 0 }
                    .toByteArray()
                    .toString(Charsets.US_ASCII)
                    .trim()
                dbf.readUnsignedByte() // field type
                dbf.skipBytes(4) // field address, not used
                val length = dbf.readUnsignedByte()
                dbf.readUnsignedByte() // decimal count
                dbf.skipBytes(14)
                if (name.isNotBlank() && length > 0) {
                    fields.add(DbfField(name, offset, length))
                    offset += length
                }
            }
            val heightField = fields.firstOrNull {
                normalizeFieldName(it.name) in HEIGHT_FIELDS
            } ?: fields.firstOrNull {
                val key = normalizeFieldName(it.name)
                key.contains("height") || key == "hgt"
            } ?: throw IOException(
                "DBF 中没有识别到建筑高度字段。可识别字段：${fields.joinToString { it.name }}。请使用带 height（米）字段的数据。"
            )
            val nameField = fields.firstOrNull {
                normalizeFieldName(it.name) in setOf("name", "namecn", "buildingname", "bldgname")
            }
            if (offset + 1 > recordLength) throw IOException("DBF 记录字段长度超出范围")
            return DbfLayout(records, headerLength, recordLength, fields, heightField, nameField)
        }
    }

    private fun normalizeFieldName(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun readDbfNumber(dbf: RandomAccessFile, layout: DbfLayout, row: Int, field: DbfField): Double? {
        val raw = readDbfText(dbf, layout, row, field)
        val match = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").find(raw.replace(',', '.'))
        return match?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun readDbfText(dbf: RandomAccessFile, layout: DbfLayout, row: Int, field: DbfField): String {
        if (row !in 0 until layout.recordCount) return ""
        val position = layout.headerLength.toLong() +
            row.toLong() * layout.recordLength.toLong() + field.offset
        if (position + field.length > dbf.length()) return ""
        dbf.seek(position)
        val bytes = ByteArray(field.length)
        dbf.readFully(bytes)
        return bytes.toString(Charsets.ISO_8859_1).trim().trim('\u0000')
    }

    internal fun toWgs84(x: Double, y: Double, projection: String): Pair<Double, Double> {
        val definition = projection.lowercase(Locale.ROOT)
        if (definition.contains("3857") || definition.contains("900913") ||
            definition.contains("pseudo-mercator") || definition.contains("web_mercator")
        ) {
            val longitude = Math.toDegrees(x / EARTH_RADIUS)
            val latitude = Math.toDegrees(2.0 * atan(exp(y / EARTH_RADIUS)) - PI / 2.0)
            return longitude to latitude
        }
        if (x in -180.0..180.0 && y in -90.0..90.0) {
            return x to y
        }
        if (definition.contains("4490") || definition.contains("4326") ||
            definition.contains("gcs_cgcs2000") || definition.contains("wgs_1984")
        ) {
            throw IOException("Shapefile 声明为地理坐标系，但坐标值超出经纬度范围")
        }
        throw IOException("暂不支持此 Shapefile 投影。请先在 QGIS 中另存为 EPSG:4326（WGS 84）后导入。")
    }

    internal fun parseHeightMeters(raw: String): Double? {
        val value = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
            .find(raw.trim().replace(',', '.'))?.value?.toDoubleOrNull()
        return value?.takeIf { it.isFinite() && it in 1.5..500.0 }
    }

    private fun signedArea(points: List<Pair<Double, Double>>): Double {
        var area = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            area += a.first * b.second - b.first * a.second
        }
        return area / 2.0
    }

    private fun readIntLE(file: RandomAccessFile): Int {
        val b0 = file.readUnsignedByte()
        val b1 = file.readUnsignedByte()
        val b2 = file.readUnsignedByte()
        val b3 = file.readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readIntBE(file: RandomAccessFile): Int {
        val b0 = file.readUnsignedByte()
        val b1 = file.readUnsignedByte()
        val b2 = file.readUnsignedByte()
        val b3 = file.readUnsignedByte()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readDoubleLE(file: RandomAccessFile): Double {
        var bits = 0L
        repeat(8) { index ->
            bits = bits or (file.readUnsignedByte().toLong() shl (8 * index))
        }
        return java.lang.Double.longBitsToDouble(bits)
    }

    private fun readUnsignedShortLE(file: RandomAccessFile): Int {
        val low = file.readUnsignedByte()
        val high = file.readUnsignedByte()
        return low or (high shl 8)
    }
}
