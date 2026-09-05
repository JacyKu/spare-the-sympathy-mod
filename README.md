# Spare the Sympathy

A client-side Fabric mod for Minecraft 1.20.4 that dumps Monumenta API items with their correct in-game textures to a spritesheet for [sts.deepa.cat](https://sts.deepa.cat).

## Purpose

Monumenta applies item textures at runtime via CIT (Custom Item Textures) packs. This mod renders every item from the [Monumenta items API](https://api.playmonumenta.com/items) using the same CIT, Entity Texture Features, and Entity Model Features logic the game uses, so the spritesheet shows items exactly as they appear in-game.

## Requirements

- Minecraft 1.20.4
- Fabric Loader 0.18.6+
- Java 17+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [CIT Resewn](https://modrinth.com/mod/cit-resewn) 1.1.4-1.1.5
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) 7.0.8
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.0.10

## Building

```bash
./gradlew build
```

The built jar is at `build/libs/sparethesympathy-0.1.0.jar`.

## Running

```bash
./gradlew runClient
```

## Dumping items

In-game, run:

```
/sts dump
```

The command fetches all items from the Monumenta API, renders each with its in-game textures, and writes a spritesheet and its metadata JSON. The output paths are printed in chat when the dump finishes.

## Animated and oversized items

The dump captures every frame of animated item textures into horizontal strips on a dedicated spritesheet (`sts-itemsheet-anim.png`, separate from the static sheet). The manifest records the per-frame dwell times (in ticks, 50 ms each), a per-entry `sheet` field, and frame 0 is also used as the static icon. Items whose icon extends beyond the 16-unit icon box (e.g. oversized models such as the Plate of Nessi) are re-captured at a wide projection and cropped to their content bounds, with the resulting cell size recorded in the manifest (`w`/`h`).
