# MoonExpansion

Machine recipe support for **Minecraft 1.12.2 / Forge**, built as an extension of
[MoonTweaker](../README.md).

MoonExpansion adds a `moonexpansion` API to MoonTweaker's Lua runtime so scripts
can add recipes to popular tech and magic machines without writing a mod.

| Mod | Machines |
|---|---|
| Thermal Expansion | Pulverizer, Induction Smelter, Magma Crucible |
| Ender IO | SAG Mill, Alloy Smelter |
| Immersive Engineering | Crusher, Blast Furnace, Arc Furnace |

- **Mod ID:** `moonexpansion`
- **Requires:** MoonTweaker, Forgelin-Continuous, Forge 14.23.5.2847+
- **Optional:** Thermal Expansion, Ender IO, Immersive Engineering
- **Author:** Kartendsy
- **License:** GPL-3.0-or-later (see [LICENSE](LICENSE) and [NOTICE](NOTICE))

## Installation

1. Install Forge 1.12.2.
2. Put `Forgelin-Continuous-<version>.jar` in `mods/`.
3. Put `MoonTweaker-<version>.jar` and `MoonExpansion-<version>.jar` in `mods/`.
4. Put the machine mods you want to use in `mods/` as well.
5. Write scripts under `lua_scripts/server/` or `lua_scripts/startup/`.
6. Restart the game or server.

MoonExpansion works with any combination of the three supported mods. Calling a
machine API for a mod that is not installed raises a Lua error.

## Building

```bash
./gradlew build
```

Output: `build/libs/MoonExpansion-0.1.0.jar`.

The project compiles against `libs/MoonTweaker-0.1.0-dev.jar`; replace that file
when the MoonTweaker API changes. MoonExpansion does **not** compile against
Thermal Expansion, Ender IO or Immersive Engineering. Their recipe managers are
called through reflection, using the class names of their 1.12.2 releases.
Gradle runs on JDK 25 and provisions a Java 8 toolchain automatically.

Tested with:

| Mod | Version |
|---|---|
| Thermal Expansion | `1.12.2-5.5.4.43` |
| Ender IO | `1.12.2-5.3.72` |
| Immersive Engineering | `0.12-92-559` |

## When scripts run

| Folder | Loaded at |
|---|---|
| `lua_scripts/server/` | `RegistryEvent.Register<IRecipe>` |
| `lua_scripts/startup/` | `FMLPostInitializationEvent` |

Recipes are queued while the script runs and applied once at
`FMLLoadCompleteEvent`, after every mod has finished initialization. This makes
the machine recipe managers available even when another mod loads later than
MoonExpansion.

`moonexpansion` is rejected in `lua_scripts/registry/`. Use `server/` or
`startup/`.

## Checking installed mods

```lua
log.info("thermal=" .. tostring(moonexpansion.mods.thermal))
log.info("enderio=" .. tostring(moonexpansion.mods.enderio))
log.info("ie=" .. tostring(moonexpansion.mods.ie))
```

Each field is a boolean.

## Input and output strings

Machine functions take the same string forms as the rest of MoonTweaker:

| Form | Meaning |
|---|---|
| `"minecraft:iron_ingot"` | item, metadata 0 |
| `"minecraft:wool:14"` | item with metadata |
| `"minecraft:iron_dust * 3"` | item with count 1..64 |
| `"ore:oreIron"` | Ore Dictionary entry (inputs only) |
| `"fluid:water * 1000"` | fluid with amount (crucible output) |

`ore:` inputs are expanded before the recipe is added:

- Single-input machines (Pulverizer, Crucible, SAG Mill) get one recipe per
  matching item.
- Two-input machines (Induction Smelter, Alloy Smelter) get one recipe per
  combination of matching items.
- Immersive Engineering keeps `ore:` inputs as an Ore Dictionary reference, so
  any item registered under that name works in the machine.

## Thermal Expansion

### `moonexpansion.thermal.pulverizer.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.thermal.pulverizer.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  2500,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default |
|---|---|
| `energy` | `3000` RF |
| `secondary_output` | none |
| `chance` | `100` (0..100) |

### `moonexpansion.thermal.smelter.add_recipe(output, primary_input, secondary_input, energy?, secondary_output?, chance?)`

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

Both inputs are required; Thermal Expansion has no one-input smelter recipe.

| Parameter | Default |
|---|---|
| `energy` | `4000` RF |
| `secondary_output` | none |
| `chance` | `100` (0..100) |

### `moonexpansion.thermal.crucible.add_recipe(fluid_output, input, energy?)`

```lua
moonexpansion.thermal.crucible.add_recipe(
  "fluid:water * 250",
  "minecraft:apple",
  1500
)
```

| Parameter | Default |
|---|---|
| `energy` | `2000` RF |
| fluid amount | `1000` mB |

Thermal Expansion rejects duplicate inputs. Such recipes are reported in the
log as failed and are not added.

## Ender IO

### `moonexpansion.enderio.sag_mill.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.enderio.sag_mill.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  240,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default |
|---|---|
| `energy` | `240` RF |
| `secondary_output` | none |
| `chance` | `100` (0..100) |

Recipes use the `CHANCE_ONLY` bonus type and `IGNORE` recipe level, so they can
be made in any SAG Mill.

### `moonexpansion.enderio.alloy_smelter.add_recipe(output, input, second_input?, energy?)`

```lua
moonexpansion.enderio.alloy_smelter.add_recipe(
  "minecraft:gold_ingot",
  "minecraft:apple",
  "minecraft:sand",
  2400
)
```

| Parameter | Default |
|---|---|
| `energy` | `2400` RF |

At most two input arguments are supported. Each may be an `ore:` entry, in which
case one recipe is added per combination.

## Immersive Engineering

### `moonexpansion.ie.crusher.add_recipe(output, input, energy?)`

```lua
moonexpansion.ie.crusher.add_recipe(
  "minecraft:iron_dust * 3",
  "ore:oreIron"
)
```

`energy` is the total RF cost, default `3000`. Process time stays at the
Immersive Engineering default.

### `moonexpansion.ie.blast_furnace.add_recipe(output, input, time?, slag?)`

```lua
moonexpansion.ie.blast_furnace.add_recipe(
  "minecraft:glass",
  "minecraft:sand",
  800
)
```

| Parameter | Default |
|---|---|
| `time` | `1200` ticks |
| `slag` | none |

### `moonexpansion.ie.arc_furnace.add_recipe(output, input, time?, energy_per_tick?, slag?)`

```lua
moonexpansion.ie.arc_furnace.add_recipe(
  "minecraft:flint * 2",
  "minecraft:gravel",
  100,
  80
)
```

| Parameter | Default |
|---|---|
| `time` | `200` ticks |
| `energy_per_tick` | `100` RF/t |
| `slag` | none |

## Full example

```lua
-- lua_scripts/server/machines.lua
local function try(name, fn)
  local ok, err = pcall(fn)
  if not ok then
    log.error(name .. ": " .. tostring(err))
  end
end

try("pulverizer", function()
  moonexpansion.thermal.pulverizer.add_recipe(
    "minecraft:sugar * 2", "minecraft:apple", 2500, "minecraft:gunpowder", 25
  )
end)

try("smelter", function()
  moonexpansion.thermal.smelter.add_recipe(
    "minecraft:gold_ingot", "minecraft:apple", "minecraft:sand", 3500,
    "minecraft:gold_nugget", 50
  )
end)

try("crucible", function()
  moonexpansion.thermal.crucible.add_recipe(
    "fluid:water * 250", "minecraft:apple", 1500
  )
end)

try("sag_mill", function()
  moonexpansion.enderio.sag_mill.add_recipe(
    "minecraft:sugar * 2", "minecraft:apple", 240
  )
end)

try("alloy_smelter", function()
  moonexpansion.enderio.alloy_smelter.add_recipe(
    "minecraft:brass_ingot", "ore:ingotCopper", "ore:ingotZinc", 2400
  )
end)

try("crusher", function()
  moonexpansion.ie.crusher.add_recipe(
    "minecraft:iron_dust * 3", "ore:oreIron"
  )
end)

try("blast_furnace", function()
  moonexpansion.ie.blast_furnace.add_recipe(
    "minecraft:glass", "minecraft:sand", 800
  )
end)

try("arc_furnace", function()
  moonexpansion.ie.arc_furnace.add_recipe(
    "minecraft:flint * 2", "minecraft:gravel", 100, 80
  )
end)
```

The `pcall` wrapper is optional. Without it, the first failing call stops that
script and MoonTweaker records the error under `/moontweaker errors`.

## Limitations

- Recipes can only be added while the game is starting. `/moontweaker reload`
  cannot apply machine recipes; it raises an error and the game must be
  restarted.
- Only adding is supported. Existing machine recipes cannot be removed or
  changed.
- Thermal Expansion rejects duplicate inputs; the log reports these as failed.
- Immersive Engineering and Ender IO accept duplicate recipes, so adding the
  same recipe twice may add it twice.
- Recipe managers are called by reflection. Different major versions of a
  supported mod may rename classes or methods; a mismatch is logged as a failed
  recipe instead of crashing the game.
- Immersive Engineering crusher recipes use the machine's default process time;
  only the RF cost can be set.
