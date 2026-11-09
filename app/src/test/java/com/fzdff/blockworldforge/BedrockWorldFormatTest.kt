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
    fun terrainFoundationStartsNearLowestSurfaceInsteadOfAlwaysAtMinY() {
        val floor = BedrockWorldExporter.terrainFloorY(500.0, 900.0, 1)
        assertTrue(floor > -64)
        assertTrue(floor < 300)
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
