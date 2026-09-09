# Dynasty

Dynasty is a voxel survival game inspired by Minecraft. It is a first-person sandbox: walk a block world, look around, jump, and reshape the terrain.

This repo is a **Pre-Alpha Prototype**. The survival loop (hunger, tools, crafting, danger) and other mechanics are not implemented yet. What you have is a playable slice that has movement, physics, chunks, and block editing.

## Current prototype

- Desktop (LWJGL3) window at 1280×720
- Four 16×16 chunks around the origin (32×32 blocks on X/Z)
- Flat terrain: 30 stone, 4 dirt, 1 grass on top, plus 20 blocks of air to build in
- Grass, dirt, and stone textures (nearest-neighbor, 16×16)
- First-person move, look, gravity, jump, and solid-block collision
- Break and place blocks (reach 6), with a face outline on the targeted block
- Three-slot hotbar: grass, dirt, stone
- Debug overlay (FPS, position, controls)
- Falling below the world respawns you at the start

## Controls

| Input | Action |
| --- | --- |
| W A S D | Move |
| Mouse | Look |
| Space | Jump |
| Left click | Break the targeted block |
| Right click | Place the selected block against that face |
| 1 / 2 / 3 or mouse wheel | Select hotbar slot |
| F5 | Toggle debug HUD |
| Esc | Free or lock the mouse, also click the window to lock it again |

## How to run

Players do not need to compile anything. Use a release build.

1. Install **Java 21** or newer ([Eclipse Temurin](https://adoptium.net/) works well). Confirm with `java -version`.
2. Open the [Releases](https://github.com/maxieruff/DynastyGame/releases) page and download `Dynasty-1.0.0.jar` from the latest release.
3. Run it by double clicking on it
