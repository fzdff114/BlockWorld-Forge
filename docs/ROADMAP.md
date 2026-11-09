# BlockWorld Forge — Roadmap

## 0.1 UI foundation
- [x] Responsive white-and-blue workspace
- [x] Dashboard, sample project list and quick-create interaction
- [x] Map data import entry point and local file picker
- [x] World configuration preview and terrain-style selector
- [x] Asset library and Bedrock export screens
- [x] Clear labels separating UI preview from implemented engine features

## 0.2 Project model
- [ ] Project schema and local persistence
- [ ] Create, rename, duplicate and delete projects
- [ ] Save settings between sessions

## 0.3 Map data adapters
- [ ] Region search and coordinate selection
- [ ] OpenStreetMap import pipeline
- [ ] Authorized provider adapter interface
- [ ] Elevation and land-cover adapters
- [ ] Licensing and attribution metadata

## 0.4 World generation core
- [ ] Coordinate projection and region tiling
- [ ] Terrain heightfield conversion
- [ ] Rivers, lakes, coastlines and biome distribution
- [ ] Structures and exploration-point placement
- [ ] Chunk generation and incremental rebuilds

## 0.5 Bedrock exporter
- [ ] Bedrock world database writer
- [ ] World metadata and spawn configuration
- [ ] Export validation and .mcworld packaging
- [ ] Android import smoke tests

## Engineering rules
- Keep UI state separate from generation-engine code.
- Do not label planned functionality as complete.
- Record data-source licensing and attribution requirements before integration.
- Validate generated output before calling an export successful.
