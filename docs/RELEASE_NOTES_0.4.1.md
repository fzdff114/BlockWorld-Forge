# Atlas 0.4.1 — Faster collection and easier area selection

## Faster collection
- Use a 30 m terrain sample grid, closer to the native resolution of the global SRTM-based source, instead of oversampling it at 20 m.
- Cap the requested Terrarium zoom at z14 for the balanced 30 m grid.
- Download up to eight elevation tiles concurrently and recycle each decoded image immediately after sampling.
- Run OpenStreetMap building queries concurrently with DEM downloads.
- Shorten Overpass query and network timeouts; show live tile-download progress and useful error messages.

## Easier map selection
- Default to two-corner rectangle selection: tap two opposite corners to make a rectangular area.
- Keep polygon mode for irregular areas.
- Show the first rectangle corner on the map; Undo and Clear reset the pending selection correctly.
- Search for a place name (for example, Shenzhen) or enter latitude/longitude coordinates.

## World type and terrain
- Keep `Generator=1` (Bedrock Infinite), not `Generator=2` (Flat).
- Add regression tests confirming that the exported `level.dat` is not marked as Flat and that different elevation samples produce different surface heights.
- Existing project exports remain elevation-driven; this update does not switch to superflat generation.

## Notes
- 30 m is the sampling spacing, not a guarantee that each region has 30 m native elevation accuracy. Actual DEM data varies by region.
- OSM building coverage and Overpass service speed vary by area.
- Previously saved projects keep their old elevation grid; recapture the area to benefit from the faster 0.4.1 pipeline.
- Import and visual appearance still require testing in Minecraft Android.

