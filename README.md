# Tides

`tides` is now a renderer companion project, not a client-side water hack.

The old approach tried to mutate `SceneTilePaint` and `SceneTileModel` directly from a normal RuneLite plugin. That works for experiments, but it does not produce reliable textured moving water across paint-backed tiles. The project has been reset around the parts that *do* carry forward into a 117HD/RSHD renderer implementation:

- water tile detection and classification
- immutable per-scene water snapshots
- shoreline detection
- deterministic wave sampling that a renderer can apply to uploaded vertices
- a lightweight debug overlay for inspection

## Current Scope

The plugin now scans the active scene and builds water metadata only. It does **not** attempt to replace tile paints, generate scene models, or render fake textured surfaces.

Core classes:

- `TidesPlugin`: scene scanning, snapshot lifecycle, public accessors
- `TidesSceneSnapshot`: immutable water-tile snapshot for the current world view
- `TidesWaterTile`: per-tile renderer metadata
- `TidesWaterType`: RLHD-style water classification from vanilla texture ids
- `TidesWaveSampler`: deterministic height function for future renderer displacement

## Intended 117HD / RSHD Integration

The clean path is to apply `TidesWaveSampler` in the renderer upload/shader path, not in the vanilla client tile objects.

That means a future renderer-side patch should consume `tides` water metadata during scene upload, then displace the uploaded water vertices before final shading. In RLHD terms, the likely integration points are the tile upload path and/or the vertex shader inputs used for water surfaces.

## Running

```powershell
.\gradlew.bat runClient
```

Enable the `Debug overlay` config option if you want to inspect detected water tiles and shoreline edges in-game.
