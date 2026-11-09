package com.fzdff.blockworldforge

import org.iq80.leveldb.CompressionType
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class BedrockWorldFormatTest {
    @Test
    fun scaleMapsMetersToBlocks() {
        val size = BedrockWorldExporter.scaledDimensions(1000.0, 500.0, 4)
        assertEquals(250, size.width)
        assertEquals(125, size.depth)
    }

    @Test
    fun largeMapFromReportedScreenshotFitsChunkedExportBudget() {
        val size = BedrockWorldExporter.GridSize(5044, 4759)
        assertEquals(94_168L, BedrockWorldExporter.chunkCountForSize(size))
        assertEquals(94_168L, BedrockWorldExporter.validateDimensions(size))
    }

    @Test
    fun mapBeyondChunkBudgetIsRejectedBeforeDatabaseCreation() {
        val error = runCatching {
            BedrockWorldExporter.validateDimensions(BedrockWorldExporter.GridSize(8192, 8192))
        }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test
    fun verticalExaggerationMakesLowReliefTerrainMoreVisible() {
        val naturalScale = BedrockWorldExporter.verticalMetersPerBlock(4, 100.0, 120.0, 1.0)
        val doubledScale = BedrockWorldExporter.verticalMetersPerBlock(4, 100.0, 120.0, 2.0)
        assertEquals(4.0, naturalScale, 0.0001)
        assertEquals(2.0, doubledScale, 0.0001)
        val naturalRelief = BedrockWorldExporter.surfaceYForElevation(120.0, 100.0, naturalScale) -
            BedrockWorldExporter.surfaceYForElevation(100.0, 100.0, naturalScale)
        val doubledRelief = BedrockWorldExporter.surfaceYForElevation(120.0, 100.0, doubledScale) -
            BedrockWorldExporter.surfaceYForElevation(100.0, 100.0, doubledScale)
        assertTrue(doubledRelief > naturalRelief)
        assertEquals(10, doubledRelief)
    }

    @Test
    fun verticalExaggerationIsBoundedForMobileWorldHeight() {
        assertNotNull(runCatching {
            BedrockWorldExporter.verticalMetersPerBlock(4, 0.0, 100.0, 0.5)
        }.exceptionOrNull())
        assertNotNull(runCatching {
            BedrockWorldExporter.verticalMetersPerBlock(4, 0.0, 100.0, 4.0)
        }.exceptionOrNull())
        assertTrue(BedrockWorldExporter.verticalMetersPerBlock(4, -1200.0, 1800.0, 3.0) >= 1800.0 / 280.0)
    }

    @Test
    fun highAltitudePlateauKeepsItsLocalReliefAtTheSelectedHorizontalScale() {
        val lowlandScale = BedrockWorldExporter.verticalMetersPerBlock(4, 20.0, 80.0, 1.0)
        val plateauScale = BedrockWorldExporter.verticalMetersPerBlock(4, 1520.0, 1580.0, 1.0)
        assertEquals(4.0, lowlandScale, 0.0001)
        assertEquals(4.0, plateauScale, 0.0001)
        val lowlandRelief = BedrockWorldExporter.surfaceYForElevation(80.0, 20.0, lowlandScale) -
            BedrockWorldExporter.surfaceYForElevation(20.0, 20.0, lowlandScale)
        val plateauRelief = BedrockWorldExporter.surfaceYForElevation(1580.0, 1520.0, plateauScale) -
            BedrockWorldExporter.surfaceYForElevation(1520.0, 1520.0, plateauScale)
        assertEquals(lowlandRelief, plateauRelief)
        assertEquals(15, plateauRelief)
    }

    @Test
    fun terrainFoundationUsesLocalDatumInsteadOfAbsoluteSeaLevelAltitude() {
        assertEquals(4, BedrockWorldExporter.terrainFloorY(500.0, 900.0, 1))
        assertEquals(4, BedrockWorldExporter.terrainFloorY(2500.0, 2520.0, 1))
    }

    @Test
    fun levelDatUsesInfiniteGeneratorAndNotFlatPreset() {
        val bytes = BedrockWorldExporter.encodeLevelDat("Atlas Terrain", 20, 80, 20)
        val marker = "Generator".toByteArray(Charsets.UTF_8)
        val offset = indexOfBytes(bytes, marker)
        assertTrue(offset >= 0)
        assertEquals(
            BedrockWorldExporter.WORLD_GENERATOR_INFINITE,
            readIntLE(bytes, offset + marker.size)
        )
        assertEquals(false, bytes.toString(Charsets.ISO_8859_1).contains("FlatWorldLayers"))
    }

    @Test
    fun worldSurfaceHeightChangesWithRealElevationSamples() {
        val low = BedrockWorldExporter.surfaceYForElevation(20.0, 20.0, 2.0)
        val high = BedrockWorldExporter.surfaceYForElevation(120.0, 20.0, 2.0)
        assertTrue(high > low)
        assertEquals(50, high - low)
    }

    @Test
    fun terrariumZoomRejectsLargeRegionsInsteadOfUsingVeryCoarseTiles() {
        val zoom = chooseTerrariumZoom(-0.01, 0.01, -0.01, 0.01, 196)
        assertTrue(zoom in 12..14)
        val failure = runCatching {
            chooseTerrariumZoom(-60.0, 60.0, -40.0, 40.0, 196)
        }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun terrariumElevationDecoderUsesMapzenEncoding() {
        assertEquals(0.0, decodeTerrariumElevation(128, 0, 0), 0.0001)
        assertEquals(128.5, decodeTerrariumElevation(128, 128, 128), 0.0001)
        assertEquals(-32768.0, decodeTerrariumElevation(0, 0, 0), 0.0001)
    }

    @Test
    fun terrariumTilePixelIndexAccountsForBufferedAndHiResTiles() {
        assertEquals(2, terrariumTilePixelIndex(0, 260))
        assertEquals(257, terrariumTilePixelIndex(255, 260))
        assertEquals(2, terrariumTilePixelIndex(0, 516))
        assertEquals(512, terrariumTilePixelIndex(255, 516))
        assertEquals(0, terrariumTilePixelIndex(0, 256))
    }

    @Test
    fun renderedBasemapBuildingPolygonReadsVectorTileHeightFields() {
        val feature = """
            {
              "type":"Feature",
              "id":12345,
              "properties":{
                "building":"yes",
                "render_height":42.5,
                "render_min_height":3.0,
                "name":"测试楼"
              },
              "geometry":{
                "type":"Polygon",
                "coordinates":[[
                  [116.3990,39.8990],
                  [116.4010,39.8990],
                  [116.4010,39.9010],
                  [116.3990,39.9010],
                  [116.3990,39.8990]
                ]]
              }
            }
        """.trimIndent()
        val bounds = listOf(
            GeoCoordinate(39.902, 116.398),
            GeoCoordinate(39.902, 116.402),
            GeoCoordinate(39.898, 116.402),
            GeoCoordinate(39.898, 116.398)
        )
        val buildings = RenderedMapBuildingReader.parseFeature(feature, bounds)
        assertEquals(1, buildings.size)
        assertEquals(42.5, buildings.single().heightMeters, 0.0001)
        assertEquals(3.0, buildings.single().minHeightMeters, 0.0001)
        assertEquals("测试楼", buildings.single().name)
        assertTrue(buildings.single().source.startsWith("OpenFreeMap"))
    }

    @Test
    fun renderedBasemapBuildingParserSupportsMultiPolygonAndLevelsFallback() {
        val feature = """
            {
              "type":"Feature",
              "properties":{"building":"yes","building:levels":"5"},
              "geometry":{
                "type":"MultiPolygon",
                "coordinates":[
                  [[[116.4000,39.9000],[116.4005,39.9000],[116.4005,39.9005],[116.4000,39.9005],[116.4000,39.9000]]],
                  [[[116.4010,39.9000],[116.4015,39.9000],[116.4015,39.9005],[116.4010,39.9005],[116.4010,39.9000]]]
                ]
              }
            }
        """.trimIndent()
        val bounds = listOf(
            GeoCoordinate(39.902, 116.399),
            GeoCoordinate(39.902, 116.403),
            GeoCoordinate(39.899, 116.403),
            GeoCoordinate(39.899, 116.399)
        )
        val buildings = RenderedMapBuildingReader.parseFeature(feature, bounds)
        assertEquals(2, buildings.size)
        assertEquals(16.0, buildings.first().heightMeters, 0.0001)
    }

    @Test
    fun renderedBasemapBuildingParserRejectsNonBuildingPolygons() {
        val feature = """
            {
              "type":"Feature",
              "properties":{"class":"water","name":"pond"},
              "geometry":{
                "type":"Polygon",
                "coordinates":[[
                  [116.3990,39.8990],[116.4010,39.8990],
                  [116.4010,39.9010],[116.3990,39.9010],
                  [116.3990,39.8990]
                ]]
              }
            }
        """.trimIndent()
        val bounds = listOf(
            GeoCoordinate(39.902, 116.398),
            GeoCoordinate(39.902, 116.402),
            GeoCoordinate(39.898, 116.402),
            GeoCoordinate(39.898, 116.398)
        )
        assertTrue(RenderedMapBuildingReader.parseFeature(feature, bounds).isEmpty())
    }

    @Test
    fun chineseBuildingImporterConvertsWebMercatorCoordinatesToWgs84() {
        val origin = ChinaBuildingDatasetImporter.toWgs84(0.0, 0.0, "EPSG:3857")
        assertEquals(0.0, origin.first, 0.000001)
        assertEquals(0.0, origin.second, 0.000001)
        val beijing = ChinaBuildingDatasetImporter.toWgs84(12_995_000.0, 4_823_000.0, "EPSG:3857")
        assertTrue(beijing.first in 116.0..117.0)
        assertTrue(beijing.second in 39.0..40.0)
    }

    @Test
    fun chineseBuildingImporterAcceptsGeographicAndCleansHeightValues() {
        val point = ChinaBuildingDatasetImporter.toWgs84(116.4, 39.9, "EPSG:4326")
        assertEquals(116.4, point.first, 0.000001)
        assertEquals(39.9, point.second, 0.000001)
        assertEquals(18.5, ChinaBuildingDatasetImporter.parseHeightMeters("18.5 m")!!, 0.0001)
        assertEquals(null, ChinaBuildingDatasetImporter.parseHeightMeters("unknown"))
        assertEquals(null, ChinaBuildingDatasetImporter.parseHeightMeters("0"))
    }

    @Test
    fun osmBuildingHeightConvertsMetersToWorldBlocks() {
        assertEquals(12, BedrockWorldExporter.buildingHeightBlocks(24.0, 2.0))
        assertEquals(2, BedrockWorldExporter.buildingHeightBlocks(1.0, 4.0))
        assertEquals(96, BedrockWorldExporter.buildingHeightBlocks(1000.0, 1.0))
    }

    @Test
    fun buildingPaletteUsesBrickWallsAndStoneBrickRoofs() {
        val wall = BedrockWorldExporter.encodePaletteEntry(7).toString(Charsets.ISO_8859_1)
        val roof = BedrockWorldExporter.encodePaletteEntry(8).toString(Charsets.ISO_8859_1)
        assertTrue(wall.contains("minecraft:bricks"))
        assertTrue(roof.contains("minecraft:stone_bricks"))
    }

    @Test
    fun chunkKeyUsesLittleEndianCoordinatesAndSignedSubChunkY() {
        val key = BedrockWorldExporter.chunkKey(3, -2, 0x2F.toByte(), -4)
        assertEquals(10, key.size)
        assertArrayEquals(byteArrayOf(3, 0, 0, 0), key.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(-2, -1, -1, -1), key.copyOfRange(4, 8))
        assertEquals(0x2F, key[8].toInt() and 0xFF)
        assertEquals((-4).toByte(), key[9])
    }

    @Test
    fun subChunkStartsWithVersionNineAndSignedY() {
        val blocks = IntArray(4096)
        blocks[0] = 1
        blocks[1] = 2
        blocks[2] = 3
        val encoded = BedrockWorldExporter.encodeSubChunk(-4, blocks)
        assertEquals(9, encoded[0].toInt() and 0xFF)
        assertEquals(1, encoded[1].toInt() and 0xFF)
        assertEquals((-4).toByte(), encoded[2])
        assertEquals(4, encoded[3].toInt() and 0xFF)
    }

    @Test
    fun levelDatHasStorageVersionTenAndCorrectPayloadLength() {
        val bytes = BedrockWorldExporter.encodeLevelDat("Atlas Test", 12, 70, 34)
        assertEquals(10, readIntLE(bytes, 0))
        assertEquals(bytes.size - 8, readIntLE(bytes, 4))
        assertEquals(10, bytes[8].toInt() and 0xFF)
    }

    @Test
    fun mcpeLevelDbCompressionCanWriteCloseReopenAndRead() {
        val folder = File(System.getProperty("java.io.tmpdir"), "atlas-db-test-" + UUID.randomUUID())
        try {
            val db = Iq80DBFactory.factory.open(
                folder,
                Options().createIfMissing(true).compressionType(CompressionType.ZLIB_RAW)
            )
            val key = BedrockWorldExporter.chunkKey(1, 2, 0x2C.toByte())
            val value = byteArrayOf(41)
            db.put(key, value)
            db.close()

            val reopened = Iq80DBFactory.factory.open(
                folder,
                Options().createIfMissing(false).compressionType(CompressionType.ZLIB_RAW)
            )
            try {
                assertNotNull(reopened.get(key))
                assertArrayEquals(value, reopened.get(key))
            } finally {
                reopened.close()
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun bedrockPaletteIncludesRequiredInfiniburnState() {
        val encoded = BedrockWorldExporter.encodePaletteEntry(1)
        val text = encoded.toString(Charsets.ISO_8859_1)
        assertEquals(true, text.contains("minecraft:bedrock"))
        assertEquals(true, text.contains("infiniburn_bit"))
    }

    @Test
    fun levelDatContainsTargetBedrockNetworkVersionAndClientVersion() {
        val bytes = BedrockWorldExporter.encodeLevelDat("Atlas Test", 12, 70, 34)
        val marker = "NetworkVersion".toByteArray(Charsets.UTF_8)
        val offset = indexOfBytes(bytes, marker)
        assertEquals(true, offset >= 0)
        val valueOffset = offset + marker.size
        assertArrayEquals(
            byteArrayOf(0x91.toByte(), 0x08, 0x00, 0x00),
            bytes.copyOfRange(valueOffset, valueOffset + 4)
        )

        val metadata = bytes.toString(Charsets.ISO_8859_1)
        assertEquals(true, metadata.contains("MinimumCompatibleClientVersion"))
        assertEquals(true, metadata.contains("lastOpenedWithVersion"))
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        for (start in 0..haystack.size - needle.size) {
            var matches = true
            for (index in needle.indices) {
                if (haystack[start + index] != needle[index]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
