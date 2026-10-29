package com.moontweaker.registry

import com.moontweaker.api.MoonTweakerApi
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemBucket
import net.minecraft.item.ItemHoe
import net.minecraft.item.ItemPickaxe
import net.minecraft.item.ItemSpade
import net.minecraft.item.ItemSword
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.util.EnumHelper
import net.minecraftforge.fluids.BlockFluidClassic
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidRegistry
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.VarArgFunction

object RegistryApi {

    private const val DEFAULT_NAMESPACE = "moontweaker"
    private val NAMESPACE = Regex("^[a-z0-9_.-]+$")

    private var namespace = DEFAULT_NAMESPACE
    private val usedIds = mutableSetOf<ResourceLocation>()
    private val items = mutableListOf<Item>()
    private val blocks = mutableListOf<Block>()
    private val toolMaterials = mutableMapOf<String, Item.ToolMaterial>()
    private val armorMaterials = mutableMapOf<String, ItemArmor.ArmorMaterial>()

    private val ARMOR_PIECES = listOf(
        "helmet" to EntityEquipmentSlot.HEAD,
        "chestplate" to EntityEquipmentSlot.CHEST,
        "leggings" to EntityEquipmentSlot.LEGS,
        "boots" to EntityEquipmentSlot.FEET
    )

    fun reset() {
        namespace = DEFAULT_NAMESPACE
        usedIds.clear()
        items.clear()
        blocks.clear()
        toolMaterials.clear()
        armorMaterials.clear()
    }

    fun install(g: Globals) {
        if (MoonTweakerApi.currentStage != "registry") {
            g.set("registry", restricted())
            return
        }

        val registry = LuaValue.tableOf()
        registry.set("namespace", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val value = arg.checkjstring()
                if (!NAMESPACE.matches(value)) throw LuaError("bad namespace '$value'")
                namespace = value
                return LuaValue.NIL
            }
        })
        registry.set("item", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addItem(args.arg(1).checkjstring(), options(args.arg(2)))
                return LuaValue.NIL
            }
        })
        registry.set("material", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addMaterial(args.arg(1).checkjstring(), args.arg(2).checkjstring(), options(args.arg(3)))
                return LuaValue.NIL
            }
        })
        registry.set("block", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addBlock(args.arg(1).checkjstring(), options(args.arg(2)))
                return LuaValue.NIL
            }
        })
        registry.set("tool", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addTool(args.arg(1).checkjstring(), args.arg(2).checkjstring(), options(args.arg(3)))
                return LuaValue.NIL
            }
        })
        registry.set("armor", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addArmor(args.arg(1).checkjstring(), options(args.arg(2)))
                return LuaValue.NIL
            }
        })
        registry.set("fluid", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addFluid(args.arg(1).checkjstring(), options(args.arg(2)))
                return LuaValue.NIL
            }
        })
        g.set("registry", registry)
    }

    fun pendingItems(): List<Item> = items
    fun pendingBlocks(): List<Block> = blocks

    private fun restricted(): LuaValue {
        val table = LuaValue.tableOf()
        val fail = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs =
                throw LuaError("registry API is only available in lua_scripts/registry/")
        }
        for (name in listOf("namespace", "item", "material", "block", "tool", "armor", "fluid")) {
            table.set(name, fail)
        }
        return table
    }

    private fun addItem(name: String, opts: LuaValue) {
        val id = id(name)
        val item = Item()
        item.setRegistryName(id)
        item.setTranslationKey(id.path)
        item.setCreativeTab(CreativeTabs.MISC)
        item.setMaxStackSize(opts.get("max_stack").optint(64).coerceIn(1, 64))
        items += item
    }

    private fun addMaterial(kind: String, name: String, opts: LuaValue) {
        val suffix = when (kind) {
            "ingot", "dust", "gem" -> kind
            else -> throw LuaError("unknown material kind '$kind' (ingot, dust, gem)")
        }
        addItem("${name}_$suffix", opts)
    }

    private fun addBlock(name: String, opts: LuaValue) {
        val id = id(name)
        val block = Block(blockMaterial(opts.get("material").optjstring("rock")))
        block.setRegistryName(id)
        block.setTranslationKey(id.path)
        block.setHardness(opts.get("hardness").optdouble(2.0).toFloat())
        block.setResistance(opts.get("resistance").optdouble(10.0).toFloat())
        blocks += block

        val itemBlock = ItemBlock(block)
        itemBlock.setRegistryName(id)
        itemBlock.setCreativeTab(CreativeTabs.BUILDING_BLOCKS)
        items += itemBlock
    }

    private fun addTool(kind: String, materialName: String, opts: LuaValue) {
        val material = toolMaterial(materialName, opts)
        val tool = when (kind) {
            "pickaxe" -> Pickaxe(material)
            "axe" -> Axe(material)
            "shovel" -> Shovel(material)
            "sword" -> ItemSword(material)
            "hoe" -> ItemHoe(material)
            else -> throw LuaError("unknown tool '$kind' (pickaxe, axe, shovel, sword, hoe)")
        }
        val id = id("${materialName}_$kind")
        tool.setRegistryName(id)
        tool.setTranslationKey(id.path)
        tool.setCreativeTab(CreativeTabs.TOOLS)
        items += tool
    }

    private fun addArmor(materialName: String, opts: LuaValue) {
        val material = armorMaterial(materialName, opts)
        for ((piece, slot) in ARMOR_PIECES) {
            val armor = ItemArmor(material, 0, slot)
            val id = id("${materialName}_$piece")
            armor.setRegistryName(id)
            armor.setTranslationKey(id.path)
            armor.setCreativeTab(CreativeTabs.COMBAT)
            items += armor
        }
    }

    private fun addFluid(name: String, opts: LuaValue) {
        val id = id(name)
        val fluid = Fluid(
            "${id.namespace}_${id.path}",
            ResourceLocation(id.namespace, "blocks/${id.path}_still"),
            ResourceLocation(id.namespace, "blocks/${id.path}_flowing")
        )
        fluid.setUnlocalizedName(id.path)
        fluid.setDensity(opts.get("density").optint(1000))
        fluid.setViscosity(opts.get("viscosity").optint(1000))
        fluid.setTemperature(opts.get("temperature").optint(300))
        fluid.setLuminosity(opts.get("luminosity").optint(0))
        fluid.setColor(opts.get("color").optint(0xFFFFFF))
        fluid.setGaseous(opts.get("gaseous").optboolean(false))
        if (!FluidRegistry.registerFluid(fluid)) {
            throw LuaError("fluid '$id' is already registered")
        }

        val block = BlockFluidClassic(fluid, Material.WATER)
        block.setRegistryName(id)
        block.setTranslationKey(id.path)
        blocks += block

        val bucketId = fresh(id.namespace, "${id.path}_bucket")
        val bucket = ItemBucket(block)
        bucket.setRegistryName(bucketId)
        bucket.setTranslationKey(bucketId.path)
        bucket.setCreativeTab(CreativeTabs.MISC)
        items += bucket

        FluidRegistry.addBucketForFluid(fluid)
    }

    private fun toolMaterial(name: String, opts: LuaValue): Item.ToolMaterial {
        val key = "$namespace:$name"
        return toolMaterials.getOrPut(key) {
            EnumHelper.addToolMaterial(
                enumName("tool", name),
                opts.get("level").optint(1),
                opts.get("durability").optint(180),
                opts.get("efficiency").optdouble(4.0).toFloat(),
                opts.get("damage").optdouble(1.0).toFloat(),
                opts.get("enchantability").optint(10)
            ) ?: throw LuaError("cannot create tool material '$name'")
        }
    }

    private fun armorMaterial(name: String, opts: LuaValue): ItemArmor.ArmorMaterial {
        val key = "$namespace:$name"
        return armorMaterials.getOrPut(key) {
            val reduction = opts.get("reduction")
            val amounts = if (reduction.istable()) {
                IntArray(4) { reduction.get(it + 1).optint(0) }
            } else {
                intArrayOf(1, 3, 2, 1)
            }
            EnumHelper.addArmorMaterial(
                enumName("armor", name),
                "${namespace}_$name",
                opts.get("durability").optint(15),
                amounts,
                opts.get("enchantability").optint(10),
                SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                opts.get("toughness").optdouble(0.0).toFloat()
            ) ?: throw LuaError("cannot create armor material '$name'")
        }
    }

    private fun enumName(kind: String, name: String): String =
        "${namespace}_${kind}_$name".uppercase().replace(Regex("[^A-Z0-9_]"), "_")

    private fun blockMaterial(name: String): Material = when (name) {
        "rock" -> Material.ROCK
        "wood" -> Material.WOOD
        "iron" -> Material.IRON
        "glass" -> Material.GLASS
        "sand" -> Material.SAND
        "ground" -> Material.GROUND
        "cloth" -> Material.CLOTH
        else -> throw LuaError("unknown block material '$name'")
    }

    private fun options(value: LuaValue): LuaValue =
        if (value.isnil()) LuaValue.tableOf() else value.checktable()

    private fun id(name: String): ResourceLocation {
        val location = try {
            if (name.contains(':')) ResourceLocation(name) else ResourceLocation(namespace, name)
        } catch (e: Exception) {
            throw LuaError("bad id '$name'")
        }
        if (!usedIds.add(location)) throw LuaError("duplicate id '$location'")
        return location
    }

    private fun fresh(ns: String, path: String): ResourceLocation {
        val location = try {
            ResourceLocation(ns, path)
        } catch (e: Exception) {
            throw LuaError("bad id '$ns:$path'")
        }
        if (!usedIds.add(location)) throw LuaError("duplicate id '$location'")
        return location
    }

    private class Pickaxe(material: Item.ToolMaterial) : ItemPickaxe(material)
    private class Axe(material: Item.ToolMaterial) : ItemAxe(material, 6.0F, -3.0F)
    private class Shovel(material: Item.ToolMaterial) : ItemSpade(material)
}
