# Atlas 0.4.0 — Vector basemap, detailed terrain, and buildings

## Map
- Replace the OpenTopoMap raster basemap with OpenFreeMap Liberty vector style.
- Add a 3D / flat camera toggle. Coordinate search zooms to city-building detail and tilts the camera for 3D building extrusion.
- No map API key or Google Play Services required.

## Terrain collection
- Replace the 20 × 20 Open-Meteo elevation point grid with Mapzen Terrarium DEM raster tiles hosted on AWS Open Data.
- Decode the Terrarium RGB elevation encoding and sample a variable-resolution grid at approximately 20 m spacing, capped at 384 × 384 samples.
- Select the highest DEM tile zoom (up to z15) that keeps the requested area within 64 tiles, and download tiles in batches of at most four concurrent requests.

## Buildings
- Query OpenStreetMap building ways and relations through Overpass.
- Store the building polygons, mapped heights and minimum heights in each saved project.
- During Bedrock export, rasterize the footprints into brick walls and stone-brick roofs, with building heights scaled into world blocks.
- Show the collected building count and any Overpass service error in the map screen.

## Important limitations
- DEM grid spacing is not the same as guaranteed source accuracy or native DEM resolution. The source combines SRTM and other open elevation datasets; detail varies by region.
- OSM building coverage is uneven. Buildings not mapped in OSM will not appear. Missing height metadata uses an estimated 8 m height.
- Old saved projects contain the old coarse elevation grid and no building footprints. Re-select the same area in 0.4.0, collect terrain + buildings again, and save a new project.
- OpenFreeMap is a community service without an SLA. Overpass is a shared public query service and may be temporarily busy.
- Actual Minecraft Android import and appearance still need device-side verification.

## Attribution
- OpenFreeMap © OpenMapTiles; data © OpenStreetMap contributors (ODbL).
- Mapzen Terrain Tiles © Mapzen and their upstream open-data sources.
