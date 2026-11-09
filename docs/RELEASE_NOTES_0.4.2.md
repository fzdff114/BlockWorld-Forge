# Atlas 0.4.2 — More visible terrain relief and cached map capture

## Terrain capture
- Add fast 30 m and detailed 20 m sampling presets.
- Reuse downloaded Terrarium DEM tiles from a persistent app-private cache (up to approximately 384 MiB).
- Keep up to eight DEM tile downloads in flight while sampling and recycling decoded bitmaps.
- Restrict automatic DEM zoom fallback to z14–z12. If the selected area cannot fit at a useful zoom, stop with a clear instruction to reduce the area instead of silently producing overly coarse terrain.

## Buildings
- Cache successful OpenStreetMap / Overpass results for seven days.
- Include `building:part` ways and relations as well as building outlines.
- Deduplicate identical footprints.
- Explicitly warn when no OSM building polygons are returned; this is not proof that the real area has no buildings.

## Terrain relief
- Add project-level vertical terrain scale presets: 1×, 1.5×, 2×, and 3× (default 2×).
- Use the same vertical scale when rasterizing terrain, building heights, spawn height, and the terrain foundation.
- Keep headroom for high elevations within Bedrock's build-height range.

## Validation
- Add regression tests for vertical exaggeration, height range limits, and DEM zoom fallback.
- Keep the Bedrock Infinite world generator and LevelDB chunk export path.
- Keep the existing chunk format checks and world database close/reopen verification.

## Data limitations
- 20 m / 30 m are application sampling spacings, not guarantees of native DEM accuracy. Source resolution and quality vary by region.
- Vertical exaggeration changes Minecraft's vertical display scale; it cannot invent elevation detail absent from the source data.
- Building coverage depends on OpenStreetMap. Unmapped buildings will not be generated automatically.
- Roads as block surfaces, hydrography, vegetation / biome distribution, caves, and ruins are not included in this release.
- Actual import appearance should still be confirmed in Minecraft Bedrock on the target device.

