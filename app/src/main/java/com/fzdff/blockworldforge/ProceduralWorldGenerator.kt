package com.fzdff.blockworldforge

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor

internal data class ProceduralWorldSettings(
    val seed: Long = 20261109L,
    val worldSize: Int = 256,
    val relief: Int = 58,
    val mountainRuggedness: Int = 62,
    val plateauAmount: Int = 24,
    val biomeCount: Int = 29,
    val caveDensity: Int = 48,
    val oreRichness: Int = 52,
    val treeDensity: Int = 58,
    val structureDensity: Int = 42,
    val waterLevel: Int = 42,
    val detailPreset: String = "standard",
    val enableCaves: Boolean = true,
    val enableOres: Boolean = true,
    val enableTrees: Boolean = true,
    val enableStructures: Boolean = true,
    val enableWater: Boolean = true,
    val structureTypes: Set<String> = DEFAULT_STRUCTURES
) {
    fun normalized(): ProceduralWorldSettings = copy(
        worldSize = worldSize.coerceIn(128, 512),
        relief = relief.coerceIn(0, 100),
        mountainRuggedness = mountainRuggedness.coerceIn(0, 100),
        plateauAmount = plateauAmount.coerceIn(0, 100),
        biomeCount = biomeCount.coerceIn(20, ProceduralWorldGenerator.BIOMES.size),
        caveDensity = caveDensity.coerceIn(0, 100),
        oreRichness = oreRichness.coerceIn(0, 100),
        treeDensity = treeDensity.coerceIn(0, 100),
        structureDensity = structureDensity.coerceIn(0, 100),
        waterLevel = waterLevel.coerceIn(16, 64),
        detailPreset = detailPreset.takeIf { it in setOf("compact", "standard", "high") } ?: "standard",
        structureTypes = structureTypes.intersect(DEFAULT_STRUCTURES)
    )

    fun toJson(): JSONObject {
        val value = normalized()
        return JSONObject()
            .put("format", "blockworld-procedural-v1")
            .put("seed", value.seed)
            .put("worldSize", value.worldSize)
            .put("relief", value.relief)
            .put("mountainRuggedness", value.mountainRuggedness)
            .put("plateauAmount", value.plateauAmount)
            .put("biomeCount", value.biomeCount)
            .put("caveDensity", value.caveDensity)
            .put("oreRichness", value.oreRichness)
            .put("treeDensity", value.treeDensity)
            .put("structureDensity", value.structureDensity)
            .put("waterLevel", value.waterLevel)
            .put("detailPreset", value.detailPreset)
            .put("enableCaves", value.enableCaves)
            .put("enableOres", value.enableOres)
            .put("enableTrees", value.enableTrees)
            .put("enableStructures", value.enableStructures)
            .put("enableWater", value.enableWater)
            .put("structureTypes", JSONArray(value.structureTypes.sorted()))
    }

    fun estimatedArchiveBytes(): Long {
        val value = normalized()
        val chunksPerSide = (value.worldSize + 15L) / 16L
        val chunks = chunksPerSide * chunksPerSide
        val densityFactor = when (value.detailPreset) {
            "compact" -> 5_000L
            "high" -> 17_000L
            else -> 9_000L
        }
        val featureFactor = 1.0 +
            value.caveDensity * 0.0025 +
            value.oreRichness * 0.0018 +
            value.treeDensity * 0.0020 +
            value.structureDensity * 0.0015
        return (chunks * densityFactor * featureFactor).toLong().coerceAtLeast(256_000L)
    }

    companion object {
        val DEFAULT_STRUCTURES = setOf(
            "village", "desert_temple", "jungle_temple", "ruined_portal",
            "mineshaft", "stronghold", "ocean_ruin", "watchtower"
        )

        fun fromJson(raw: String): ProceduralWorldSettings {
            val json = JSONObject(raw)
            val types = json.optJSONArray("structureTypes") ?: JSONArray()
            val structures = buildSet {
                for (i in 0 until types.length()) add(types.optString(i))
            }
            return ProceduralWorldSettings(
                seed = json.optLong("seed", 20261109L),
                worldSize = json.optInt("worldSize", 256),
                relief = json.optInt("relief", 58),
                mountainRuggedness = json.optInt("mountainRuggedness", 62),
                plateauAmount = json.optInt("plateauAmount", 24),
                biomeCount = json.optInt("biomeCount", 29),
                caveDensity = json.optInt("caveDensity", 48),
                oreRichness = json.optInt("oreRichness", 52),
                treeDensity = json.optInt("treeDensity", 58),
                structureDensity = json.optInt("structureDensity", 42),
                waterLevel = json.optInt("waterLevel", 42),
                detailPreset = json.optString("detailPreset", "standard"),
                enableCaves = json.optBoolean("enableCaves", true),
                enableOres = json.optBoolean("enableOres", true),
                enableTrees = json.optBoolean("enableTrees", true),
                enableStructures = json.optBoolean("enableStructures", true),
                enableWater = json.optBoolean("enableWater", true),
                structureTypes = if (structures.isEmpty()) DEFAULT_STRUCTURES else structures
            ).normalized()
        }
    }
}

internal data class BiomeDefinition(
    val key: String,
    val displayName: String,
    val bedrockId: Int,
    val surfaceBlock: Int,
    val soilBlock: Int,
    val treeStyle: String,
    val temperature: Double,
    val moisture: Double
)

internal object WorldBlockId {
    const val AIR = 0
    const val BEDROCK = 1
    const val STONE = 2
    const val DIRT = 3
    const val GRASS = 4
    const val SAND = 5
    const val WATER = 6
    const val BRICKS = 7
    const val STONE_BRICKS = 8
    const val DEEPSLATE = 9
    const val GRANITE = 10
    const val DIORITE = 11
    const val ANDESITE = 12
    const val GRAVEL = 13
    const val CLAY = 14
    const val SNOW_BLOCK = 15
    const val SANDSTONE = 16
    const val RED_SAND = 17
    const val RED_SANDSTONE = 18
    const val MOSS_BLOCK = 19
    const val PODZOL = 20
    const val MYCELIUM = 21
    const val COARSE_DIRT = 22
    const val MUD = 23
    const val NETHERRACK = 24
    const val CALCITE = 25
    const val PACKED_ICE = 26
    const val ICE = 27
    const val TUFF = 28
    const val OBSIDIAN = 29
    const val OAK_LOG = 30
    const val BIRCH_LOG = 31
    const val SPRUCE_LOG = 32
    const val JUNGLE_LOG = 33
    const val ACACIA_LOG = 34
    const val DARK_OAK_LOG = 35
    const val MANGROVE_LOG = 36
    const val CHERRY_LOG = 37
    const val OAK_LEAVES = 40
    const val BIRCH_LEAVES = 41
    const val SPRUCE_LEAVES = 42
    const val JUNGLE_LEAVES = 43
    const val ACACIA_LEAVES = 44
    const val DARK_OAK_LEAVES = 45
    const val MANGROVE_LEAVES = 46
    const val CHERRY_LEAVES = 47
    const val COAL_ORE = 50
    const val IRON_ORE = 51
    const val COPPER_ORE = 52
    const val GOLD_ORE = 53
    const val REDSTONE_ORE = 54
    const val LAPIS_ORE = 55
    const val DIAMOND_ORE = 56
    const val EMERALD_ORE = 57
    const val DEEPSLATE_COAL_ORE = 83
    const val DEEPSLATE_IRON_ORE = 84
    const val DEEPSLATE_COPPER_ORE = 85
    const val DEEPSLATE_GOLD_ORE = 86
    const val DEEPSLATE_REDSTONE_ORE = 87
    const val DEEPSLATE_LAPIS_ORE = 88
    const val DEEPSLATE_DIAMOND_ORE = 89
    const val DEEPSLATE_EMERALD_ORE = 90
    const val AMETHYST_BLOCK = 58
    const val MOSSY_STONE_BRICKS = 60
    const val CRACKED_STONE_BRICKS = 61
    const val CHISELED_SANDSTONE = 62
    const val CUT_SANDSTONE = 63
    const val SMOOTH_SANDSTONE = 64
    const val OBSIDIAN_CRYING = 65
    const val GOLD_BLOCK = 66
    const val COPPER_BLOCK = 67
    const val MOSSY_COBBLESTONE = 68
    const val COBBLESTONE = 69
    const val IRON_BARS = 70
    const val GLOWSTONE = 71
    const val GLASS = 72
    const val TERRACOTTA = 73
    const val WHITE_TERRACOTTA = 74
    const val ORANGE_TERRACOTTA = 75
    const val RED_TERRACOTTA = 76
    const val BLUE_ICE = 77
    const val DRIPSTONE_BLOCK = 78
    const val POINTED_DRIPSTONE = 79
    const val MUSHROOM_BLOCK = 80
    const val MELON = 81
    const val HAY_BLOCK = 82
}

internal object ProceduralWorldGenerator {
    val BIOMES = listOf(
        BiomeDefinition("plains", "平原", 1, WorldBlockId.GRASS, WorldBlockId.DIRT, "oak", 0.65, 0.48),
        BiomeDefinition("forest", "森林", 4, WorldBlockId.GRASS, WorldBlockId.DIRT, "oak", 0.55, 0.72),
        BiomeDefinition("birch_forest", "白桦林", 27, WorldBlockId.GRASS, WorldBlockId.DIRT, "birch", 0.62, 0.62),
        BiomeDefinition("roofed_forest", "黑森林", 29, WorldBlockId.PODZOL, WorldBlockId.DIRT, "dark_oak", 0.50, 0.88),
        BiomeDefinition("taiga", "针叶林", 5, WorldBlockId.PODZOL, WorldBlockId.DIRT, "spruce", 0.22, 0.66),
        BiomeDefinition("mega_taiga", "巨型针叶林", 32, WorldBlockId.PODZOL, WorldBlockId.COARSE_DIRT, "giant_spruce", 0.18, 0.55),
        BiomeDefinition("cold_taiga", "寒冷针叶林", 30, WorldBlockId.SNOW_BLOCK, WorldBlockId.DIRT, "spruce", 0.08, 0.53),
        BiomeDefinition("desert", "沙漠", 2, WorldBlockId.SAND, WorldBlockId.SANDSTONE, "dead", 0.98, 0.08),
        BiomeDefinition("badlands", "恶地", 37, WorldBlockId.RED_SAND, WorldBlockId.TERRACOTTA, "dead", 0.96, 0.12),
        BiomeDefinition("savanna", "热带草原", 35, WorldBlockId.COARSE_DIRT, WorldBlockId.DIRT, "acacia", 0.92, 0.22),
        BiomeDefinition("jungle", "丛林", 21, WorldBlockId.GRASS, WorldBlockId.MUD, "jungle", 0.92, 0.94),
        BiomeDefinition("bamboo_jungle", "竹林", 168, WorldBlockId.MOSS_BLOCK, WorldBlockId.DIRT, "jungle", 0.90, 0.98),
        BiomeDefinition("swamp", "沼泽", 6, WorldBlockId.MUD, WorldBlockId.DIRT, "oak", 0.72, 0.94),
        BiomeDefinition("mangrove_swamp", "红树林沼泽", 184, WorldBlockId.MUD, WorldBlockId.MUD, "mangrove", 0.94, 0.99),
        BiomeDefinition("mushroom_fields", "蘑菇岛", 14, WorldBlockId.MYCELIUM, WorldBlockId.DIRT, "mushroom", 0.78, 0.78),
        BiomeDefinition("cherry_grove", "樱花林", 185, WorldBlockId.GRASS, WorldBlockId.DIRT, "cherry", 0.56, 0.62),
        BiomeDefinition("meadow", "草甸", 177, WorldBlockId.GRASS, WorldBlockId.DIRT, "sparse", 0.42, 0.60),
        BiomeDefinition("grove", "雪林", 178, WorldBlockId.SNOW_BLOCK, WorldBlockId.DIRT, "spruce", 0.14, 0.70),
        BiomeDefinition("snowy_plains", "积雪平原", 12, WorldBlockId.SNOW_BLOCK, WorldBlockId.DIRT, "sparse_spruce", 0.02, 0.40),
        BiomeDefinition("ice_spikes", "冰刺之地", 140, WorldBlockId.PACKED_ICE, WorldBlockId.SNOW_BLOCK, "sparse_spruce", 0.00, 0.30),
        BiomeDefinition("frozen_peaks", "冰封山峰", 181, WorldBlockId.PACKED_ICE, WorldBlockId.CALCITE, "none", -0.05, 0.22),
        BiomeDefinition("jagged_peaks", "尖峭山峰", 180, WorldBlockId.STONE, WorldBlockId.CALCITE, "none", 0.08, 0.22),
        BiomeDefinition("stony_peaks", "裸岩山峰", 182, WorldBlockId.STONE, WorldBlockId.GRAVEL, "none", 0.46, 0.12),
        BiomeDefinition("badlands_plateau", "恶地高原", 38, WorldBlockId.TERRACOTTA, WorldBlockId.RED_SANDSTONE, "dead", 0.92, 0.10),
        BiomeDefinition("warm_ocean", "暖水海洋", 40, WorldBlockId.SAND, WorldBlockId.CLAY, "none", 0.99, 0.70),
        BiomeDefinition("cold_ocean", "寒冷海洋", 44, WorldBlockId.GRAVEL, WorldBlockId.CLAY, "none", 0.10, 0.70),
        BiomeDefinition("deep_ocean", "深海", 24, WorldBlockId.GRAVEL, WorldBlockId.CLAY, "none", 0.44, 0.76),
        BiomeDefinition("flower_forest", "繁花森林", 132, WorldBlockId.GRASS, WorldBlockId.DIRT, "birch", 0.61, 0.83),
        BiomeDefinition("pale_garden", "苍白花园", 186, WorldBlockId.MOSS_BLOCK, WorldBlockId.DIRT, "pale", 0.48, 0.70)
    )

    fun heightAt(x: Int, z: Int, settings: ProceduralWorldSettings): Double {
        val seed = settings.seed
        val continent = fbm(x * 0.0028, z * 0.0028, seed + 11, 4)
        val broad = fbm(x * 0.0065, z * 0.0065, seed + 23, 5)
        val ridgeNoise = fbm(x * 0.015, z * 0.015, seed + 31, 4)
        val detailMultiplier = when (settings.detailPreset) {
            "compact" -> 0.62
            "high" -> 1.55
            else -> 1.0
        }
        val detail = fbm(x * 0.046 * detailMultiplier, z * 0.046 * detailMultiplier, seed + 41, 3)
        val ridged = 1.0 - abs(ridgeNoise * 2.0 - 1.0)
        val mountainMask = smoothStep(0.48, 0.78, continent) * (0.45 + settings.mountainRuggedness / 180.0)
        val rawRelief = (continent - 0.45) * 1.35 +
            (broad - 0.5) * 0.55 +
            (ridged - 0.42) * mountainMask * 1.25 +
            (detail - 0.5) * (0.12 + settings.mountainRuggedness / 220.0) * detailMultiplier
        val amplitude = 16.0 + settings.relief * 1.42
        val plateau = settings.plateauAmount / 100.0
        val shaped = rawRelief * (1.0 - plateau * 0.42) +
            (if (rawRelief > 0.42) 0.42 + (rawRelief - 0.42) * 0.28 else rawRelief) * plateau * 0.42
        return (settings.waterLevel - 24.0 + shaped * amplitude).coerceIn(-42.0, 196.0)
    }

    fun biomeAt(x: Int, z: Int, height: Double, settings: ProceduralWorldSettings): BiomeDefinition {
        val count = settings.biomeCount.coerceIn(20, BIOMES.size)
        val broad = fbm(x * 0.0032, z * 0.0032, settings.seed + 991, 3)
        val moisture = fbm(x * 0.008, z * 0.008, settings.seed + 997, 3)
        val climate = (broad * 0.64 + moisture * 0.36).coerceIn(0.0, 0.999999)
        var index = floor(climate * count).toInt().coerceIn(0, count - 1)
        val temperature = fbm(x * 0.0018, z * 0.0018, settings.seed + 1009, 3) - height * 0.002
        if (height > 115 && temperature < 0.42) {
            index = when {
                temperature < 0.17 -> BIOMES.indexOfFirst { it.key == "frozen_peaks" }
                temperature < 0.30 -> BIOMES.indexOfFirst { it.key == "jagged_peaks" }
                else -> BIOMES.indexOfFirst { it.key == "stony_peaks" }
            }.coerceAtMost(count - 1)
        }
        if (height < settings.waterLevel - 8) {
            index = when {
                temperature > 0.72 -> BIOMES.indexOfFirst { it.key == "warm_ocean" }
                temperature < 0.28 -> BIOMES.indexOfFirst { it.key == "cold_ocean" }
                else -> BIOMES.indexOfFirst { it.key == "deep_ocean" }
            }.coerceAtMost(count - 1)
        }
        return BIOMES[index.coerceIn(0, count - 1)]
    }

    fun caveAt(x: Int, y: Int, z: Int, settings: ProceduralWorldSettings): Boolean {
        if (!settings.enableCaves || settings.caveDensity <= 0 || y < -58) return false
        val caveNoise = noise3(x * 0.052, y * 0.068, z * 0.052, settings.seed + 2003)
        val threshold = 0.085 + settings.caveDensity / 1550.0
        // A single coherent 3-D field forms winding tunnels and wider chambers.
        return abs(caveNoise - 0.5) < threshold
    }

    fun oreAt(x: Int, y: Int, z: Int, biome: BiomeDefinition, settings: ProceduralWorldSettings): Int {
        if (!settings.enableOres || settings.oreRichness <= 0) return WorldBlockId.STONE
        val richness = settings.oreRichness / 100.0
        val depthEligible = y in -58..118
        if (!depthEligible) return WorldBlockId.STONE
        val vein = noise3(x * 0.16, y * 0.17, z * 0.16, settings.seed + 3109)
        val roll = hash01(x, y, z, settings.seed + 3119)
        val chance = 0.28 * richness
        val baseOre = when {
            y in 10..118 && vein > 0.72 && roll < chance -> WorldBlockId.COAL_ORE
            y in -20..92 && vein > 0.76 && roll < chance * 0.78 -> WorldBlockId.IRON_ORE
            y in -12..96 && vein > 0.79 && roll < chance * 0.72 -> WorldBlockId.COPPER_ORE
            y in -36..52 && vein > 0.83 && roll < chance * 0.40 -> WorldBlockId.GOLD_ORE
            y in -52..28 && vein > 0.87 && roll < chance * 0.30 -> WorldBlockId.REDSTONE_ORE
            y in -34..40 && vein > 0.85 && roll < chance * 0.28 -> WorldBlockId.LAPIS_ORE
            y in -58..18 && vein > 0.90 && roll < chance * 0.18 -> WorldBlockId.DIAMOND_ORE
            y in -24..72 && biome.key.contains("peak") && roll < chance * 0.13 -> WorldBlockId.EMERALD_ORE
            else -> WorldBlockId.STONE
        }
        if (y >= 0) return baseOre
        return when (baseOre) {
            WorldBlockId.COAL_ORE -> WorldBlockId.DEEPSLATE_COAL_ORE
            WorldBlockId.IRON_ORE -> WorldBlockId.DEEPSLATE_IRON_ORE
            WorldBlockId.COPPER_ORE -> WorldBlockId.DEEPSLATE_COPPER_ORE
            WorldBlockId.GOLD_ORE -> WorldBlockId.DEEPSLATE_GOLD_ORE
            WorldBlockId.REDSTONE_ORE -> WorldBlockId.DEEPSLATE_REDSTONE_ORE
            WorldBlockId.LAPIS_ORE -> WorldBlockId.DEEPSLATE_LAPIS_ORE
            WorldBlockId.DIAMOND_ORE -> WorldBlockId.DEEPSLATE_DIAMOND_ORE
            WorldBlockId.EMERALD_ORE -> WorldBlockId.DEEPSLATE_EMERALD_ORE
            else -> baseOre
        }
    }

    fun treeCandidate(cellX: Int, cellZ: Int, settings: ProceduralWorldSettings): Pair<Int, Int>? {
        if (!settings.enableTrees || settings.treeDensity <= 0) return null
        val cellSize = 7
        val roll = hash01(cellX, cellZ, 0, settings.seed + 4001)
        if (roll > settings.treeDensity / 112.0) return null
        val jitterX = (hash01(cellX, cellZ, 1, settings.seed + 4003) * cellSize).toInt()
        val jitterZ = (hash01(cellX, cellZ, 2, settings.seed + 4007) * cellSize).toInt()
        return (cellX * cellSize + jitterX) to (cellZ * cellSize + jitterZ)
    }

    fun treeProfile(biome: BiomeDefinition, x: Int, z: Int, settings: ProceduralWorldSettings): TreeProfile {
        val variation = (hash01(x, z, 3, settings.seed + 4021) * 4).toInt()
        return when (biome.treeStyle) {
            "birch" -> TreeProfile(WorldBlockId.BIRCH_LOG, WorldBlockId.BIRCH_LEAVES, 5 + variation, "round")
            "spruce" -> TreeProfile(WorldBlockId.SPRUCE_LOG, WorldBlockId.SPRUCE_LEAVES, 7 + variation, "cone")
            "giant_spruce" -> TreeProfile(WorldBlockId.SPRUCE_LOG, WorldBlockId.SPRUCE_LEAVES, 11 + variation, "giant_cone")
            "jungle" -> TreeProfile(WorldBlockId.JUNGLE_LOG, WorldBlockId.JUNGLE_LEAVES, 9 + variation, "umbrella")
            "acacia" -> TreeProfile(WorldBlockId.ACACIA_LOG, WorldBlockId.ACACIA_LEAVES, 6 + variation, "flat")
            "dark_oak" -> TreeProfile(WorldBlockId.DARK_OAK_LOG, WorldBlockId.DARK_OAK_LEAVES, 7 + variation, "wide")
            "mangrove" -> TreeProfile(WorldBlockId.MANGROVE_LOG, WorldBlockId.MANGROVE_LEAVES, 7 + variation, "mangrove")
            "cherry" -> TreeProfile(WorldBlockId.CHERRY_LOG, WorldBlockId.CHERRY_LEAVES, 6 + variation, "dome")
            "pale" -> TreeProfile(WorldBlockId.DARK_OAK_LOG, WorldBlockId.OAK_LEAVES, 7 + variation, "wide")
            "mushroom" -> TreeProfile(WorldBlockId.OAK_LOG, WorldBlockId.MUSHROOM_BLOCK, 6 + variation, "mushroom")
            "sparse_spruce" -> TreeProfile(WorldBlockId.SPRUCE_LOG, WorldBlockId.SPRUCE_LEAVES, 5 + variation, "cone")
            "none", "dead" -> TreeProfile(WorldBlockId.OAK_LOG, WorldBlockId.OAK_LEAVES, 0, "none")
            "sparse" -> TreeProfile(WorldBlockId.OAK_LOG, WorldBlockId.OAK_LEAVES, 5 + variation, "round")
            else -> TreeProfile(WorldBlockId.OAK_LOG, WorldBlockId.OAK_LEAVES, 6 + variation, "round")
        }
    }

    fun structureAnchor(cellX: Int, cellZ: Int, settings: ProceduralWorldSettings): StructureAnchor? {
        if (!settings.enableStructures || settings.structureDensity <= 0 || settings.structureTypes.isEmpty()) return null
        val spacing = 112
        if (hash01(cellX, cellZ, 9, settings.seed + 5003) > settings.structureDensity / 100.0) return null
        val x = cellX * spacing + 18 + (hash01(cellX, cellZ, 10, settings.seed + 5009) * 76).toInt()
        val z = cellZ * spacing + 18 + (hash01(cellX, cellZ, 11, settings.seed + 5011) * 76).toInt()
        val types = settings.structureTypes.sorted()
        val type = types[(hash01(cellX, cellZ, 12, settings.seed + 5017) * types.size).toInt().coerceIn(0, types.lastIndex)]
        return StructureAnchor(x, z, type)
    }

    fun hash01(x: Int, y: Int, z: Int, seed: Long): Double {
        var h = seed xor (x.toLong() * -7046029254386353131L)
        h = h xor (y.toLong() * -4658895280553007687L)
        h = h xor (z.toLong() * -7723592293110705685L)
        h = (h xor (h ushr 30)) * -4658895280553007687L
        h = (h xor (h ushr 27)) * -7723592293110705685L
        h = h xor (h ushr 31)
        return (h ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    fun previewHeights(settings: ProceduralWorldSettings, resolution: Int = 64): DoubleArray {
        val size = settings.worldSize.coerceAtLeast(128)
        return DoubleArray(resolution * resolution) { index ->
            val x = index % resolution * size / resolution
            val z = index / resolution * size / resolution
            heightAt(x, z, settings)
        }
    }

    private fun fbm(x: Double, z: Double, seed: Long, octaves: Int): Double {
        var amplitude = 0.55
        var frequency = 1.0
        var total = 0.0
        var normalizer = 0.0
        repeat(octaves) {
            total += valueNoise2(x * frequency, z * frequency, seed + it * 101L) * amplitude
            normalizer += amplitude
            amplitude *= 0.5
            frequency *= 2.0
        }
        return if (normalizer == 0.0) 0.5 else total / normalizer
    }

    private fun valueNoise2(x: Double, z: Double, seed: Long): Double {
        val x0 = floor(x).toInt()
        val z0 = floor(z).toInt()
        val fx = smooth(fraction(x))
        val fz = smooth(fraction(z))
        val a = hash01(x0, 0, z0, seed)
        val b = hash01(x0 + 1, 0, z0, seed)
        val c = hash01(x0, 0, z0 + 1, seed)
        val d = hash01(x0 + 1, 0, z0 + 1, seed)
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz)
    }

    private fun noise3(x: Double, y: Double, z: Double, seed: Long): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val z0 = floor(z).toInt()
        val fx = smooth(fraction(x))
        val fy = smooth(fraction(y))
        val fz = smooth(fraction(z))
        val c000 = hash01(x0, y0, z0, seed)
        val c100 = hash01(x0 + 1, y0, z0, seed)
        val c010 = hash01(x0, y0 + 1, z0, seed)
        val c110 = hash01(x0 + 1, y0 + 1, z0, seed)
        val c001 = hash01(x0, y0, z0 + 1, seed)
        val c101 = hash01(x0 + 1, y0, z0 + 1, seed)
        val c011 = hash01(x0, y0 + 1, z0 + 1, seed)
        val c111 = hash01(x0 + 1, y0 + 1, z0 + 1, seed)
        val x00 = lerp(c000, c100, fx)
        val x10 = lerp(c010, c110, fx)
        val x01 = lerp(c001, c101, fx)
        val x11 = lerp(c011, c111, fx)
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz)
    }

    private fun smooth(value: Double): Double = value * value * (3.0 - 2.0 * value)
    private fun fraction(value: Double): Double = value - floor(value)
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    private fun smoothStep(edge0: Double, edge1: Double, value: Double): Double {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}

internal data class TreeProfile(val logBlock: Int, val leafBlock: Int, val height: Int, val shape: String)
internal data class StructureAnchor(val x: Int, val z: Int, val type: String)
