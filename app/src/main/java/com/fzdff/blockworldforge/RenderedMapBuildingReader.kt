package com.fzdff.blockworldforge

import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.max

internal data class RenderedBuildingReadResult(
    val buildings: List<OsmBuilding>,
    val renderedFeatureCount: Int,
    val queriedLayerIds: List<String>
)

internal object RenderedMapBuildingReader {
    private val BUILDING_LAYER_IDS = arrayOf("building", "building-3d")
    private const val SOURCE_NAME = "OpenFreeMap / OpenMapTiles vector building layer"

    fun readVisibleBuildings(
        map: MapLibreMap,
        mapView: MapView,
        boundary: List<GeoCoordinate>
    ): RenderedBuildingReadResult {
        require(boundary.size >= 3) { "地图选区至少需要三个边界点" }
        if (mapView.width <= 0 || mapView.height <= 0) {
            return RenderedBuildingReadResult(emptyList(), 0, BUILDING_LAYER_IDS.toList())
        }

        val west = boundary.minOf { it.longitude }
        val east = boundary.maxOf { it.longitude }
        val south = boundary.minOf { it.latitude }
        val north = boundary.maxOf { it.latitude }

        val screenCorners = listOf(
            map.projection.toScreenLocation(LatLng(north, west)),
            map.projection.toScreenLocation(LatLng(north, east)),
            map.projection.toScreenLocation(LatLng(south, west)),
            map.projection.toScreenLocation(LatLng(south, east))
        )
        val bounds = RectF(
            screenCorners.minOf { it.x },
            screenCorners.minOf { it.y },
            screenCorners.maxOf { it.x },
            screenCorners.maxOf { it.y }
        )
        if (!bounds.intersect(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())) {
            return RenderedBuildingReadResult(emptyList(), 0, BUILDING_LAYER_IDS.toList())
        }

        // Liberty's 2D fill layer is visible at neighborhood zoom; its 3D
        // extrusion layer is visible at street zoom. Query both so capture does
        // not depend on which visual layer happens to be active at this zoom.
        val rendered = runCatching {
            map.queryRenderedFeatures(bounds, *BUILDING_LAYER_IDS)
        }.getOrElse {
            emptyList()
        }
        val features = if (rendered.isNotEmpty()) rendered else {
            // Fallback for customized style layer IDs: query the rendered viewport
            // and filter features by building-specific attributes and polygon geometry.
            runCatching { map.queryRenderedFeatures(bounds) }.getOrDefault(emptyList())
        }

        val buildings = ArrayList<OsmBuilding>()
        val dedupe = HashSet<String>()
        for (feature in features) {
            if (buildings.size >= 20_000) break
            val raw = runCatching { feature.toJson() }.getOrNull() ?: continue
            for (building in parseFeature(raw, boundary)) {
                val centerLon = building.polygon.map { it.longitude }.average()
                val centerLat = building.polygon.map { it.latitude }.average()
                val key = String.format(
                    java.util.Locale.US,
                    "%.5f,%.5f,%.1f",
                    centerLon,
                    centerLat,
                    building.heightMeters
                )
                if (dedupe.add(key)) buildings.add(building)
                if (buildings.size >= 20_000) break
            }
        }
        return RenderedBuildingReadResult(buildings, features.size, BUILDING_LAYER_IDS.toList())
    }

    internal fun parseFeature(
        rawFeatureJson: String,
        boundary: List<GeoCoordinate>
    ): List<OsmBuilding> {
        val feature = runCatching { JSONObject(rawFeatureJson) }.getOrNull() ?: return emptyList()
        val properties = feature.optJSONObject("properties") ?: JSONObject()
        val geometry = feature.optJSONObject("geometry") ?: return emptyList()
        val geometryType = geometry.optString("type")
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()

        // Only polygonal features carrying OpenMapTiles building attributes are accepted.
        val isBuilding = listOf(
            "render_height", "render_min_height", "building", "building:part",
            "building:levels", "height", "min_height"
        ).any { properties.has(it) && !properties.isNull(it) }
        if (!isBuilding) return emptyList()

        val height = numberProperty(properties, "render_height")
            ?: numberProperty(properties, "height")
            ?: numberProperty(properties, "building:height")
            ?: numberProperty(properties, "building:levels")?.times(3.2)
            ?: 8.0
        if (!height.isFinite() || height !in 1.5..500.0) return emptyList()

        val minHeight = (
            numberProperty(properties, "render_min_height")
                ?: numberProperty(properties, "min_height")
                ?: numberProperty(properties, "building:min_level")?.times(3.2)
                ?: 0.0
            ).coerceIn(0.0, max(0.0, height - 1.0))

        val name = properties.optString("name").takeIf { it.isNotBlank() && it != "null" }
        val polygons: List<JSONArray> = when (geometryType) {
            "Polygon" -> listOf(coordinates)
            "MultiPolygon" -> (0 until coordinates.length()).mapNotNull { coordinates.optJSONArray(it) }
            else -> return emptyList()
        }

        val output = ArrayList<OsmBuilding>()
        for (polygonCoordinates in polygons) {
            val outerRing = polygonCoordinates.optJSONArray(0) ?: continue
            val polygon = (0 until outerRing.length()).mapNotNull { index ->
                val pair = outerRing.optJSONArray(index) ?: return@mapNotNull null
                if (pair.length() < 2) return@mapNotNull null
                val lon = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (!lon.isFinite() || !lat.isFinite() ||
                    lon !in -180.0..180.0 || lat !in -90.0..90.0
                ) null else GeoCoordinate(lat, lon)
            }
            if (polygon.size < 3) continue
            val openRing = if (polygon.first() == polygon.last()) polygon.dropLast(1) else polygon
            if (openRing.size < 3) continue
            val centroid = GeoCoordinate(
                openRing.map { it.latitude }.average(),
                openRing.map { it.longitude }.average()
            )
            val intersectsSelection = pointInPolygon(centroid, boundary) ||
                openRing.any { pointInPolygon(it, boundary) } ||
                boundary.any { pointInPolygon(it, openRing) }
            if (!intersectsSelection) continue

            output.add(
                OsmBuilding(
                    polygon = openRing,
                    heightMeters = height.coerceAtLeast(3.0),
                    minHeightMeters = minHeight,
                    name = name,
                    source = SOURCE_NAME
                )
            )
        }
        return output
    }

    private fun numberProperty(properties: JSONObject, key: String): Double? {
        if (!properties.has(key) || properties.isNull(key)) return null
        val raw = properties.opt(key)
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
                .find(raw.replace(',', '.'))?.value?.toDoubleOrNull()
            else -> null
        }
        return value?.takeIf { it.isFinite() }
    }

    private fun pointInPolygon(point: GeoCoordinate, polygon: List<GeoCoordinate>): Boolean {
        if (polygon.size < 3) return false
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
}
