log.info("MoonTweaker example loaded")

-- items: "mod:item" or "mod:item:meta", ore dictionary: "ore:ingotIron"
-- recipes.remove("minecraft:stick")
-- recipes.remove({ output = "minecraft:stone" })
-- recipes.shaped("minecraft:diamond_block", { "###", "###", "###" }, { ["#"] = "minecraft:diamond" })
-- recipes.shapeless("minecraft:planks", { "minecraft:log" })
-- recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)

local cfg = require("config.example")
log.info("example config value: " .. tostring(cfg.value))
