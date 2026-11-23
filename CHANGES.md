# Fork changes and provenance

## 1.0.93-mc1.12.2

- Standardize the Personal Dimension naming in English and Chinese.
- Expose all 12 global settings in the Forge Mod Config screen, with bilingual comments.
- Keeps the 1.0.92 change that avoids a full client chunk renderer reload on configuration sync.

## 1.0.64-mc1.12.2

- World preview in the portal GUI is now computed on a background thread: texture PNG reading, color averaging and the 128x128 pixel buffer no longer stall the render thread when settings change. GPU atlas readback (the rare fallback) and texture upload stay on the render thread.
- Personal dimension config files (`personalspace_metadata.cfg`) are now written on a background thread. Autosaves, world unloads and settings updates no longer block the server thread with file I/O; writes stay strictly ordered per file and are flushed before the server finishes stopping.
- See `docs/` for per-feature implementation notes and `项目总文档.md` for the project overview.

## 1.0.63-mc1.12.2

- Add a per-dimension road area size input below the road/solid toggle (1-20 chunks, square area enclosed by roads).
- Road area size is stored in the dimension config, synced over the network, included in the preset string (old preset strings keep the current value), and reflected in the preview.
- Dimensions saved by older versions keep the global `roadAreaChunks` config value as their default.

## 1.0.62-mc1.12.2

- Suppress chunk borders directly adjacent to roads.
- Portal drops and pick-block no longer retain dimension bindings; old bound items no longer relink. Administrator give-portal remains an explicit recovery path.
- Protect portals inside personal dimensions from player and entity destruction.
- Add one movable teleport point per personal dimension, reusing portal textures. Portal entry and coordinate-free tpx prefer its upper surface; right-click returns to the last entrance or overworld spawn.
- Persist teleport point coordinates separately from editable generation presets.
- Claim gameplay remains reserved and is not enabled.


## 1.0.54-mc1.12.2 source snapshot

This is a modified Minecraft 1.12.2 port of GTNewHorizons/PersonalSpace (upstream targets Minecraft 1.7.10).

The local development history is not available as Git commits. This document is a high-level provenance notice, not a complete per-version changelog.

The exported source includes Forge/Minecraft 1.12.2 API migration, world configuration and GUI modifications, and fixes for FlatLayerInfo/IBlockState usage, block metadata calculation and preview map-color API calls.

Public-source packaging changes only:

- Retained the original LGPL-3.0 license and added a root license copy and GPL-3.0 text.
- Added upstream attribution and build instructions.
- Added English and Chinese AI tool disclosures for this fork's development and source preparation, without attributing AI usage to upstream or claiming complete manual review/testing.
- Replaced the machine-specific dependency directory with `libs/` in the exported build script.
- Added Gradle 4.9 wrapper configuration and Git ignore rules.
- Excluded build output, caches, historical backups and third-party dependency JARs.

Game code and mod version were not changed during this packaging step. The precise upstream base commit and individual modification dates remain to be verified before claiming a complete change history.
