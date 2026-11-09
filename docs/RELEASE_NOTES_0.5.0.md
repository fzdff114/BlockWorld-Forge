# BlockWorld-Forge 0.5.0 — Procedural World & Structure Generator

## Complete product direction change
- Replace the map/elevation/building capture workflow in the main navigation with a local procedural world generator.
- Keep only three primary sections: Generator, Projects and Export.
- Save generator configuration and seed in the project; export directly to a Bedrock .mcworld file without network map access.

## Generator capabilities
- Seeded multi-scale terrain with editable relief, mountain ruggedness, plateau strength and water level.
- 29 Bedrock biome definitions with biome selection count control.
- 3-D noise caves and depth-aware ore distribution, including deepslate ore variants.
- Nine tree species profiles with varied canopy shapes.
- Eight original vanilla-style structure families: village cluster, desert temple, jungle temple, ruined portal, mineshaft, stronghold, ocean ruin and mountain watchtower.
- World edge presets from 128 to 512 blocks.
- Compact / Standard / High detail modes with a compressed-world size estimate.

## Bedrock data format
- Generate 3-D biome palette records in Data3D for the 24 vertical subchunk sections.
- Pack biome palette indices in Bedrock XZY order (Y changes fastest).
- Store heightmap values with the Bedrock world-height offset.
- Use modern Bedrock log and leaf identifiers/states; add deepslate ore variants.
- Hollow out underground mineshaft and stronghold areas before adding supports and walls.
- Keep chunk writes batched and reopen the generated LevelDB to verify a sample chunk.

## Known limits
- The 8 structures are newly constructed vanilla-style designs, not exact Mojang structure templates.
- This is a finite pre-generated square world, not infinite runtime terrain generation; maximum side length is 512 blocks for mobile stability.
- Biome selection is procedural and does not fully duplicate the vanilla Bedrock biome climate algorithm.
- No mobs, NPCs, loot contents or redstone logic are generated yet.
- Archive size is an estimate; actual compression varies by generated content.
- Android Bedrock device-side import and appearance still require real-device verification.

## Regression coverage
- Settings/seed JSON round-trip, deterministic terrain and biome selection.
- Cave/ore generation toggles and seeded features.
- 3-D biome palette encoding, XZY indexing and heightmap offset.
- Modern tree block states and deepslate ore identifiers.
