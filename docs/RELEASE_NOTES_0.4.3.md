# BlockWorld-Forge 0.4.3 — China building-height import and real terrain scale

## Terrain scale
- Rebase terrain Y coordinates on the minimum elevation of the selected region, not the absolute elevation above sea level.
- Preserve the physical horizontal/vertical meters-per-block ratio for regions whose local relief fits Bedrock's build-height range.
- Compress only when the selected region's own elevation range would exceed the available build height.
- Calculate the sea-water plane from actual 0 m elevation relative to the local datum.
- Default new projects to vertical scale 1× and report the actual resulting vertical meters per block.

## China building data
- Add a Shapefile ZIP importer for building footprints and per-building height attributes.
- The importer supports Polygon / PolygonZ / PolygonM shapes; WGS84 / CGCS2000 geographic coordinates and EPSG:3857 Web Mercator; DBF fields such as height, height_m, building_height and hgt.
- Filter imported features to the current map-selection bounding box, prefer imported China footprints to OSM fallback polygons, and deduplicate approximate matching centroids.
- Preserve per-feature data source in project metadata and write 3D-GloBFP CC BY 4.0 attribution into the generated world.
- Limit the imported features to 20,000 per project and ZIP expansion to 1.5 GB for mobile-device safety.

## Data sources and limitations
- Recommended individual-building data: 3D-GloBFP (2020), organized by spatial tiles and China provinces; estimated building heights are not live survey measurements. The dataset reports R² 0.66–0.96 and RMSE 1.9–14.6 m across 33 validation subregions.
- China Urban Building Footprint Height Dataset (2018) for Beijing, Changsha, Chengdu, Hangzhou, Nanjing, Shanghai, Shenyang, Tianjin, Wuhan and Xi'an is linked as an alternate source. Its data portal terms must be checked before reuse; the app does not redistribute it.
- Existing OSM buildings remain a fallback only. Areas without mapped or imported footprints will still be missing buildings.
- Terrain DEM sampling spacing (20 m / 30 m) is not a guarantee of local source-data accuracy.
- Actual Minecraft Bedrock device import and visual appearance still require device-side verification.

## Build correction
- Fix the Kotlin import for polygon-ring area calculation in the Shapefile importer before the 0.4.3 release build.

## Regression coverage
- Verify local-elevation datum on high-altitude plateaus.
- Verify vertical exaggeration behavior, water level, Web Mercator conversion, and numeric building-height parsing.
