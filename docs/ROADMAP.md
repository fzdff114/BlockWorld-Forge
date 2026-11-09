# BlockWorld Forge Roadmap

## 0.1 Native Android foundation
- [x] Kotlin + Jetpack Compose (no WebView or HTML UI)
- [x] White-and-blue mobile interface
- [x] Dashboard and in-memory project creation
- [x] Android document picker entry point
- [x] World size and terrain configuration UI
- [x] Asset library and Bedrock export screens
- [x] GitHub Actions debug APK build

## 0.2 Project model
- [ ] Persist project metadata and settings locally
- [ ] Rename, duplicate, archive and delete projects
- [ ] Restore state after app restart

## 0.3 Map data adapters
- [ ] Region search and coordinate selection
- [ ] OpenStreetMap import
- [ ] Authorized map-provider adapter interface
- [ ] Elevation and land-cover data adapters
- [ ] Data-source attribution and licensing metadata

## 0.4 World generation core
- [ ] Coordinate projection and region tiling
- [ ] Heightfield conversion and terrain shaping
- [ ] Rivers, lakes, coastlines and biome distribution
- [ ] Structure and exploration-point placement
- [ ] Chunk generation and incremental rebuilds

## 0.5 Bedrock exporter
- [ ] Bedrock world database writer
- [ ] World metadata and spawn configuration
- [ ] Export validation and .mcworld packaging
- [ ] Android Minecraft import smoke tests

## Engineering rules
- Keep UI and generation engine separate.
- Do not label planned functionality as complete.
- Verify data-source licensing before provider integration.
- Validate generated output before claiming export success.
