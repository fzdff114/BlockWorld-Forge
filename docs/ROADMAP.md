# BlockWorld-Forge Roadmap

## 0.5.0 — Procedural world generator
- [x] Replace map-first workflow with a local seeded world generator
- [x] Multi-scale continent, ridge, plateau and relief controls
- [x] 29 Bedrock biome definitions and 3-D biome palettes
- [x] Procedural cave fields and depth-aware ore / deepslate ore variants
- [x] Multiple tree species and canopy profiles
- [x] Eight original vanilla-style structure families
- [x] World-size and detail presets with estimated .mcworld size
- [x] Chunked LevelDB writing and close/reopen verification
- [ ] Verify import and visual appearance on target Android Bedrock devices

## 0.6 — Biome detail and structure quality
- [ ] Per-biome surface layers, undergrowth, flowers and ground cover
- [ ] Better cave branching, aquifers and large cave chambers
- [ ] More detailed structure interiors, doors, stairs, windows and paths
- [ ] Structure spacing rules and biome-aware placement
- [ ] Preview structure positions and biome map before export

## 0.7 — World data management
- [ ] Incremental regeneration and region rebuilds
- [ ] Import an existing Bedrock world and regenerate a selected region
- [ ] Preset library for seeds and generation profiles
- [ ] Optional external resource-pack companion for decorative custom blocks

The map scraping workflow has been removed from the primary UI. The generator produces a finite, pre-generated square world, with mobile-safe dimensions up to 512 × 512 blocks in this release.
