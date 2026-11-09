# BlockWorld Forge

A responsive workspace for creating large Minecraft Bedrock worlds from real-world map data and procedural terrain.

> Current stage: UI foundation / interactive frontend prototype. Map-data ingestion, terrain generation, and Bedrock export are planned modules and are not implemented yet.

## Run locally

This first UI prototype is a static site and needs no build tool:

1. Open `index.html` in a modern browser, or
2. Serve the repository root with any static HTTP server.

## Interface scope

- Project dashboard and recent world projects
- Map data import entry point (local file selection is UI-only in this stage)
- World size, target edition, coordinate and terrain settings
- Generation queue preview and progress-state demo
- Responsive layout for desktop and mobile

## Planned architecture

- `web/`: UI prototype
- `docs/`: product notes and implementation roadmap
- `engine/`: future map-data and world-generation core
- `exporters/`: future Minecraft Bedrock world exporter

## Visual direction

White canvas, blue primary actions, pale-blue highlights, cool-gray surfaces, restrained borders and clear typography.
