# BlockWorld-Forge 0.6.0 — Larger Worlds & Tree Fixes

## World size
- Raise the default procedural world size from 256×256 to 768×768 blocks.
- Raise the selectable maximum from 512×512 to 1024×1024 blocks.
- Keep the existing chunk-count and edge safety limits in the exporter; large worlds take longer to generate and occupy more storage.

## Tree species and geometry
- Correct Pale Garden trees to use pale oak logs and pale oak leaves.
- Use mushroom stems and red/brown mushroom cap blocks for mushroom-field giant mushrooms.
- Add horizontal pillar-axis states for branches and muddy mangrove roots.
- Add fuller layered crowns and distinct profiles for birch, spruce, giant spruce, jungle, acacia, dark oak, mangrove, cherry, pale oak and mushroom trees.
- Give crowns a rounded, species-specific outline and preserve branch and canopy connections across chunk boundaries.
- Keep 768 blocks as the fallback default for newly loaded configurations and use the same mushroom cap taper in every generated tree.
- Use 2×2 trunks for large dark oak and giant spruce specimens; add bent acacia trunks and extended mangrove roots.

## Bedrock palette state handling
- Resolve branch/root axis variants to canonical Bedrock block identifiers.
- Encode persistent leaves with `persistent_bit` and `update_bit`.
- Encode mushroom stem/cap texture states using `huge_mushroom_bits`.

## Regression coverage
- Default and maximum world-size normalization.
- Pale oak log/leaf identifiers.
- Horizontal log/root axis state serialization.
- Mushroom stem and cap palette states.
- World-size estimate scaling.

## Validation note
- JVM unit tests and Android APK build are run by GitHub Actions.
- Import and visual appearance still need confirmation on the target phone and Minecraft Bedrock version.