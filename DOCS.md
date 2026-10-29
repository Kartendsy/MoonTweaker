# MoonTweaker — Complete Documentation

Technical reference and API guide for **MoonTweaker 0.1.0**, the all-in-one
server-side Lua scripting mod for **Minecraft 1.12.2 / Forge**.

This document is the full reference. For a short overview and installation
quick-start, see [README.md](README.md).

---

## Table of contents

1. [Overview](#1-overview)
2. [Compatibility](#2-compatibility)
3. [Installation](#3-installation)
4. [Quick start](#4-quick-start)
5. [Script system](#5-script-system)
6. [Data types and argument parsing](#6-data-types-and-argument-parsing)
7. [Global API](#7-global-api)
8. [Recipe API](#8-recipe-api)
9. [Loot API](#9-loot-api)
10. [Registry API](#10-registry-api)
11. [Machine recipe API](#11-machine-recipe-api)
12. [Event API](#12-event-api)
13. [Commands](#13-commands)
14. [Logging and diagnostics](#14-logging-and-diagnostics)
15. [Architecture and internals](#15-architecture-and-internals)
16. [Building and development](#16-building-and-development)
17. [Error reference](#17-error-reference)
18. [Limitations](#18-limitations)
19. [Troubleshooting and FAQ](#19-troubleshooting-and-faq)
20. [License and credits](#20-license-and-credits)

---

## 1. Overview

MoonTweaker loads user-written Lua scripts and exposes a controlled API for
changing Minecraft server behaviour without writing Java or Kotlin:

| Area | Global | What it can do |
|---|---|---|
| Crafting | `recipes` | Add shaped, shapeless and furnace recipes; remove registered crafting recipes |
| Loot | `loot` | Add, remove and clear loot table entries |
| Content | `registry` | Register items, blocks, tools, armor and fluids |
| Machines | `moonexpansion` | Add Thermal Expansion, Ender IO and Immersive Engineering recipes |
| Events | `events` | React to login, block break/place, entity death, item use and server ticks |
| Utilities | `item`, `ore`, `log`, `print`, `require`, `os` | Item helpers, logging, module loading and a restricted clock |

The mod is implemented in **Kotlin**, runs on **Forgelin-Continuous**, and embeds
the **LuaJ 3.0.1** runtime. It has no Mixins, no coremod and does not patch
Minecraft classes. Minecraft content is manipulated through ordinary Forge
registries and events.

### Design goals

- One jar for recipes, loot, content and machine recipes.
- Scripts are plain `.lua` files under `lua_scripts/`.
- Each stage (`registry`, `server`, `startup`) runs at the Forge lifecycle point
  where the operations are legal.
- The Lua environment is sandboxed: no filesystem, no Java reflection from Lua,
  no arbitrary file loading.
- Unsupported operations fail with readable Lua errors instead of crashing the
  game.

### API globals at a glance

```lua
-- Always available
print(msg)                 log.info(msg)  log.warn(msg)  log.error(msg)
item("mod:item:meta")      ore("ingotIron")               require("module.name")

-- Always installed, but stage-appropriate use is required
recipes.*                  loot.*            events.*
moonexpansion.*            registry.*        -- registry only works in registry stage
```

The exact stage rules are in [§5.3](#53-stage-capability-matrix).

---

## 2. Compatibility

| Component | Required version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Forgelin-Continuous | 2.x, required at runtime (`required-after:forgelin`) |
| Java | 8 |
| Lua | 5.2 syntax (LuaJ 3.0.1) |

Optional integration targets:

| Mod | Tested version |
|---|---|
| Thermal Expansion | `1.12.2-5.5.4.43` |
| Ender IO | `1.12.2-5.3.72` |
| Immersive Engineering | `0.12-92-559` |

These three mods are **not** compile-time dependencies. Their recipe managers
are accessed by reflection at runtime, so a different major version may work or
may fail with a logged error.

### Client/server requirements

- Recipe, loot, machine and event scripts are processed on the server.
- Content registered through `registry` becomes part of the game registry, so
  **clients that connect to a server using registered content need the same mod
  and assets**. A client without the mod cannot see the registered items,
  blocks or fluids correctly.
- Pure recipe/loot/event scripts do not require the mod on the client.

---

## 3. Installation

1. Install Minecraft Forge 1.12.2.
2. Put `Forgelin-Continuous-<version>.jar` in `mods/`.
3. Put `MoonTweaker-<version>.jar` in `mods/`.
4. Start the game or server once.
   MoonTweaker creates `lua_scripts/registry/`, `lua_scripts/server/` and
   `lua_scripts/startup/` next to the `config/` directory.
5. Add `.lua` files to the appropriate stage folder.
6. Restart, or use `/moontweaker reload` for server-stage scripts.

Installed layout (single-player or client):

```
.minecraft/
  mods/
    Forgelin-Continuous-<version>.jar
    MoonTweaker-<version>.jar
  config/
  lua_scripts/
    registry/
    server/
    startup/
```

Dedicated server:

```
server/
  mods/
    Forgelin-Continuous-<version>.jar
    MoonTweaker-<version>.jar
  config/
  lua_scripts/
    registry/
    server/
    startup/
```

The script root is the **parent of the Forge config directory**, not the config
directory itself. Under a development run this is `run/lua_scripts/`.

---

## 4. Quick start

Create `lua_scripts/server/example.lua`:

```lua
log.info("hello from MoonTweaker")

-- Remove every recipe that produces a stick
recipes.remove("minecraft:stick")

-- Add a shaped recipe
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)

-- Add a weighted loot entry
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 1,
  max = 3,
  weight = 10,
})

-- React to a block break
events.on("block.break", function(e)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)
```

Restart the server. Recipe changes apply during startup. Loot changes apply
when loot tables load. If you only changed server-stage scripts after the
server is running:

```
/moontweaker reload
/reload
```

Recipe changes cannot be applied after the recipe registry is frozen; see
[§5.8](#58-reloading).

---

## 5. Script system

### 5.1 Directory layout

```
lua_scripts/
  registry/     content registration, runs at pre-initialization
  startup/      startup scripts, runs at post-initialization
  server/       recipe/loot/machine/event scripts, runs during recipe registration
```

Any `.lua` file below those directories is loaded, including nested folders:

```
lua_scripts/
  server/
    main.lua
    config/
      balance.lua
    recipes/
      tools.lua
```

### 5.2 Load order and lifecycle

| Order | Forge event | MoonTweaker action | Stage folder |
|---|---|---|---|
| 1 | `FMLPreInitializationEvent` | Load `registry/`, register Forge handlers | `registry/` |
| 2 | `RegistryEvent.Register<Block>` | Register queued blocks | — |
| 3 | `RegistryEvent.Register<Item>` | Register queued items and ItemBlocks | — |
| 4 | `RegistryEvent.Register<IRecipe>` (LOWEST priority) | Load `server/` | `server/` |
| 5 | `FMLPostInitializationEvent` | Load `startup/` | `startup/` |
| 6 | `FMLLoadCompleteEvent` | Apply queued machine recipes | — |
| 7 | `FMLServerStartingEvent` | Register `/moontweaker` | — |
| 8 | `LootTableLoadEvent` (per table) | Apply queued loot operations | — |
| 9 | Runtime | Dispatch gameplay events | — |

Important consequences:

- `registry/` runs **before** Forge opens its registry events, so it can only
  queue content. Actual registration happens in steps 2 and 3.
- `server/` runs at **LOWEST** priority during recipe registration. Recipes from
  other mods are therefore already present when it runs, so they can be removed.
- `server/` runs **before** `startup/`, even though it is registered later in
  this table. Both can queue loot, machine recipes and events.
- Machine recipes are queued by both stages and applied once at step 6, after
  every mod has finished initialization.
- Loot operations are queued and applied later, when each loot table loads.

### 5.3 Stage capability matrix

| Operation | `registry/` | `server/` | `startup/` |
|---|---|---|---|
| `registry.*` | Yes | Error | Error |
| `recipes.*` | Not supported | Yes | After registry lock; not supported |
| `loot.*` | Queued, then discarded | Yes | Yes |
| `moonexpansion.*` | Error | Queued | Queued |
| `events.on` | Cleared when `server/` loads | Yes | Yes |
| `require` | Yes (relative to `registry/`) | Yes (relative to `server/`) | Yes (relative to `startup/`) |

Only `registry` and `moonexpansion` are explicitly stage-gated in code. The
other rows describe when operations are effective and safe.

- Loot operations queued by `registry/` are discarded because loading `server/`
  calls `LootApi.reset()`.
- Event listeners registered by `registry/` are cleared when `server/` loads.
- Crafting recipes must be registered or removed while the recipe registry is
  open. `startup/` runs after that point, so recipe editing there is too late.

### 5.4 File discovery and ordering

- Only files ending in `.lua` are executed.
- Discovery is recursive (`walkTopDown`).
- Files are sorted lexicographically by their full path.
- Each stage has its own Lua global environment (`Globals`).
- Modules loaded with `require` are cached **per stage load**.
- Loading a stage clears that stage's module cache. It does not share state with
  other stages.
- An error in one file does not stop the remaining files. The file is skipped,
  the error is recorded, and loading continues.

Ordering example:

```
server/a.lua
server/config/b.lua
server/z.lua
```

`server/a.lua` runs before `server/config/b.lua`, which runs before
`server/z.lua`.

### 5.5 Error handling

Every file is executed inside a `try` block:

- `LuaError` and any other `Throwable` are caught.
- The error is appended to the script error list and reported by
  `/moontweaker errors`.
- A partially executed file may already have queued some operations before the
  failing line. For example, an item registered on line 3 stays registered if
  line 5 throws.

The `loadedCount` reported by reload counts only files that completed without an
error.

Script chunk names are relative to the stage folder, so a Lua error includes the
file and line:

```
config/balance.lua:7: bad item id 'foo'
```

### 5.6 `require`

`require(name)` loads a Lua file relative to the **current stage folder**.

- Dots are converted to directory separators: `config.balance` →
  `config/balance.lua`.
- The extension `.lua` is appended.
- The resolved file must stay inside the stage folder; path traversal is
  rejected after canonicalization.
- Modules are cached per stage load. A second `require` returns the same value.
- If the module returns `nil`, `require` returns `true`.
- Errors inside the module propagate to the calling script.

`lua_scripts/server/config/balance.lua`:

```lua
return {
  diamondWeight = 10,
  enableBonusLoot = true,
}
```

`lua_scripts/server/main.lua`:

```lua
local balance = require("config.balance")
log.info("diamond weight = " .. balance.diamondWeight)
```

Rejected:

```lua
require("../registry/content")   -- outside the server stage folder
require("missing.module")        -- module not found
```

Errors:

```
invalid module: <name>
module not found: <name>
```

### 5.7 Sandbox

The Lua environment contains:

| Library | Available |
|---|---|
| Base | Yes, except removed functions below |
| `string` | Yes |
| `table` | Yes |
| `math` | Yes (`JseMathLib`) |
| `bit32` | Yes |
| `coroutine` | Yes |
| `os` | Restricted: `time`, `clock`, `difftime` only |
| `package` | Removed |
| `io` | Removed |
| `luajava` | Removed |
| `debug` | Removed |
| `dofile`, `loadfile`, `load` | Removed |

Custom functions:

- `print(msg)` writes to the mod logger at INFO with the `[lua]` prefix.
- `log.info`, `log.warn`, `log.error` do the same at their levels.
- `require` is MoonTweaker's own loader, not Lua's default module searcher.

`os` details:

| Function | Behaviour |
|---|---|
| `os.time()` | Unix time in seconds as a floating-point number; arguments are ignored |
| `os.clock()` | Process monotonic clock in seconds |
| `os.difftime(a, b)` | Returns `a - b` |

`os.date`, `os.getenv`, `os.execute`, `os.remove`, `os.rename`, `os.exit` and
the rest are **not** available.

There is no instruction budget, watchdog or time limit. A script with an
infinite loop will hang the server thread. Treat scripts as trusted code.

### 5.8 Reloading

Command:

```
/moontweaker reload
```

What reload does:

1. Clears the recorded script errors.
2. Resets the recipe counter.
3. Resets `loadedCount`.
4. Reloads **only** `lua_scripts/server/`.
5. Clears event listeners, loot operations and machine-recipe operations before
   running the server files.
6. Re-creates the Lua globals for the server stage.

What this means in practice:

| Resource | Effect of reload |
|---|---|
| Server script code | Reloaded |
| Lua globals from server stage | Reset |
| `require` module cache | Cleared for the server stage |
| Event listeners | All cleared, including listeners registered by `startup/` |
| Loot operations | Previous operations discarded; server scripts queue new ones |
| Machine recipe operations | Cleared, but machine recipes cannot be re-applied after startup |
| Crafting recipes | Cannot be changed after the registry is frozen |
| Registry content | Cannot be reloaded; restart required |

After reloading loot scripts, run `/reload` so loot tables are loaded again and
the queued operations are applied.

Reload does **not** re-run `registry/` or `startup/`. A server restart is the
only way to apply registry changes or re-run startup scripts.

---

## 6. Data types and argument parsing

### 6.1 Item IDs

The canonical form is:

```
namespace:path
namespace:path:metadata
```

Examples:

```lua
"minecraft:stone"
"minecraft:wool:14"
"thermalfoundation:material:128"
"my_mod:ruby"
```

Rules:

- At least two colon-separated parts are required.
- The namespace and path are resolved through `ForgeRegistries.ITEMS`.
- An unknown item raises `unknown item '<id>'`.
- The optional third part is parsed as an integer metadata value.
- A non-numeric metadata suffix raises `bad metadata in '<id>'` in the loot and
  machine APIs, and is silently treated as `0` in `item()`.

### 6.2 Ore Dictionary entries

Prefix an entry with `ore:`:

```lua
"ore:ingotIron"
"ore:logWood"
"ore:blockGlass"
```

`ore(name)` is a convenience function:

```lua
ore("ingotIron")        -- returns "ore:ingotIron"
```

Ore entries are accepted:

- In recipe ingredients (`recipes.shaped` keys, `recipes.shapeless` list).
- As machine recipe inputs.

They are **not** accepted as an output, as a loot item, or as a registry ID.

### 6.3 Item stack table

Anywhere an item is accepted, a table may be used instead of a string:

```lua
{ id = "minecraft:diamond", count = 4, meta = 0 }
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `id` | string | required | Item ID, optionally with metadata suffix |
| `count` | integer | `1` | Stack size |
| `meta` | integer | `0` | Metadata; an ID suffix overrides it |

If `id` also contains metadata, the suffix wins:

```lua
{ id = "minecraft:wool:14", meta = 0 }   -- metadata 14
```

`item(id)` validates the item and returns a stack table:

```lua
local stack = item("minecraft:diamond")
-- { id = "minecraft:diamond", count = 1 }
```

If the ID contains metadata, the returned table also includes `meta`:

```lua
local wool = item("minecraft:wool:14")
-- { id = "minecraft:wool:14", count = 1, meta = 14 }
```

`item()` does not check that the metadata exists; it only checks the item
registry entry.

In ingredient positions, a table contributes only its `id`; `count` and `meta`
from the table are ignored by `recipes.shaped`/`recipes.shapeless` keys and
lists. Use a string with a metadata suffix when a recipe ingredient needs
metadata.

### 6.4 Machine recipe shorthand

Machine recipe arguments accept a compact string syntax:

| Form | Meaning |
|---|---|
| `"minecraft:iron_ingot"` | Item, count 1, metadata 0 |
| `"minecraft:wool:14"` | Item, count 1, metadata 14 |
| `"minecraft:iron_dust * 3"` | Item, count 3 (1..64) |
| `"ore:oreIron"` | Ore Dictionary entry (inputs only) |
| `"fluid:water * 1000"` | Fluid with amount in mB (crucible output only) |

Rules:

- The count separator is `*`, and it is split at the first occurrence.
- Count must be between 1 and 64 inclusive.
- `ore:` inputs expand differently per machine; see
  [§11.2](#112-input-expansion).
- Fluid names are resolved through `FluidRegistry`; amount defaults to `1000`
  and must be at least 1.

### 6.5 Numeric arguments

- Energy, time and RF-per-tick values must be Lua numbers.
- They are converted with `toint()`, so fractional values are truncated.
- Values below 1 raise `value must be at least 1`.
- `chance` accepts 0..100 and defaults to `100`.
- Optional arguments can be omitted; missing values use the documented default.

---

## 7. Global API

### 7.1 Logging

```lua
print("message")
log.info("message")
log.warn("message")
log.error("message")
```

Output goes to the Minecraft log with the prefix `[lua]`:

```
[Server thread/INFO] [moontweaker]: [lua] message
```

Each function accepts one argument. Additional arguments are ignored by the
binding; concatenate strings explicitly:

```lua
log.info("x=" .. x .. " y=" .. y)
```

Logging is not recorded in `/moontweaker errors`. Only load-time script
exceptions are.

### 7.2 `item(id)`

```lua
item("minecraft:diamond")
item("minecraft:wool:14")
```

Returns a stack table, or raises a Lua error if the item is unknown. See
[§6.3](#63-item-stack-table).

### 7.3 `ore(name)`

```lua
ore("ingotIron")   -- "ore:ingotIron"
```

### 7.4 `require(name)`

Loads a module from the current stage folder. See [§5.6](#56-require).

### 7.5 `os`

Restricted clock functions only: `os.time`, `os.clock`, `os.difftime`. See
[§5.7](#57-sandbox).

---

## 8. Recipe API

The `recipes` table is installed in every stage. Use it in
`lua_scripts/server/`; recipes added or removed there are processed while the
recipe registry is open.

### 8.1 `recipes.remove(target)`

Removes crafting recipes from the global recipe registry.

Accepted forms:

```lua
-- By output item (string is treated as an output stack)
recipes.remove("minecraft:stick")

-- By exact recipe registry ID
recipes.remove({ id = "minecraft:stick" })

-- By output item in table form
recipes.remove({ output = "minecraft:stone" })

-- Both: union of matching registry IDs and matching outputs
recipes.remove({ id = "minecraft:torch", output = "minecraft:stone" })
```

Return value: the number of removed recipes (`integer`).

```lua
local removed = recipes.remove({ output = "minecraft:stone" })
log.info("removed " .. removed .. " recipes")
```

Behaviour:

- A string target is parsed as an output item stack, not a registry ID.
- `{ id = ... }` compares `recipe.registryName.toString()` exactly.
- `{ output = ... }` matches the recipe's primary output item and metadata.
- If both `id` and `output` are present, a recipe matching either condition is
  removed.
- `minecraft:wool:32767` uses the wildcard metadata value and matches all
  metadata variants.
- Removal logs the total:

  ```
  MoonTweaker: removed 3 recipes
  ```

- A recipe that cannot be removed is logged as a warning and skipped; the
  remainder still run.

Limitations:

- Only recipes in `ForgeRegistries.RECIPES` are affected. **Furnace recipes are
  stored in `FurnaceRecipes` and cannot be removed with this function.**
- Recipes without a registry name cannot be targeted by `{ id = ... }`.
- Nested or custom recipe classes are matched only by their primary output.

### 8.2 `recipes.shaped(output, pattern, keys)`

```lua
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)
```

| Argument | Type | Description |
|---|---|---|
| `output` | item string/table | Recipe result, including optional count/metadata |
| `pattern` | array of strings | Rows of the crafting grid |
| `keys` | table | Single-character key → ingredient |

Behaviour:

- The grid width is the length of the longest row.
- Rows may have different lengths; missing cells are treated as empty.
- A space (`" "`) is an empty cell.
- Every non-space character must have a key, otherwise:
  `shaped(): missing key 'X'`.
- Key values may be item IDs, stack tables (only `id` is used) or `ore:` entries.
- An empty pattern raises `shaped(): empty pattern`.
- The recipe is registered with the ID `moontweaker:script_<n>`.

Example with a hole and mixed ingredients:

```lua
recipes.shaped(
  "minecraft:iron_pickaxe",
  { "III", " S ", " S " },
  {
    ["I"] = "minecraft:iron_ingot",
    ["S"] = "minecraft:stick",
  }
)
```

Output with count:

```lua
recipes.shaped(
  { id = "minecraft:torch", count = 4 },
  { "C", "S" },
  { ["C"] = "minecraft:coal", ["S"] = "minecraft:stick" }
)
```

### 8.3 `recipes.shapeless(output, ingredients)`

```lua
recipes.shapeless(
  "minecraft:planks",
  { "minecraft:log", "minecraft:log" }
)
```

| Argument | Type | Description |
|---|---|---|
| `output` | item string/table | Recipe result |
| `ingredients` | array | Item IDs, stack tables or `ore:` entries |

Behaviour:

- The list must not be empty, otherwise
  `shapeless(): empty ingredient list`.
- Use array indices (`{ a, b, c }`), not string keys; the length operator is
  used.
- Ore entries expand to an ore ingredient, so any matching item works.
- The recipe is registered with the ID `moontweaker:script_<n>`.

Example:

```lua
recipes.shapeless(
  "my_mod:mystery_token",
  { "ore:ingotCopper", "minecraft:redstone" }
)
```

### 8.4 `recipes.smelting(input, output, experience?)`

> **Argument order:** input first, output second. This differs from many recipe
> APIs.

```lua
recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)
```

| Argument | Type | Default | Description |
|---|---|---|---|
| `input` | item string/table | required | Furnace input |
| `output` | item string/table | required | Furnace result |
| `experience` | number | `0.0` | Experience awarded |

Examples:

```lua
-- Cobblestone -> stone
recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)

-- Stick -> torch (from the bundled example)
recipes.smelting("minecraft:stick", "minecraft:torch", 0.5)
```

Furnace recipes are added to `FurnaceRecipes` immediately and are **not**
registered as `IRecipe` entries. They therefore:

- cannot be removed with `recipes.remove`;
- do not appear as crafting-table recipes in JEI;
- use the first matching input stack.

### 8.5 Recipe ID counter

Each shaped/shapeless recipe gets a registry name:

```
moontweaker:script_0
moontweaker:script_1
moontweaker:script_2
```

The counter is reset by `/moontweaker reload`. Because the recipe registry is
frozen after server start, attempting to add a recipe after reload fails; the
name reset is not a way to replace recipes.

---

## 9. Loot API

The `loot` table is installed in every stage. Use it in `server/` or `startup/`.
Operations are queued and applied when Forge fires `LootTableLoadEvent` for the
matching table.

Table names are vanilla resource locations:

```lua
"minecraft:chests/simple_dungeon"
"minecraft:chests/nether_bridge"
"minecraft:chests/village_blacksmith"
"minecraft:chests/abandoned_mineshaft"
"minecraft:chests/stronghold_library"
"minecraft:entities/zombie"
```

A table name must contain a colon, otherwise:

```
bad loot table name '<name>'
```

Operations that target a table which never loads are simply never applied; no
error is raised.

### 9.1 `loot.add(table, spec)`

Adds a new pool containing one weighted item entry. Because every call creates
its own pool, each call is rolled independently.

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
| `item` | string | required | Item ID, optionally with metadata suffix |
| `min` | integer | `1` | Minimum stack size |
| `max` | integer | `min` | Maximum stack size |
| `weight` | integer | `1` | Selection weight inside the pool |
| `quality` | integer | `0` | Quality bonus scaled by luck |
| `meta` | integer | ID suffix, else `0` | Item metadata |

Validation:

| Condition | Error |
|---|---|
| `min < 1` | `loot.add(): 'min' must be at least 1` |
| `max < min` | `loot.add(): 'max' must be >= 'min'` |
| `weight < 0` | `loot.add(): 'weight' must be >= 0` |
| missing `item` | `loot.add(): 'item' is required` |
| wrong spec type | `loot.add(): expected item id or table` |
| unknown item | `unknown item '<id>'` |
| bad metadata suffix | `bad metadata in '<id>'` |

Metadata precedence:

1. `spec.meta`, if present;
2. metadata suffix in `spec.item`;
3. `0`.

String shorthand:

```lua
loot.add("minecraft:chests/stronghold_library", "minecraft:book")
```

Implementation details:

- The operation adds a `LootPool` with one `LootEntryItem`.
- `SetCount` is always applied with the `min..max` range.
- `SetMetadata` is applied only when the effective metadata is non-zero.
- Pool and entry names use the prefix `moontweaker_` and a counter that is
  reset when the server stage loads.
- `quality` is passed through to the entry; it is not validated.

Example with metadata:

```lua
loot.add("minecraft:chests/desert_pyramid", {
  item = "minecraft:wool:14",
  min = 1,
  max = 2,
  weight = 5,
})
```

### 9.2 `loot.remove(table, item)`

Removes direct item entries from a table.

```lua
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")
loot.remove("minecraft:chests/abandoned_mineshaft", { item = "minecraft:rail" })
```

Behaviour:

- Only `LootEntryItem` entries are inspected.
- Matching is by **item identity**, so metadata is ignored:
  `minecraft:wool:14` removes entries for all wool metadata values.
- Pools that become empty are removed.
- Nested table entries, empty entries and custom entry classes are left alone.
- The item ID must resolve to a registered item.

### 9.3 `loot.clear(table)`

Removes every pool from the table.

```lua
loot.clear("minecraft:chests/simple_dungeon")
```

Usually combined with `loot.add`:

```lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "my_mod:mystery_token",
  min = 1,
  max = 3,
  weight = 1,
})
```

Operations are applied in the order they were queued, so `clear` followed by
`add` produces a table containing only the added entries.

### 9.4 When loot changes apply

`LootTableLoadEvent` fires when the server loads a loot table. MoonTweaker
applies all queued operations whose table name matches, then logs:

```
MoonTweaker: applied 2 loot changes to minecraft:chests/simple_dungeon
```

Recipe for applying changes:

1. Edit loot scripts in `server/`.
2. `/moontweaker reload` — re-queues operations; this also discards operations
   queued by `startup/`.
3. `/reload` — reloads loot tables, applying the queued operations.
4. Or restart the server.

Failure during application is logged as an error and does not stop other
operations:

```
MoonTweaker: failed to change loot table <table>: <error>
```

### 9.5 Loot internals

Forge 1.12.2 does not expose mutable pool/entry lists publicly. MoonTweaker uses
reflection with deobfuscated and SRG fallbacks:

| Class | Field | SRG fallback |
|---|---|---|
| `LootTable` | `pools` | `field_186466_c` |
| `LootPool` | `lootEntries` | `field_186453_a` |
| `LootEntryItem` | `item` | `field_186368_a` |

This is why a severe Forge mapping change could break loot editing.

---

## 10. Registry API

The `registry` table is available **only** in `lua_scripts/registry/`.

In `startup/` and `server/`, every function raises:

```
registry API is only available in lua_scripts/registry/
```

Registry scripts run during `FMLPreInitializationEvent`. They queue objects;
MoonTweaker registers them during `RegistryEvent.Register<Block>` and
`RegistryEvent.Register<Item>`. A restart is required to apply changes.

### 10.1 Namespace

Default namespace: `moontweaker`.

```lua
registry.namespace("my_mod")
```

Rules:

- Must match `^[a-z0-9_.-]+$`; uppercase letters are rejected.
- Applies to IDs created after the call.
- Fully qualified IDs bypass the prefix:

  ```lua
  registry.item("other_mod:custom_item")
  ```

- Invalid namespace:

  ```
  bad namespace '<value>'
  ```

- IDs are checked for duplicates within one load:

  ```
  duplicate id '<namespace:path>'
  ```

  A duplicate aborts the current script file. Objects queued earlier in the
  same file remain queued.

### 10.2 `registry.item(name, opts)`

Registers a plain item in the Miscellaneous creative tab.

```lua
registry.item("ruby", { max_stack = 64 })
```

| Option | Type | Default | Notes |
|---|---|---|---|
| `max_stack` | integer | `64` | Clamped to 1..64 |

Translation key: `<path>`. Full ID: `<namespace>:<name>`.

### 10.3 `registry.material(kind, name, opts)`

Registers a material item with a conventional suffix.

| `kind` | Suffix | Example |
|---|---|---|
| `ingot` | `_ingot` | `my_mod:copper_ingot` |
| `dust` | `_dust` | `my_mod:copper_dust` |
| `gem` | `_gem` | `my_mod:ruby_gem` |

```lua
registry.material("ingot", "copper")
registry.material("dust", "copper")
registry.material("gem", "ruby")
```

`opts` accepts the same fields as `registry.item`. Unknown kinds raise:

```
unknown material kind '<kind>' (ingot, dust, gem)
```

### 10.4 `registry.block(name, opts)`

Registers a block and its `ItemBlock`, so it can be held and placed.

```lua
registry.block("copper_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})
```

| Option | Type | Default |
|---|---|---|
| `hardness` | number | `2.0` |
| `resistance` | number | `10.0` |
| `material` | string | `"rock"` |

Supported materials:

| Value | Minecraft material |
|---|---|
| `rock` | `Material.ROCK` |
| `wood` | `Material.WOOD` |
| `iron` | `Material.IRON` |
| `glass` | `Material.GLASS` |
| `sand` | `Material.SAND` |
| `ground` | `Material.GROUND` |
| `cloth` | `Material.CLOTH` |

Creative tab: Building Blocks.

Unknown material:

```
unknown block material '<name>'
```

Limitations: no blockstate/model generation, no tile entity, no custom harvest
level or drops. See [§18](#18-limitations).

### 10.5 `registry.tool(kind, material, opts)`

Creates a tool material and one tool item.

| `kind` | Item class | ID |
|---|---|---|
| `pickaxe` | `ItemPickaxe` | `<material>_pickaxe` |
| `axe` | `ItemAxe` | `<material>_axe` |
| `shovel` | `ItemSpade` | `<material>_shovel` |
| `sword` | `ItemSword` | `<material>_sword` |
| `hoe` | `ItemHoe` | `<material>_hoe` |

```lua
registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})
```

| Option | Type | Default | Meaning |
|---|---|---|---|
| `level` | integer | `1` | Harvest level |
| `durability` | integer | `180` | Maximum durability |
| `efficiency` | number | `4.0` | Mining speed |
| `damage` | number | `1.0` | Attack damage modifier |
| `enchantability` | integer | `10` | Enchantability |

Behaviour:

- Tool materials are keyed by `<namespace>:<material>`.
- The **first** call for a material defines its stats; later tools with the same
  material reuse the existing material and ignore their `opts`.
- The axe uses attack damage `6.0` and speed `-3.0` as constructor values.
- Creative tab: Tools.
- Translation key: `<material>_<kind>`.

To get a full tool set, call the function once per kind with the same material
and the same options.

### 10.6 `registry.armor(material, opts)`

Creates an armor material and a full set:

| Piece | ID | Slot |
|---|---|---|
| Helmet | `<material>_helmet` | Head |
| Chestplate | `<material>_chestplate` | Chest |
| Leggings | `<material>_leggings` | Legs |
| Boots | `<material>_boots` | Feet |

```lua
registry.armor("copper", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
  toughness = 0.0,
})
```

| Option | Type | Default | Notes |
|---|---|---|---|
| `durability` | integer | `15` | Durability multiplier |
| `reduction` | array of 4 integers | `{1, 3, 2, 1}` | Helmet, chest, legs, boots |
| `enchantability` | integer | `10` | Enchantability |
| `toughness` | number | `0.0` | Armor toughness |

Behaviour:

- Armor materials are keyed by `<namespace>:<material>`; the first call wins.
- The armor texture base name is `<namespace>_<material>`.
- Render index is `0`.
- Creative tab: Combat.
- If `reduction` is not a table, the default `{1, 3, 2, 1}` is used. If it is a
  table, its four entries are read with `0` as the fallback for missing values.

### 10.7 `registry.fluid(name, opts)`

Registers a fluid, its world block and a bucket item.

```lua
registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  temperature = 300,
  luminosity = 0,
  color = 0x1A1A1A,
  gaseous = false,
})
```

| Option | Type | Default |
|---|---|---|
| `density` | integer | `1000` |
| `viscosity` | integer | `1000` |
| `temperature` | integer | `300` |
| `luminosity` | integer | `0` |
| `color` | integer (RGB) | `0xFFFFFF` |
| `gaseous` | boolean | `false` |

Behaviour:

- Internal fluid name: `<namespace>_<path>`.
- Still texture: `<namespace>:blocks/<path>_still`.
- Flowing texture: `<namespace>:blocks/<path>_flowing`.
- The world block uses the same registry ID as the fluid and is a
  `BlockFluidClassic` with `Material.WATER`.
- The bucket item is `<path>_bucket` in the Miscellaneous tab.
- `FluidRegistry.addBucketForFluid(fluid)` is called.
- Registering an already registered fluid raises:

  ```
  fluid '<id>' is already registered
  ```

Textures required on the client:

```
assets/<namespace>/textures/blocks/<path>_still.png
assets/<namespace>/textures/blocks/<path>_flowing.png
```

### 10.8 Assets and translations

The registry API creates game objects only. Models, blockstates, textures and
language entries must come from a resource pack using the same namespace.

Expected layout:

```
assets/<namespace>/models/item/<id>.json
assets/<namespace>/textures/items/<id>.png
assets/<namespace>/blockstates/<id>.json
assets/<namespace>/models/block/<id>.json
assets/<namespace>/textures/blocks/<id>.png
assets/<namespace>/lang/en_us.lang
```

Armor textures:

```
assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_1.png
assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_2.png
```

Language keys:

| Type | Key |
|---|---|
| Item | `item.<id>.name` |
| Block | `tile.<id>.name` |
| Fluid | `fluid.<id>` |

Conventions:

- Plain items, tools and armor use `item/generated`; tools often look better
  with `item/handheld`.
- Blocks need a blockstate, a block model and an item model.
- Without assets the content still exists on the server but renders as missing
  textures on the client.

### 10.9 Registry internals

- Pending objects are stored in lists inside `RegistryApi`.
- `RegistryHandler` registers blocks, then items, during the corresponding
  Forge registry events.
- `RegistryApi.reset()` clears the namespace and all pending content each time
  the registry stage is loaded.
- EnumHelper is used to create tool and armor materials. Enum names are
  sanitized to uppercase ASCII and prefixed with the namespace.
- The fluid block and the fluid share an ID but live in different registries;
  the duplicate-ID check only applies to the Lua-created object list.

---

## 11. Machine recipe API

The `moonexpansion` table adds recipes to supported tech mods.

| Mod | Machine | Lua path |
|---|---|---|
| Thermal Expansion | Pulverizer | `moonexpansion.thermal.pulverizer` |
| Thermal Expansion | Induction Smelter | `moonexpansion.thermal.smelter` |
| Thermal Expansion | Magma Crucible | `moonexpansion.thermal.crucible` |
| Ender IO | SAG Mill | `moonexpansion.enderio.sag_mill` |
| Ender IO | Alloy Smelter | `moonexpansion.enderio.alloy_smelter` |
| Immersive Engineering | Crusher | `moonexpansion.ie.crusher` |
| Immersive Engineering | Blast Furnace | `moonexpansion.ie.blast_furnace` |
| Immersive Engineering | Arc Furnace | `moonexpansion.ie.arc_furnace` |

Availability:

```lua
log.info("thermal=" .. tostring(moonexpansion.mods.thermal))
log.info("enderio=" .. tostring(moonexpansion.mods.enderio))
log.info("ie=" .. tostring(moonexpansion.mods.ie))
```

| Field | Meaning |
|---|---|
| `moonexpansion.mods.thermal` | Thermal Expansion loaded |
| `moonexpansion.mods.enderio` | Ender IO loaded |
| `moonexpansion.mods.ie` | Immersive Engineering loaded |

Calling a machine API for an uninstalled mod raises:

```
MoonTweaker: <mod name> is not installed
```

### 11.1 Stage and timing rules

- Use in `lua_scripts/server/` or `lua_scripts/startup/`.
- In `lua_scripts/registry/`:

  ```
  moonexpansion: use lua_scripts/server/ or lua_scripts/startup/
  ```

- Calls only **queue** operations. They are applied once at
  `FMLLoadCompleteEvent`.
- After startup has applied recipes, any further call raises:

  ```
  moonexpansion: machine recipes are applied at startup; restart the game
  ```

- Argument validation happens immediately; semantic failures (duplicate Thermal
  Expansion inputs, missing reflection methods, unsupported mod version) happen
  later at apply time and are logged:

  ```
  MoonTweaker: failed to add <description>: <message>
  ```

- Successful apply logs the total:

  ```
  MoonTweaker: applied 9 machine recipes
  ```

- `/moontweaker reload` clears queued operations but cannot re-apply them,
  because the applied flag remains set. Restart to change machine recipes.

### 11.2 Input expansion

`ore:` inputs are expanded before the recipe is added:

| Machine type | Expansion |
|---|---|
| Single-input (Pulverizer, Crucible, SAG Mill, IE Crusher) | One recipe per matching item |
| Two-input (Induction Smelter, Alloy Smelter) | One recipe per combination of matching items |
| Immersive Engineering Blast/Arc Furnace | Ore name passed through as a dictionary reference |

For expanded ore inputs, each generated stack uses the ore entry's metadata and
count 1. A count cannot be attached to an `ore:` input.

### 11.3 Thermal Expansion

#### `moonexpansion.thermal.pulverizer.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.thermal.pulverizer.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  2500,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `energy` | `3000` | RF |
| `secondary_output` | none | Optional |
| `chance` | `100` | 0..100 |

If a secondary output is supplied, the five-argument Thermal Expansion overload
is used; otherwise the three-argument overload is used.

#### `moonexpansion.thermal.smelter.add_recipe(output, primary_input, secondary_input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.thermal.smelter.add_recipe(
  "minecraft:gold_ingot",
  "minecraft:apple",
  "minecraft:sand",
  3500,
  "minecraft:gold_nugget",
  50
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `primary_input` | required | Item or ore entry |
| `secondary_input` | required | Must be a string; both inputs are mandatory |
| `energy` | `4000` | RF |
| `secondary_output` | none | Optional |
| `chance` | `100` | 0..100 |

Missing second input:

```
thermal.smelter needs a secondary input
```

#### `moonexpansion.thermal.crucible.add_recipe(fluid_output, input, energy?)`

```lua
moonexpansion.thermal.crucible.add_recipe(
  "fluid:water * 250",
  "minecraft:apple",
  1500
)
```

| Parameter | Default | Notes |
|---|---|---|
| `fluid_output` | required | `"fluid:<name> * <amount>"`, amount defaults to `1000` mB |
| `input` | required | Item or ore entry |
| `energy` | `2000` | RF |

Thermal Expansion rejects duplicate inputs. A duplicate is detected at apply
time (`addRecipe` returns `null`), logged as a failure, and not added.

### 11.4 Ender IO

#### `moonexpansion.enderio.sag_mill.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.enderio.sag_mill.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  240,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `energy` | `240` | RF |
| `secondary_output` | none | Optional |
| `chance` | `100` | 0..100 |

Recipes use:

- `RecipeBonusType.CHANCE_ONLY`
- `RecipeLevel.IGNORE`

The secondary output chance is converted to a fraction (`chance / 100.0`).

#### `moonexpansion.enderio.alloy_smelter.add_recipe(output, input, second_input?, energy?)`

```lua
moonexpansion.enderio.alloy_smelter.add_recipe(
  "minecraft:gold_ingot",
  "minecraft:apple",
  "minecraft:sand",
  2400
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `second_input` | none | Optional item or ore entry |
| `energy` | `2400` | RF |

At most two inputs are supported. Each `ore:` input expands to all matching
items, producing one recipe per combination. The recipe level is
`RecipeLevel.IGNORE`.

### 11.5 Immersive Engineering

#### `moonexpansion.ie.crusher.add_recipe(output, input, energy?)`

```lua
moonexpansion.ie.crusher.add_recipe(
  "minecraft:iron_dust * 3",
  "ore:oreIron"
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry (ore stays a dictionary reference) |
| `energy` | `3000` | Total RF cost |

Process time stays at the Immersive Engineering default; only the RF cost is
settable.

#### `moonexpansion.ie.blast_furnace.add_recipe(output, input, time?, slag?)`

```lua
moonexpansion.ie.blast_furnace.add_recipe(
  "minecraft:glass",
  "minecraft:sand",
  800
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `time` | `1200` | Ticks |
| `slag` | none | Optional item stack |

#### `moonexpansion.ie.arc_furnace.add_recipe(output, input, time?, energy_per_tick?, slag?)`

```lua
moonexpansion.ie.arc_furnace.add_recipe(
  "minecraft:flint * 2",
  "minecraft:gravel",
  100,
  80
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `time` | `200` | Ticks |
| `energy_per_tick` | `100` | RF/t |
| `slag` | none | Optional item stack |

### 11.6 Reflection classes

The integration is deliberately version-tolerant but not version-proof. These
classes are resolved at apply time:

| Purpose | Class |
|---|---|
| Thermal pulverizer | `cofh.thermalexpansion.util.managers.machine.PulverizerManager` |
| Thermal smelter | `cofh.thermalexpansion.util.managers.machine.SmelterManager` |
| Thermal crucible | `cofh.thermalexpansion.util.managers.machine.CrucibleManager` |
| Ender IO SAG Mill | `crazypants.enderio.base.recipe.sagmill.SagMillRecipeManager` |
| Ender IO Alloy Smelter | `crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager` |
| Ender IO recipe | `crazypants.enderio.base.recipe.Recipe` |
| Ender IO input | `crazypants.enderio.base.recipe.RecipeInput` |
| Ender IO input interface | `crazypants.enderio.base.recipe.IRecipeInput` |
| Ender IO output | `crazypants.enderio.base.recipe.RecipeOutput` |
| Ender IO bonus | `crazypants.enderio.base.recipe.RecipeBonusType` |
| Ender IO level | `crazypants.enderio.base.recipe.RecipeLevel` |
| Ender IO list | `com.enderio.core.common.util.NNList` |
| IE crusher | `blusunrize.immersiveengineering.api.crafting.CrusherRecipe` |
| IE blast furnace | `blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe` |
| IE arc furnace | `blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe` |

Static methods are located by **name and parameter count**, not by exact
signature. If a mod version changes overloads or class names, the affected
recipe is logged as failed and the rest continue.

### 11.7 Machine recipe limitations

- Add-only: existing machine recipes cannot be removed or edited.
- Thermal Expansion duplicate inputs are rejected.
- Ender IO and Immersive Engineering may accept the same recipe twice; adding it
  twice can create duplicates.
- Machine recipes cannot be applied by `/moontweaker reload`; restart required.
- IE crusher process time is fixed; only RF cost is configurable.

---

## 12. Event API

`events.on(name, callback)` registers a listener. Callbacks receive one table
argument. Listeners can be registered from `server/` or `startup/`; listeners
registered earlier are cleared when `server/` loads.

```lua
events.on("block.break", function(e)
  log.info(e.player.name .. " broke " .. e.block)
end)
```

### 12.1 Event list

| Event | Fields | Cancelable |
|---|---|---|
| `player.logged_in` | `player` | No |
| `block.break` | `player`, `block`, `pos` | Yes |
| `block.place` | `player`, `block`, `pos` | Yes |
| `entity.death` | `entity`, `player` (may be `nil`) | Yes |
| `item.use` | `player`, `item` | Yes |
| `server.tick` | none | No |

Unknown event names are accepted silently. A typo results in a listener that is
never called; there is no validation error.

### 12.2 Data shapes

Player:

```lua
{
  name = "Player",
  uuid = "00000000-0000-0000-0000-000000000000",
  x = 0.0,
  y = 64.0,
  z = 0.0,
  dimension = 0,
}
```

Position:

```lua
{ x = 0, y = 64, z = 0 }
```

Item:

```lua
{ id = "minecraft:diamond_pickaxe", count = 1, meta = 0 }
```

Block and entity fields are registry ID strings:

```
"minecraft:diamond_ore"
"minecraft:zombie"
```

If a registry key cannot be resolved, the field falls back to
`"minecraft:air"` for blocks, `"minecraft:air"` for items and `"unknown"` for
entities.

### 12.3 Cancelling

Every event context exposes `e:cancel()`. It is only effective for cancelable
events; on `player.logged_in` and `server.tick` it does nothing.

```lua
events.on("block.break", function(e)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)
```

| Event | Cancel effect |
|---|---|
| `block.break` | Cancels the break before the block is removed |
| `block.place` | Cancels the placement |
| `entity.death` | Cancels the death event |
| `item.use` | Cancels the item use |

### 12.4 Event details

#### `player.logged_in`

Fires from `PlayerLoggedInEvent`. Not cancelable.

```lua
events.on("player.logged_in", function(e)
  log.info(e.player.name .. " joined at " .. e.player.x .. "," .. e.player.y)
end)
```

#### `block.break`

Fires from `BlockEvent.BreakEvent`, before the block is removed.

```lua
events.on("block.break", function(e)
  log.info(e.player.name .. " broke " .. e.block .. " at " .. e.pos.x .. "," .. e.pos.y .. "," .. e.pos.z)
end)
```

#### `block.place`

Fires from `BlockEvent.PlaceEvent` for the placed block state.

#### `entity.death`

Fires from `LivingDeathEvent`.

- `e.entity` is the entity registry ID.
- `e.player` is the player credited as the true source, or `nil`.
- Cancelling prevents the death event from completing.

```lua
events.on("entity.death", function(e)
  if e.entity == "minecraft:zombie" and e.player ~= nil then
    log.info(e.player.name .. " killed a zombie")
  end
end)
```

#### `item.use`

Fires from `PlayerInteractEvent.RightClickItem`.

- This is the right-click-air/item path. Right-clicking a block fires a
  different Forge event that MoonTweaker does not expose.
- `e.item` describes the held stack.

#### `server.tick`

Fires at the end of every server tick, 20 times per second.

```lua
events.on("server.tick", function(e)
  -- keep this cheap
end)
```

### 12.5 Listener lifecycle and errors

- Listeners are stored in `EventApi` and cleared when the server stage loads.
- A reload clears all listeners, including those registered by `startup/`;
  only `server/` is re-run, so startup listeners are lost until restart.
- Duplicate registrations are allowed and all run.
- There is no unsubscribe API and no one-shot listener.
- Exceptions inside a callback are caught and logged:

  ```
  MoonTweaker: error in event '<name>': <message>
  ```

They do **not** appear in `/moontweaker errors` and do not cancel other
listeners.

---

## 13. Commands

| Command | Description |
|---|---|
| `/moontweaker reload` | Reload and re-run `lua_scripts/server/` |
| `/moontweaker errors` | Print the last load-time script errors |
| `/moontweaker` | Print usage |

Example output:

```
MoonTweaker reloaded: 4 scripts, 1 errors
Run /moontweaker errors for details
```

```
server/broken.lua: 12: unknown item 'foo:bar'
```

Permissions:

- `checkPermission` always returns `true`.
- Both subcommands are available to every player.
- There is no permission node yet. Restrict the command with a permissions
  plugin on public servers.

Tab completion suggests `reload` and `errors`.

`/moontweaker errors` only lists script-loading errors. It does not list:

- event callback errors (logged only);
- loot application errors (logged only);
- machine recipe application errors (logged only).

---

## 14. Logging and diagnostics

All messages use the `moontweaker` logger.

| Message | Meaning |
|---|---|
| `MoonTweaker: stage '<stage>' loaded, N scripts, M errors` | A stage finished loading |
| `[lua] <message>` | Script `print`/`log` output |
| `MoonTweaker: removed N recipes` | Result of `recipes.remove` |
| `MoonTweaker: registered N blocks` | Queued blocks registered |
| `MoonTweaker: registered N items` | Queued items registered |
| `MoonTweaker: applied N loot changes to <table>` | Loot operations applied |
| `MoonTweaker: failed to change loot table <table>: <error>` | A loot operation failed |
| `MoonTweaker: applied N machine recipes` | Machine queue applied |
| `MoonTweaker: failed to add <description>: <message>` | One machine recipe failed |
| `MoonTweaker: cannot remove recipe <id>: <message>` | Recipe removal was rejected |
| `MoonTweaker: error in event '<name>': <message>` | Event callback error |

Recommended checks after editing scripts:

1. Search the log for `MoonTweaker:`.
2. Run `/moontweaker errors`.
3. Verify stage-specific timing (recipes during startup, loot after `/reload`,
   machine recipes only after restart).

---

## 15. Architecture and internals

### 15.1 Component map

| File | Responsibility |
|---|---|
| `MoonTweaker.kt` | `@Mod` entry point, constants, logger, `ScriptManager`, Forge event wiring |
| `api/MoonTweakerApi.kt` | Tracks the currently loading stage (`registry`, `server`, `startup`) |
| `script/ScriptManager.kt` | Directory discovery, sandboxed `Globals`, file execution, `require`, errors, reload |
| `lua/RecipeApi.kt` | `item`, `recipes.remove/shaped/shapeless/smelting` |
| `loot/LootApi.kt` | `loot.add/remove/clear`, operation queue, reflection-based table mutation |
| `loot/LootTableHandler.kt` | `LootTableLoadEvent` → `LootApi.apply` |
| `registry/RegistryApi.kt` | `registry.*` bindings and pending object lists |
| `registry/RegistryHandler.kt` | `RegistryEvent.Register<Block/Item>` registration |
| `expansion/ExpansionApi.kt` | `moonexpansion.*`, reflection calls, deferred apply |
| `event/EventApi.kt` | Listener registry, context creation, dispatch, error isolation |
| `event/GameplayEvents.kt` | Forge event subscribers and Lua context construction |
| `event/RecipeRegistryHook.kt` | Loads `server/` at LOWEST priority during recipe registration |
| `command/MoonTweakerCommand.kt` | `/moontweaker reload` and `/moontweaker errors` |

### 15.2 Lifecycle sequence

```
Forge preInit
  └─ ScriptManager.loadRegistry()
       ├─ RegistryApi.reset()
       ├─ new Globals for registry stage
       └─ execute lua_scripts/registry/**
  └─ register RegistryHandler, LootTableHandler, RecipeRegistryHook, GameplayEvents

RegistryEvent.Register<Block>
  └─ RegistryHandler registers pending blocks

RegistryEvent.Register<Item>
  └─ RegistryHandler registers pending items and ItemBlocks

RegistryEvent.Register<IRecipe> (LOWEST)
  └─ RecipeRegistryHook → ScriptManager.loadServer()
       ├─ EventApi.clear()
       ├─ LootApi.reset()
       ├─ ExpansionApi.reset()
       ├─ new Globals for server stage
       └─ execute lua_scripts/server/**

Forge postInit
  └─ ScriptManager.loadStartup()
       ├─ new Globals for startup stage
       └─ execute lua_scripts/startup/**

FMLLoadComplete
  └─ ExpansionApi.apply()

Server starting
  └─ register /moontweaker

Loot table load
  └─ LootApi.apply(name, table)

Gameplay
  └─ GameplayEvents → EventApi.emit
```

### 15.3 ScriptManager

Key state:

| Field | Purpose |
|---|---|
| `errors` | Thread-safe list of load-time `ScriptError(file, message)` |
| `loadedCount` | Successfully executed files in the last load |
| `modules` | Per-load `require` cache |
| `recipeCounter` | Counter for `moontweaker:script_N` |

`load(stage)`:

1. Clears the module cache.
2. Sets `MoonTweakerApi.currentStage`.
3. Resets stage-specific state (`registry` → registry content; `server` →
   events, loot, machines).
4. Creates the stage directory if missing.
5. Builds a fresh `Globals`.
6. Recursively executes `.lua` files in sorted path order.
7. Logs the summary.

`createGlobals()` loads `BaseLib`, `PackageLib`, `Bit32Lib`, `TableLib`,
`StringLib`, `CoroutineLib` and `JseMathLib`, then installs LuaC and `LoadState`.
It then removes the dangerous globals (`luajava`, `io`, `package`, `debug`,
`dofile`, `loadfile`, `load`) and installs `os`, `print`, `require`, `log`,
`ore`, `recipes`, `events`, `loot`, `registry` and `moonexpansion`.

Because `registry` is installed by `RegistryApi.install(g)` with a stage check,
non-registry stages receive a table whose functions all raise a fixed error.

### 15.4 RecipeApi

- `recipes.remove` iterates `ForgeRegistries.RECIPES`, collects matching
  registry names, then removes each through the `ForgeRegistry` API.
- Removal uses the primary output (`recipe.recipeOutput`) and item identity.
- Shaped/shapeless recipes are constructed directly with Forge classes and
  registered into `ForgeRegistries.RECIPES`.
- Ore ingredients use `OreIngredient`; item ingredients use
  `Ingredient.fromStacks`.
- Furnace recipes use `FurnaceRecipes.instance().addSmeltingRecipe`.
- Invalid item IDs fail immediately during script execution.

### 15.5 LootApi and LootTableHandler

- Operations are kept in an ordered list (`ops`).
- `add` stores a parsed item, count range, weight, quality and metadata.
- `remove` stores an item; `clear` stores only the table name.
- `apply(tableName, table)` walks the operations in order and applies those
  whose table name matches.
- Each operation is independently guarded; a failure logs and continues.
- `LootApi.reset()` clears operations and resets the pool counter.
- `LootTableHandler` calls `apply` for every `LootTableLoadEvent`.

The implementation mutates Forge's internal lists through reflection because
1.12.2 provides no public mutation API for pools and entries.

### 15.6 RegistryApi and RegistryHandler

- Pending items, blocks, tool materials and armor materials are module-level
  lists/maps.
- `registry.block` queues both the `Block` and its `ItemBlock`.
- `registry.fluid` queues a fluid, a `BlockFluidClassic` and an `ItemBucket`,
  and registers the fluid immediately with `FluidRegistry`.
- `RegistryHandler` registers queued blocks at the block event and queued items
  at the item event. Items include ItemBlocks, buckets, tools and armor.
- Duplicate Lua-side IDs are rejected before Forge sees them.

### 15.7 ExpansionApi

- Builds the `moonexpansion` table for every stage.
- `mods` booleans are computed with `Loader.isModLoaded`.
- Calls validate arguments and append an `Op(description, action)`.
- `apply()` runs every queued action, logging failures with the root cause.
- `applied` is set to `true` after the first apply, even if the queue was empty.
- Reflection helpers resolve classes by name and methods by name + parameter
  count; `InvocationTargetException` causes are unwrapped for readable logs.

### 15.8 EventApi and GameplayEvents

- Listeners are keyed by event name in a mutable map.
- `context(onCancel)` creates a table with a `cancel` function; the supplied
  lambda decides what cancellation does.
- `emit` calls each listener in registration order inside a try/catch.
- `GameplayEvents` subscribes to Forge events, checks `EventApi.has(name)` for a
  fast path, builds the context and emits.
- `server.tick` only fires on `Phase.END`.

### 15.9 Threading

- All script execution and API calls happen on the server thread.
- Gameplay events and loot table loads run on the server thread.
- There are no background tasks or asynchronous queues.
- A long-running script blocks the server.

### 15.10 State reset summary

| State | Reset when |
|---|---|
| Registry pending objects and namespace | `registry` stage load |
| `require` cache | Every stage load |
| Event listeners | `server` stage load (including reload) |
| Loot operations and pool counter | `server` stage load (including reload) |
| Machine operation queue | `server` stage load (including reload) |
| Machine `applied` flag | Never after first apply (restart required) |
| Script errors | Reload command |
| Recipe counter | Reload command |

---

## 16. Building and development

### 16.1 Build requirements

| Tool | Version |
|---|---|
| JDK to run Gradle | 25 |
| Java compilation toolchain | 8 (auto-provisioned by Foojay) |
| Gradle wrapper | 9.2.0 |
| RetroFuturaGradle | 2.0.2 |
| Kotlin Gradle plugin | 2.4.10 |

The build downloads Minecraft and Forge artifacts on the first run, so an
internet connection is required initially.

### 16.2 Commands

```bash
./gradlew build
```

Output:

```
build/libs/MoonTweaker-0.1.0.jar
```

Run a development server or client:

```bash
./gradlew runServer --args nogui
./gradlew runClient
```

The first server run requires accepting the EULA in `run/eula.txt`.

Development scripts live in `run/lua_scripts/` when using `runServer` or
`runClient`, because the run directory is the game directory.

### 16.3 Build configuration

Key points from `build.gradle`:

- `minecraft.mcVersion = '1.12.2'`
- Java toolchain pinned to 8; Kotlin JVM target 8.
- `Forgelin-Continuous:2.4.10.0` is a compile/runtime dependency.
- `luaj-jse:3.0.1` is embedded into the jar via the `embed` configuration.
- Kotlin stdlib is **not** bundled (`kotlin.stdlib.default.dependency=false`);
  Forgelin provides it at runtime.
- Signature files are excluded from the shaded jar.
- `LICENSE` and `NOTICE` are included in the jar.

### 16.4 Adding a new Lua API

1. Create an API object with an `install(g: Globals, ...)` method.
2. Register functions on a Lua table and `g.set("<name>", table)`.
3. Call `install` from `ScriptManager.createGlobals()`.
4. If the API is stage-specific, check `MoonTweakerApi.currentStage` and either
   install a restricted table or throw a `LuaError`.
5. If the API queues work, add a `reset()` and call it from the correct stage
   branch in `ScriptManager.load`.
6. Document the API in this file and `README.md`.

### 16.5 Adding a new event

1. Add a subscriber in `GameplayEvents` with `@SubscribeEvent`.
2. Check `EventApi.has("your.event")` before building context.
3. Build a context with `EventApi.context { ... }` and set fields.
4. Call `EventApi.emit("your.event", ctx)`.
5. Add the event to the table in [§12.1](#121-event-list).
6. Respect the server thread; do not block.

### 16.6 Testing checklist

Before shipping a script pack:

- [ ] Every item ID resolves (`unknown item` errors absent).
- [ ] Recipe removals target the right output or registry ID.
- [ ] Shaped patterns have keys for every non-space character.
- [ ] Ore Dictionary names are correct and populated.
- [ ] Loot tables are spelled as resource locations.
- [ ] Loot operations were applied (`applied ... loot changes` in the log).
- [ ] Registered content has models, blockstates, textures and language keys.
- [ ] Machine mods are installed before calling their APIs.
- [ ] Machine recipes were applied at startup (`applied ... machine recipes`).
- [ ] Event callbacks are cheap, especially `server.tick`.
- [ ] The script pack works after a clean restart, not only after reload.
- [ ] `/moontweaker errors` reports no errors.

---

## 17. Error reference

### 17.1 Script system

| Message | Cause | Fix |
|---|---|---|
| `invalid module: <name>` | `require` path escapes the stage folder | Keep modules inside the stage |
| `module not found: <name>` | Missing `.lua` file | Check dots→folders mapping |
| `registry API is only available in lua_scripts/registry/` | Called `registry.*` in another stage | Move code to `registry/` and restart |
| `moonexpansion: use lua_scripts/server/ or lua_scripts/startup/` | Machine call in registry stage | Move code |
| `moonexpansion: machine recipes are applied at startup; restart the game` | Machine call after startup | Restart |
| `MoonTweaker: <mod> is not installed` | Machine API for absent mod | Install mod or guard with `moonexpansion.mods.*` |

### 17.2 Items and recipes

| Message | Cause |
|---|---|
| `bad item id '<id>'` | Missing namespace or malformed ID |
| `unknown item '<id>'` | Item not in `ForgeRegistries.ITEMS` |
| `expected item id or table` | Wrong Lua type for a stack |
| `expected ingredient string` | Wrong ingredient type |
| `remove() needs 'id' or 'output'` | Removal table has neither field |
| `remove() expects item id or table` | Wrong removal argument |
| `shaped(): empty pattern` | Empty pattern table |
| `shaped(): missing key 'X'` | Pattern character has no key |
| `shapeless(): empty ingredient list` | Empty ingredient table |

### 17.3 Loot

| Message | Cause |
|---|---|
| `bad loot table name '<name>'` | Table name missing `:` |
| `loot.add(): 'item' is required` | Spec table has no `item` |
| `loot.add(): expected item id or table` | Spec is not a string/table |
| `loot.add(): 'min' must be at least 1` | Invalid range |
| `loot.add(): 'max' must be >= 'min'` | Invalid range |
| `loot.add(): 'weight' must be >= 0` | Negative weight |
| `bad metadata in '<id>'` | Non-numeric metadata suffix |
| `loot.remove(): expected item id` | Wrong removal argument |

### 17.4 Registry

| Message | Cause |
|---|---|
| `bad namespace '<value>'` | Uppercase or unsupported characters |
| `bad id '<name>'` | Malformed resource location |
| `duplicate id '<namespace:path>'` | Same ID used twice in one load |
| `unknown material kind '<kind>'` | Material kind not ingot/dust/gem |
| `unknown tool '<kind>'` | Tool kind not in the supported list |
| `unknown block material '<name>'` | Unsupported block material |
| `cannot create tool material '<name>'` | EnumHelper returned null |
| `cannot create armor material '<name>'` | EnumHelper returned null |
| `fluid '<id>' is already registered` | Fluid name already exists |

### 17.5 Machine recipes

| Message | Cause |
|---|---|
| `thermal.smelter needs a secondary input` | Third argument missing or not a string |
| `bad count in '<text>'` | Count after `*` is not an integer |
| `bad count N in '<text>'` | Count outside 1..64 |
| `bad metadata in '<id>'` | Metadata suffix is not an integer |
| `expected fluid:<name> [* amount]` | Crucible output is not a fluid string |
| `bad fluid amount in '<text>'` | Amount is not an integer |
| `bad fluid amount N` | Amount below 1 |
| `unknown fluid '<name>'` | Fluid not registered |
| `unknown ore '<name>'` | Ore Dictionary entry is empty |
| `expected a number, got '<value>'` | Energy/time argument is not numeric |
| `value must be at least 1` | Energy/time/RF/tick below 1 |
| `chance must be between 0 and 100` | Chance outside range |

### 17.6 Apply-time failures (log only)

These do not appear in `/moontweaker errors`:

```
MoonTweaker: failed to add thermal pulverizer <item>: recipe already exists
MoonTweaker: failed to add <description>: <class or method not found>
MoonTweaker: failed to change loot table <table>: <error>
MoonTweaker: error in event '<name>': <error>
```

---

## 18. Limitations

### Recipes

- Crafting recipe changes cannot be applied after server start. Restart required.
- `recipes.remove` does not remove furnace recipes.
- Removal matches the primary output or exact registry ID only.
- No recipe conditions, tags or custom recipe classes.
- No client recipe-book or JEI synchronization.
- Duplicate recipe IDs after reload cannot be registered because the registry is
  frozen; the attempt appears as a script error.

### Loot

- Loot changes are server-side only; clients are not synchronized.
- `loot.remove` matches direct item entries by item identity, ignoring metadata.
- Nested tables, custom entries, conditions and functions are not exposed.
- `loot.add` creates a new pool per call; it does not merge into an existing
  pool.
- Operations for tables that never load are silently unused.

### Registry

- Registered content can only be created once per game start; restart after
  editing.
- No model, blockstate, texture, language or Ore Dictionary generation.
- Registered blocks have no custom tile entities, harvest levels, drops or
  blockstate variants.
- Creative tabs are fixed: items and buckets → Misc, blocks → Building Blocks,
  tools → Tools, armor → Combat.
- Clients need the mod and assets for registered content.
- Tool/armor materials are first-call-wins per material name.
- No fluid interaction beyond the block and bucket.

### Machines

- Add-only; no removal or editing of existing machine recipes.
- Applied only during startup; reload cannot apply them.
- Reflection-based, so mod version changes can break individual recipes.
- Thermal Expansion rejects duplicate inputs.
- Ender IO and Immersive Engineering may accept duplicate recipes.
- Immersive Engineering crusher time is not configurable.

### Events

- Only six events are exposed.
- No event priority control from Lua.
- No unsubscribe or one-shot listeners.
- `item.use` does not cover right-clicking blocks.
- Listeners registered by `startup/` are lost on `/moontweaker reload`.
- Callback errors are logged but not shown by `/moontweaker errors`.

### Security and performance

- No instruction budget or watchdog. Infinite loops hang the server.
- Scripts are trusted; do not load untrusted scripts.
- `/moontweaker` has no permission node and is open to all players.
- `server.tick` runs 20 times per second; expensive callbacks degrade TPS.

---

## 19. Troubleshooting and FAQ

### Scripts do not run

Check:

- The file ends in `.lua`.
- It is under `lua_scripts/registry/`, `lua_scripts/server/` or
  `lua_scripts/startup/`.
- The folder is next to `config/`, not inside it.
- `/moontweaker errors` and the log for `stage '...' loaded`.

### `ClassNotFoundException: io.github.chaosunity.forgelin.KotlinAdapter`

Forgelin-Continuous is missing from `mods/`. Install a compatible version.

### `Attempted to register to late` / `Attempted to remove from the registry to late`

A script tried to change recipes after Forge locked the registry. Move the code
to `lua_scripts/server/` and restart. Reload cannot bypass this.

### Registered items have missing textures

The registry API creates only game objects. Add models, blockstates, textures
and language keys in a resource pack using the registered namespace.

### Loot changes are missing

1. Run `/moontweaker reload`.
2. Run `/reload`.
3. Or restart.
4. Search the log for `applied ... loot changes`.
5. Confirm the table name exactly matches a loaded loot table.

### Machine recipes are missing

- Machine recipes apply only during startup; restart.
- Check `moonexpansion.mods.*` for the target mod.
- Search the log for `failed to add`.
- Verify the mod version is compatible with the reflection targets.

### Recipe changes are missing in JEI

JEI reads client-side recipe data. MoonTweaker does not synchronize removed or
added recipes to clients. Restart the client and check whether the recipe is
actually present in the server registry.

### Why do `/moontweaker reload` and `/reload` differ?

- `/moontweaker reload` re-runs **server scripts** and re-queues loot and event
  operations. It cannot unlock the recipe registry or re-apply machine recipes.
- `/reload` is the vanilla command that reloads the server's data, including
  loot tables, which triggers MoonTweaker's queued loot operations.

### Can I register content from `server/`?

No. `registry` only works in `registry/`, and content registration must happen
before Forge's registry events. Restart after editing.

### Can clients connect without the mod?

Yes for recipe/loot/event changes. No for content registered with `registry`,
because clients need the same registry entries and assets.

### Can scripts access files or run shell commands?

No. `io`, `luajava`, `debug`, `load`, `loadfile`, `dofile` and `package` are
removed; `os` only exposes `time`, `clock` and `difftime`.

### Does reload reset Lua global variables?

Yes for the server stage. Each stage load creates a fresh `Globals` and clears
the `require` cache.

### Does `/moontweaker reload` re-run `startup/`?

No. It reloads only `server/`. Restart to re-run startup scripts.

### Why did my `startup/` event stop working after reload?

Because loading the server stage clears all listeners. Only server scripts are
re-run; startup listeners are not restored until restart.

### Is there a way to remove a machine recipe?

No. The machine API is add-only.

### Are scripts sandboxed strongly enough for untrusted users?

No. There is no instruction limit or watchdog, and an infinite loop hangs the
server. Only run scripts you trust.

---

## 20. License and credits

MoonTweaker is licensed under the **GNU General Public License v3.0 or later**.

- Full text: [LICENSE](LICENSE)
- Copyright notice: [NOTICE](NOTICE)

Additional term under GNU GPL v3 section 7(b): every copy and every modified
version must preserve the attribution

> MoonTweaker by Kartendsy

in the mod metadata, README and any user-visible credits. Modified versions must
be marked as modified, as required by section 5(a) of the license.

### Credits

- Author and copyright holder: **Kartendsy**
- Lua runtime: [LuaJ](https://github.com/luaj/luaj)
- Kotlin runtime for Forge 1.12.2:
  [Forgelin-Continuous](https://github.com/ChAoSUnItY/Forgelin-Continuous)
- Build toolchain:
  [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)
- Inspired by the scripting model of CraftTweaker and KubeJS

---

*Documentation for MoonTweaker 0.1.0. Behaviour described here reflects the
source in `src/main/kotlin/com/moontweaker/`.*
