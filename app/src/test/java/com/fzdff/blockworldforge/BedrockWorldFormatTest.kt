package com.fzdff.blockworldforge

import org.iq80.leveldb.CompressionType
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.Iq80DBFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val text = bytes.toString(Charsets.ISO_8859_1)
        assertEquals(true, text.contains("MinimumCompatibleClientVersion"))
        assertEquals(true, text.contains("lastOpenedWithVersion"))
        assertEquals(true, text.contains("NetworkVersion"))
        val marker = "NetworkVersion"
        val offset = text.indexOf(marker)
        assertEquals(true, offset >= 0)
        val valueOffset = offset + marker.length
        assertEquals(2193, readIntLE(bytes, valueOffset))
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
