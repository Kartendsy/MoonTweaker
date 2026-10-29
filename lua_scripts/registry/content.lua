registry.namespace("testpack")

registry.material("ingot", "moonsteel")
registry.material("dust", "moonsteel")
registry.item("moon_token", { max_stack = 16 })

registry.block("moon_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})

registry.tool("pickaxe", "moonsteel", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})

registry.armor("moonsteel", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
})

registry.fluid("moon_oil", {
  density = 900,
  viscosity = 1500,
  color = 0x1A1A1A,
})

log.info("all-in-one registry test loaded")
