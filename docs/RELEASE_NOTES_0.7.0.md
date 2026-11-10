# BlockWorld-Forge 0.7.0 — Custom World Size & Geological Layers

## Custom world size
- Add a numeric custom side-length field from 128 to 2048 blocks.
- Raise the default procedural world from 768×768 to 1536×1536.
- Retain the 100,000-chunk mobile export safety budget; a 2048×2048 square world is 16,384 chunks.
- Show estimated compressed archive size and report actual output bytes and written chunk count after export.

## Richer underground geology
- Replace uniform stone/deepslate-only underground fill with seeded geological fields.
- Generate granite, diorite, andesite, tuff, calcite and deepslate formations using the selected detail preset.
- Keep cave carving and ore veins layered on top of the geological base.

## Download and file size notes
- The `.mcworld` archive is generated locally and saved through Android's document picker; it is not fetched from a server.
- ZIP/LevelDB compression can make a large, valid world archive appear small when many chunk palettes repeat.
- The estimate floor is kept below the minimum preset output so compact and standard estimates remain distinguishable.
- Do not add artificial padding; actual size depends on real terrain and feature content.

## Regression tests
- Default and custom world size bounds.
- Deterministic geological block selection.
- Export estimate scaling against the expanded world size.
- Existing chunk and Bedrock format checks remain enabled.

## Validation note
- GitHub Actions runs JVM tests and Android APK compilation before publishing the release.
- World import and visual appearance still require testing on a real target phone and Bedrock version.
## APK download fallback
- If the direct GitHub Release download is slow, the release can also be fetched through GHFast or GH-Proxy public proxy endpoints.
- These are third-party relays; verify the downloaded APK against the published SHA-256 checksum.
