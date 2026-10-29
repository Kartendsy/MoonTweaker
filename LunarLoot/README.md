# LunarLoot

Loot table scripting for **Minecraft 1.12.2 / Forge**, built as an extension of
[MoonTweaker](../README.md).

LunarLoot adds a `loot` API to MoonTweaker's Lua runtime so you can rewrite
dungeon chests, Nether fortress chests, village chests and every other loot
table without writing Java.

- **Mod ID:** `lunarloot`
- **Requires:** MoonTweaker, Forgelin-Continuous, Forge 14.23.5.2847+
- **Author:** Kartendsy
- **License:** GPL-3.0-or-later (see [LICENSE](LICENSE) and [NOTICE](NOTICE))

## Installation

1. Install Forge 1.12.2.
2. Put `Forgelin-Continuous-<version>.jar` in `mods/`.
3. Put `MoonTweaker-<version>.jar` in `mods/`.
4. Put `LunarLoot-<version>.jar` in `mods/`.
5. Put scripts in `lua_scripts/server/`.

LunarLoot is server-side. Clients do not need it.

## Building

```bash
./gradlew build
```

Output: `build/libs/LunarLoot-0.1.0.jar`.

The project compiles against `libs/MoonTweaker-0.1.0-dev.jar`; replace that file
when the MoonTweaker API changes. Gradle runs on JDK 25 and provisions a Java 8
toolchain automatically.

## API

All functions are available as the global `loot` table inside
`lua_scripts/server/` scripts. Table names use the vanilla resource location,
for example:

- `minecraft:chests/simple_dungeon`
- `minecraft:chests/nether_bridge`
- `minecraft:chests/village_blacksmith`
- `minecraft:chests/abandoned_mineshaft`
- `minecraft:entities/zombie`

### `loot.add(table, spec)`

Adds a weighted entry to a new pool, so the table rolls it once.

```lua
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 2,
  max = 4,
  weight = 10,
  quality = 0,
  meta = 0,
})
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `item` | string | required | Item ID, with optional metadata (`minecraft:wool:14`) |
| `min` | integer | `1` | Minimum stack size |
| `max` | integer | `min` | Maximum stack size |
| `weight` | integer | `1` | Selection weight inside the pool |
| `quality` | integer | `0` | Quality bonus scaled by luck |
| `meta` | integer | `0` | Item metadata; also read from the ID suffix |

A plain string is accepted as shorthand for a single item:

```lua
loot.add("minecraft:chests/stronghold_library", "minecraft:book")
```

### `loot.remove(table, item)`

Removes every direct item entry that drops the given item.

```lua
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")
```

Metadata is ignored: `minecraft:wool:14` removes all wool entries.

### `loot.clear(table)`

Removes every pool from the table. Combine with `loot.add` to fully replace the
contents.

```lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 1,
  max = 3,
  weight = 1,
})
```

### Full example

```lua
-- lua_scripts/server/loot.lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 2,
  max = 4,
  weight = 50,
})
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")
log.info("LunarLoot example loaded")
```

## When changes apply

LunarLoot applies queued operations inside Forge's `LootTableLoadEvent`, before
the table is frozen. The server log then shows one line per changed table:

```
LunarLoot: applied 2 changes to minecraft:chests/simple_dungeon
```

Operations are queued when `lua_scripts/server/` loads. After editing scripts:

1. `/moontweaker reload` queues the new operations.
2. `/reload` reloads loot tables and applies them.
3. A server restart also applies them.

Scripts in `lua_scripts/startup/` can call `loot`, but their operations are
discarded when the server stage loads.

## Limitations

- Only direct item entries are removed; nested table references and custom
  loot entries are left untouched.
- `loot.remove` matches by item, not by metadata or entry name.
- Conditions and functions are not exposed to Lua.
- Table changes are server-side only; client mods that cache loot tables are
  not synchronized.
