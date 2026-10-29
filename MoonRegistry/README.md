# MoonRegistry

Content registration for **Minecraft 1.12.2 / Forge**, built as an extension of
[MoonTweaker](../README.md).

MoonRegistry adds a `registry` API to MoonTweaker's Lua runtime so you can
register items, blocks, tools, armor and fluids from a startup script instead of
writing a mod.

- **Mod ID:** `moonregistry`
- **Requires:** MoonTweaker, Forgelin-Continuous, Forge 14.23.5.2847+
- **Author:** Kartendsy
- **License:** GPL-3.0-or-later (see [LICENSE](LICENSE) and [NOTICE](NOTICE))

## Installation

1. Install Forge 1.12.2.
2. Put `Forgelin-Continuous-<version>.jar` in `mods/`.
3. Put `MoonTweaker-<version>.jar` and `MoonRegistry-<version>.jar` in `mods/`.
4. Write registry scripts under `lua_scripts/registry/`.
5. Restart the game or server.

## Building

```bash
./gradlew build
```

Output: `build/libs/MoonRegistry-0.1.0.jar`.

The project compiles against `libs/MoonTweaker-0.1.0-dev.jar`; replace that file
when the MoonTweaker API changes. Gradle runs on JDK 25 and provisions a Java 8
toolchain automatically.

## When scripts run

Registry scripts live in `lua_scripts/registry/` and load during
`FMLPreInitializationEvent`, before Forge fires its registry events.
`MoonRegistry` then registers the queued content during
`RegistryEvent.Register<Block>` and `RegistryEvent.Register<Item>`.

The `registry` table is only usable in this stage. In `lua_scripts/startup/` and
`lua_scripts/server/` its functions throw an error. Registry scripts cannot be
reloaded with `/moontweaker reload`; restart the game.

## Namespace

Every ID is prefixed with the current namespace, which defaults to
`moonregistry`. Change it before registering anything:

```lua
registry.namespace("my_mod")
```

IDs may also be fully qualified (`"other_mod:custom_item"`), in which case the
prefix is not added.

## Items

### `registry.item(name, opts)`

Registers a plain item.

```lua
registry.item("ruby", { max_stack = 64 })
```

| Option | Type | Default |
|---|---|---|
| `max_stack` | integer | `64` (clamped to 1..64) |

### `registry.material(kind, name, opts)`

Registers a material item with a conventional suffix. Supported kinds:
`ingot`, `dust` and `gem`.

```lua
registry.material("ingot", "copper")   -- my_mod:copper_ingot
registry.material("dust", "copper")    -- my_mod:copper_dust
registry.material("gem", "ruby")       -- my_mod:ruby_gem
```

`opts` accepts the same fields as `registry.item`.

## Blocks

### `registry.block(name, opts)`

Registers a decorative block together with its `ItemBlock`, so it can be held
and placed in survival.

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

Supported materials: `rock`, `wood`, `iron`, `glass`, `sand`, `ground`, `cloth`.

## Tools

### `registry.tool(kind, material, opts)`

Creates a tool material and one tool item. Kinds: `pickaxe`, `axe`, `shovel`,
`sword`, `hoe`.

```lua
registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})
```

| Option | Type | Default |
|---|---|---|
| `level` | integer | `1` |
| `durability` | integer | `180` |
| `efficiency` | number | `4.0` |
| `damage` | number | `1.0` |
| `enchantability` | integer | `10` |

The first call for a material defines its stats; later tools with the same
material reuse them. Items are named `<material>_<kind>`, for example
`my_mod:copper_pickaxe`.

## Armor

### `registry.armor(material, opts)`

Creates an armor material and registers a full set: helmet, chestplate,
leggings and boots.

```lua
registry.armor("copper", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
  toughness = 0.0,
})
```

| Option | Type | Default |
|---|---|---|
| `durability` | integer | `15` (durability multiplier) |
| `reduction` | table of 4 integers | `{1, 3, 2, 1}` (helmet, chest, legs, boots) |
| `enchantability` | integer | `10` |
| `toughness` | number | `0.0` |

Items are named `<material>_helmet`, `<material>_chestplate`,
`<material>_leggings` and `<material>_boots`.

## Fluids

### `registry.fluid(name, opts)`

Registers a fluid, its world block and a bucket item.

```lua
registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  temperature = 300,
  luminosity = 0,
  color = 0x1A1A1A,
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

The block uses the same ID as the fluid; the bucket is `<name>_bucket`.

## Full example

```lua
-- lua_scripts/registry/content.lua
registry.namespace("my_mod")

registry.material("ingot", "copper")
registry.material("dust", "copper")
registry.material("gem", "ruby")

registry.item("mystery_token", { max_stack = 16 })

registry.block("copper_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})

registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})

registry.armor("copper", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
})

registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  color = 0x1A1A1A,
})

log.info("content registered")
```

## Assets and translations

MoonRegistry registers the game objects only. Models, textures and language
entries still belong to a resource pack, using the same namespace as the IDs:

```
assets/<namespace>/models/item/<id>.json
assets/<namespace>/textures/items/<id>.png
assets/<namespace>/blockstates/<id>.json
assets/<namespace>/models/block/<id>.json
assets/<namespace>/textures/blocks/<id>.png
assets/<namespace>/lang/en_us.lang
```

Conventions applied by the extension:

- Items, tools and armor use `item/generated` (tools look better with
  `item/handheld`).
- Blocks need a blockstate, a block model and an item model.
- Armor worn textures live at
  `assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_1.png`
  and `_layer_2.png`.
- Fluids use `textures/blocks/<id>_still.png` and `textures/blocks/<id>_flowing.png`.
- Language keys are `item.<id>.name`, `tile.<id>.name` and `fluid.<id>`.

Without assets the content works on the server but renders with missing
textures on the client.

## Limitations

- Content can only be registered once per game start; restart after editing.
- No recipes are generated automatically; use MoonTweaker's `recipes` API in
  `lua_scripts/server/`.
- No ore dictionary entries are added.
- No custom block models, block states, tile entities or harvest levels.
- Creative tabs are fixed: items and buckets go to Misc, blocks to Building
  Blocks, tools to Tools, armor to Combat.
