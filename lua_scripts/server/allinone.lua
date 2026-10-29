log.info("all-in-one server test loaded")

loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 2,
  max = 4,
  weight = 50,
})
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")

recipes.shaped(
  "testpack:moon_block",
  { "###", "###", "###" },
  { ["#"] = "testpack:moonsteel_ingot" }
)
recipes.shapeless("testpack:moonsteel_dust", { "testpack:moonsteel_ingot" })
recipes.smelting("testpack:moonsteel_dust", "testpack:moonsteel_ingot", 0.5)
local removed = recipes.remove({ output = "minecraft:stick" })
log.info("removed " .. removed .. " stick recipes")

local function try(name, fn)
  local ok, err = pcall(fn)
  if not ok then
    log.error(name .. ": " .. tostring(err))
  end
end

try("thermal pulverizer", function()
  moonexpansion.thermal.pulverizer.add_recipe(
    "minecraft:sugar * 2", "minecraft:apple", 2500, "minecraft:gunpowder", 25
  )
end)

try("thermal smelter", function()
  moonexpansion.thermal.smelter.add_recipe(
    "minecraft:gold_ingot", "minecraft:apple", "minecraft:sand", 3500,
    "minecraft:gold_nugget", 50
  )
end)

try("thermal crucible", function()
  moonexpansion.thermal.crucible.add_recipe("fluid:water * 250", "minecraft:apple", 1500)
end)

try("enderio sag mill", function()
  moonexpansion.enderio.sag_mill.add_recipe("minecraft:sugar * 2", "minecraft:apple", 240)
end)

try("enderio alloy smelter", function()
  moonexpansion.enderio.alloy_smelter.add_recipe(
    "minecraft:gold_ingot", "minecraft:apple", "minecraft:sand", 2400
  )
end)

try("ie crusher", function()
  moonexpansion.ie.crusher.add_recipe("minecraft:iron_dust * 3", "ore:oreIron")
end)

try("ie blast furnace", function()
  moonexpansion.ie.blast_furnace.add_recipe("minecraft:glass", "minecraft:sand", 800)
end)

try("ie arc furnace", function()
  moonexpansion.ie.arc_furnace.add_recipe("minecraft:flint * 2", "minecraft:gravel", 100, 80)
end)

events.on("server.tick", function(e)
end)
