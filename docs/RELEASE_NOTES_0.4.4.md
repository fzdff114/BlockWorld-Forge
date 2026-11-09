# BlockWorld-Forge 0.4.4 — Read rendered basemap buildings during capture

## Map capture
- Query MapLibre rendered vector features in the selected map viewport from OpenFreeMap Liberty's `building` and `building-3d` style layers.
- Extract Polygon / MultiPolygon outlines and per-feature `render_height` / `render_min_height` properties; fall back to `height` and `building:levels` when needed.
- Clip/filter candidates to the selected polygon and deduplicate repeated rendered features.
- Merge source priority: imported China building-height data, rendered basemap vector buildings, then Overpass OSM fallback.
- Show separate counts for basemap, imported China and OSM buildings after capture. If no building features were present in the visible rendered area, explain that the selected area must be visible at building zoom before capture.

## Test environment
- Add a real org.json implementation to the JVM test classpath so rendered-feature GeoJSON parsing tests run outside Android without the Android stub throwing `Method not mocked`.

## Validation
- Add tests for rendered basemap feature parsing, vector-tile height properties, multipolygon geometry, floor-count fallback and non-building rejection.
- Keep the previously fixed local terrain datum and 1× physical vertical scale behavior.
- Preserve source attribution in the exported .mcworld.

## Limitation
- Rendered feature queries use the map's last rendered frame. The selected area must be visible in the current MapLibre viewport and its building tiles must have loaded. This is not a nationwide building dataset download.
- OpenFreeMap/OpenMapTiles building heights are estimated from underlying mapped height or floor tags; unmapped buildings cannot be reconstructed by the basemap query.
