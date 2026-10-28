<p align="center">
  <img src="src/main/resources/logo.png" width="160" alt="MoonTweaker logo">
</p>

# MoonTweaker

Server-side Lua scripting for **Minecraft 1.12.2 / Forge**, inspired by CraftTweaker and KubeJS.

MoonTweaker lets you edit recipes from **any mod** without writing Java, using plain Lua files that reload without restarting the game.

- **Language:** Lua 5.2 (LuaJ 3.0.1)
- **Loader:** Forge 1.12.2
- **Implementation:** Kotlin + Forgelin-Continuous
- **Author:** Kartendsy

> Status: early MVP. Recipe APIs work; gameplay events, registry APIs, client sync and a watchdog are not implemented yet.

## Features

- Edit recipes for vanilla and modded items by item ID or Ore Dictionary entry.
- Remove recipes by output item or registry ID, regardless of which mod registered them.
- Add shaped, shapeless and furnace recipes.
- Gameplay events: player login, block break/place, entity death, item use, server tick.
- Recursive script loading with per-file error reporting.
- Sandboxed Lua runtime: no filesystem, no Java reflection, no `os.execute`.
- Module support with `require`.
- No Mixin, no coremod, no patched Minecraft classes.

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Forgelin-Continuous | 2.x (required at runtime) |
| Java | 8 |

To build from source you additionally need **JDK 25** (Gradle 9.2 runs on it) and an internet connection for the first build.

## Installation

1. Install Minecraft Forge 1.12.2.
2. Put `Forgelin-Continuous-<version>.jar` in `.minecraft/mods/`.
3. Put `MoonTweaker-<version>.jar` in `.minecraft/mods/`.
4. Start the game or server once. MoonTweaker creates `lua_scripts/` automatically.
5. Add your scripts and restart, or run `/moontweaker reload`.

## Building from source

```bash
./gradlew build
```

The reobfuscated mod jar is written to:

```
build/libs/MoonTweaker-0.1.0.jar
```

Build notes:

- Gradle 9.2 or newer is required by RetroFuturaGradle 2.0.2.
- Gradle must run on JDK 25. The Java 8 compilation toolchain is provisioned automatically by the Foojay resolver.
- LuaJ is embedded in the mod jar; Kotlin stdlib is not bundled because Forgelin provides it.

Run a development server or client:

```bash
./gradlew runServer --args nogui
./gradlew runClient
```

On the first server run, accept the Minecraft EULA in `run/eula.txt`.

## Project layout

```
build.gradle                          Gradle/RFG/Kotlin build
settings.gradle                       Plugin repositories
assets/icon.svg                       Icon source (SVG)
src/main/resources/logo.png           Rendered mod icon (256x256)
src/main/resources/mcmod.info         Forge mod metadata
src/main/resources/assets/moontweaker/lang/en_us.lang

src/main/kotlin/com/moontweaker/
  MoonTweaker.kt                      @Mod entry point
  script/ScriptManager.kt             Script discovery, sandbox, require, errors
  lua/RecipeApi.kt                    item/ore/recipes bindings
  event/RecipeRegistryHook.kt         Loads recipe scripts before the registry locks
  command/MoonTweakerCommand.kt       /moontweaker command

lua_scripts/
  startup/                            Startup scripts (no recipe access)
  server/                             Recipe scripts, loaded at recipe registry time
```

Scripts are loaded recursively in alphabetical order.

## Script lifecycle

| Folder | Loaded at | Can edit recipes? | Has world access? |
|---|---|---|---|
| `lua_scripts/startup/` | `FMLPostInitializationEvent` | No | No |
| `lua_scripts/server/` | `RegistryEvent.Register<IRecipe>` (lowest priority) | Yes | No |

Recipes must be changed **while the recipe registry is still unlocked**. That happens during `RegistryEvent.Register<IRecipe>`, which is why `server/` scripts load there. Once the server has started, recipe changes are rejected by Forge.

### Reloading

`/moontweaker reload` reloads and re-runs `lua_scripts/server/`.

- Script logic is reloaded.
- Recipe changes made after the registry has locked will fail and appear in `/moontweaker errors`. Restart the game or server to apply recipe changes.
- Lua global state is reset on reload.

## Lua API

### Logging

```lua
print("visible in the server log")
log.info("informational message")
log.warn("warning message")
log.error("error message")
```

### Items and ingredients

Item IDs follow the standard `modid:name` format, with optional metadata:

```lua
"minecraft:stone"
"minecraft:wool:14"
"modid:item:3"
```

Ore Dictionary entries use the `ore:` prefix:

```lua
"ore:ingotIron"
"ore:logWood"
```

`item(id)` validates an item and returns a table usable anywhere an item is accepted:

```lua
local stack = item("minecraft:diamond")
stack.count = 4
stack.meta = 0
```

Table form accepted by all item arguments:

```lua
{ id = "minecraft:diamond", count = 4, meta = 0 }
```

`ore(name)` is a convenience helper that returns `"ore:" .. name`:

```lua
recipes.shapeless("minecraft:stone", { ore("ingotIron") })
```

### `require`

Loads a Lua module relative to `lua_scripts/server/`. Dots map to directories and modules are cached per load.

```lua
-- lua_scripts/server/config/example.lua
return { value = 42 }
```

```lua
local cfg = require("config.example")
log.info("value = " .. cfg.value)
```

Path traversal outside `lua_scripts/server/` is rejected.

### Recipes

#### Remove recipes

```lua
-- By output item: removes every recipe producing the item
recipes.remove("minecraft:stick")

-- By exact registry ID
recipes.remove({ id = "minecraft:stick" })

-- By output, returned as a number of removed recipes
local removed = recipes.remove({ output = "minecraft:stone" })
log.info("removed " .. removed .. " recipes")
```

Removal works on the global recipe registry, so it applies to recipes registered by mods as well as vanilla.

#### Shaped recipe

```lua
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)
```

- Pattern rows may have different lengths; missing cells are treated as empty.
- Keys map a single character to an item ID or Ore Dictionary entry.

#### Shapeless recipe

```lua
recipes.shapeless(
  "minecraft:planks",
  { "minecraft:log", "minecraft:log" }
)
```

#### Furnace recipe

```lua
recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)
```

The third argument is the experience value.

### Events

Register a callback with `events.on(name, function)`. Callbacks receive one table argument; cancelable events expose `e:cancel()`.

| Event | Fields | Cancelable |
|---|---|---|
| `player.logged_in` | `player` | No |
| `block.break` | `player`, `block`, `pos` | Yes |
| `block.place` | `player`, `block`, `pos` | Yes |
| `entity.death` | `entity`, `player` (may be `nil`) | Yes |
| `item.use` | `player`, `item` | Yes |
| `server.tick` | none | No |

`player` is a table: `{ name, uuid, x, y, z, dimension }`.
`pos` is a table: `{ x, y, z }`.
`item` is a table: `{ id, count, meta }`.
`block` and `entity` are registry ID strings.

```lua
events.on("block.break", function(e)
  log.info(e.player.name .. " broke " .. e.block .. " at " .. e.pos.x .. "," .. e.pos.y .. "," .. e.pos.z)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)

events.on("server.tick", function(e)
  -- runs 20 times per second; keep it cheap
end)
```

Events are registered when `lua_scripts/server/` loads and are cleared before every reload, so a script must register its handlers again on reload.

### Full example

```lua
-- lua_scripts/server/example.lua
log.info("MoonTweaker example loaded")

-- Remove a vanilla recipe
recipes.remove("minecraft:stick")

-- Add a shaped recipe
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)

-- Add a shapeless recipe using an Ore Dictionary entry
recipes.shapeless("minecraft:stone", { "ore:ingotIron" })

-- Add a furnace recipe
recipes.smelting("minecraft:stick", "minecraft:torch", 0.5)

-- Load a module
local cfg = require("config.example")
log.info("config value: " .. tostring(cfg.value))
```

## Commands

| Command | Description |
|---|---|
| `/moontweaker reload` | Reload and re-run `lua_scripts/server/` |
| `/moontweaker errors` | Print the last script errors with file and line |

Both commands are currently available to all players. Server operators are expected to restrict access via their permissions plugin until permission checks are added.

## Sandbox

MoonTweaker deliberately exposes a small API surface. The Lua environment does **not** include:

- `io` (filesystem)
- `luajava` (Java reflection)
- `debug`
- `dofile`, `loadfile`, `load` (arbitrary bytecode/file loading)
- `package` and the default module searcher

`os` is replaced with a restricted table containing only `time`, `clock` and `difftime`.

`require` only resolves files under `lua_scripts/server/` and blocks paths that escape that directory.

Scripts run on the server thread. There is currently **no instruction budget or watchdog**, so an infinite loop in a script can hang the server. Do not run untrusted scripts.

## Known limitations

- Recipe changes cannot be applied after server start; `/moontweaker reload` cannot update recipes. Restart required.
- No item/block/fluid registration yet.
- No client recipe-book or JEI synchronization for removed recipes.
- No per-command permission node.
- Recipe APIs cover vanilla recipe types and items; mod-specific recipe types are not yet supported.

## Roadmap

1. Registry API for items, blocks and creative tabs.
2. Client recipe-book and JEI synchronization.
3. Mod recipe-type adapters and generic JSON recipes.
4. Instruction budget and watchdog for untrusted scripts.
5. EmmyLua stubs generated from the binding registry.

## Troubleshooting

**`java.lang.ClassNotFoundException: io.github.chaosunity.forgelin.KotlinAdapter`**
Forgelin-Continuous is missing from `mods/`. Install the version matching your Kotlin runtime.

**`Attempted to remove from the registry to late` / `Attempted to register to late`**
A script tried to change recipes after the registry locked. Move the code to `lua_scripts/server/` and restart the server. `/moontweaker reload` cannot bypass this Forge restriction.

**Scripts do not run**
Check that the files are `.lua`, located under `lua_scripts/server/` or `lua_scripts/startup/`, and inspect `/moontweaker errors` and the server log.

**Recipe changes are missing in JEI**
The server registry is authoritative for crafting. JEI data is client-side and is not synchronized by this MVP; a client restart or a future sync feature is required.

## License

MoonTweaker is licensed under the **GNU General Public License v3.0 or later**.
See [LICENSE](LICENSE) for the full text and [NOTICE](NOTICE) for the copyright
notice.

Additional term under GNU GPL v3 section 7(b): every copy and every modified
version must preserve the attribution

> MoonTweaker by Kartendsy

in the mod metadata, README and any user-visible credits. Modified versions must
be marked as modified, as required by section 5(a) of the license.

## Credits

- Author and copyright holder: **Kartendsy**
- Lua runtime: [LuaJ](https://github.com/luaj/luaj)
- Kotlin runtime for Forge 1.12.2: [Forgelin-Continuous](https://github.com/ChAoSUnItY/Forgelin-Continuous)
- Build toolchain: [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)

Inspired by the scripting model of CraftTweaker and KubeJS.
