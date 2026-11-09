# Atlas 0.3.1 — Large chunked world export

## Changes
- Removed the 512 × 512 block export rejection. Maps up to 8,192 blocks per edge and 100,000 Minecraft chunks are accepted.
- Generate one continuous Bedrock world in 8 × 8 chunk batches (128 × 128 blocks per batch), flushing each batch to LevelDB and updating progress.
- Use a terrain-relative foundation below the lowest sampled elevation rather than filling every column from Y=-64, reducing unnecessary underground block data.
- Stream the completed LevelDB folder directly into the selected .mcworld document. This avoids creating a second full-size ZIP in the app cache.
- Use fast ZIP compression for already-compressed LevelDB files.
- Add regression coverage for the reported 5,044 × 4,759 map size and the chunk safety limit.

## Notes
- The reported 5,044 × 4,759 map is approximately 94,168 Minecraft chunks and now fits the software limit. A map this large can still take substantial time and storage; keep enough free space available.
- Height values still come from the project's existing 20 × 20 elevation sample grid. Chunking changes export memory/storage behavior; it does not increase source elevation resolution.
- Import and visual appearance in Minecraft Android still require device-side verification.
