package com.moontweaker.lua

import com.moontweaker.script.ScriptManager
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraft.item.crafting.IRecipe
import net.minecraft.item.crafting.ShapedRecipes
import net.minecraft.item.crafting.ShapelessRecipes
import net.minecraft.item.crafting.Ingredient
import net.minecraft.util.NonNullList
import net.minecraft.util.ResourceLocation
import net.minecraftforge.oredict.OreDictionary
import net.minecraftforge.oredict.OreIngredient
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.registries.ForgeRegistry
import org.apache.logging.log4j.Logger
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.VarArgFunction

object RecipeApi {

    fun install(g: Globals, logger: Logger, manager: ScriptManager) {
        g.set("item", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = stackTable(arg.checkjstring())
        })

        val recipes = LuaValue.tableOf()
        recipes.set("remove", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val removed = remove(arg, logger)
                logger.info("MoonTweaker: removed {} recipes", removed)
                return LuaValue.valueOf(removed)
            }
        })
        recipes.set("shaped", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addShaped(args, manager)
                return LuaValue.TRUE
            }
        })
        recipes.set("shapeless", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                addShapeless(args, manager)
                return LuaValue.TRUE
            }
        })
        recipes.set("smelting", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val input = parseStack(args.arg(1))
                val output = parseStack(args.arg(2))
                FurnaceRecipes.instance().addSmeltingRecipe(input, output, args.arg(3).optdouble(0.0).toFloat())
                return LuaValue.TRUE
            }
        })
        g.set("recipes", recipes)
    }

    private fun remove(arg: LuaValue, logger: Logger): Int {
        val id: String?
        val output: ItemStack?
        if (arg.isstring()) {
            id = null
            output = parseStack(arg.tojstring())
        } else if (arg.istable()) {
            id = arg.get("id").takeIf { !it.isnil() }?.tojstring()
            output = arg.get("output").takeIf { !it.isnil() }?.let { parseStack(it) }
            if (id == null && output == null) throw LuaError("remove() needs 'id' or 'output'")
        } else {
            throw LuaError("remove() expects item id or table")
        }

        val targets = ForgeRegistries.RECIPES.values.filter { recipe ->
            (id != null && recipe.registryName.toString() == id) ||
                (output != null && sameItem(recipe.recipeOutput, output))
        }.mapNotNull { it.registryName }
        val registry = ForgeRegistries.RECIPES as ForgeRegistry<IRecipe>
        var removed = 0
        targets.forEach {
            try {
                if (registry.remove(it) != null) removed++
            } catch (e: Exception) {
                logger.warn("MoonTweaker: cannot remove recipe {}: {}", it, e.message)
            }
        }
        return removed
    }

    private fun addShaped(args: Varargs, manager: ScriptManager) {
        val output = parseStack(args.arg(1))
        val pattern = args.arg(2).checktable()
        val keys = args.arg(3).checktable()
        val rows = (1..pattern.length()).map { pattern.get(it).checkjstring() }
        if (rows.isEmpty()) throw LuaError("shaped(): empty pattern")
        val width = rows.maxOf { it.length }
        val height = rows.size
        val ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val ch = if (col < rows[row].length) rows[row][col] else ' '
                if (ch == ' ') continue
                val key = keys.get(ch.toString())
                if (key.isnil()) throw LuaError("shaped(): missing key '$ch'")
                ingredients[row * width + col] = parseIngredient(key)
            }
        }
        val recipe = ShapedRecipes("", width, height, ingredients, output)
        recipe.registryName = ResourceLocation("moontweaker", manager.nextRecipeId())
        ForgeRegistries.RECIPES.register(recipe)
    }

    private fun addShapeless(args: Varargs, manager: ScriptManager) {
        val output = parseStack(args.arg(1))
        val list = args.arg(2).checktable()
        if (list.length() == 0) throw LuaError("shapeless(): empty ingredient list")
        val ingredients = NonNullList.withSize(list.length(), Ingredient.EMPTY)
        for (i in 1..list.length()) {
            ingredients[i - 1] = parseIngredient(list.get(i))
        }
        val recipe = ShapelessRecipes("", output, ingredients)
        recipe.registryName = ResourceLocation("moontweaker", manager.nextRecipeId())
        ForgeRegistries.RECIPES.register(recipe)
    }

    private fun stackTable(id: String): LuaValue {
        val parts = id.split(":")
        if (parts.size < 2) throw LuaError("bad item id '$id'")
        if (ForgeRegistries.ITEMS.getValue(ResourceLocation(parts[0], parts[1])) == null) {
            throw LuaError("unknown item '$id'")
        }
        val t = LuaValue.tableOf()
        t.set("id", LuaValue.valueOf(id))
        if (parts.size >= 3) t.set("meta", LuaValue.valueOf(parts[2].toIntOrNull() ?: 0))
        t.set("count", LuaValue.valueOf(1))
        return t
    }

    private fun parseStack(id: String): ItemStack = parseStack(LuaValue.valueOf(id))

    private fun parseStack(v: LuaValue): ItemStack {
        val id: String
        var count = 1
        var meta = 0
        if (v.isstring()) {
            id = v.tojstring()
        } else if (v.istable()) {
            id = v.get("id").checkjstring()
            count = v.get("count").optint(1)
            meta = v.get("meta").optint(0)
        } else {
            throw LuaError("expected item id or table")
        }

        val parts = id.split(":")
        if (parts.size < 2) throw LuaError("bad item id '$id'")
        val item: Item? = ForgeRegistries.ITEMS.getValue(ResourceLocation(parts[0], parts[1]))
            ?: throw LuaError("unknown item '$id'")
        if (parts.size >= 3) meta = parts[2].toIntOrNull() ?: meta
        return ItemStack(item, count, meta)
    }

    private fun parseIngredient(v: LuaValue): Ingredient {
        val id = when {
            v.isstring() -> v.tojstring()
            v.istable() -> v.get("id").checkjstring()
            else -> throw LuaError("expected ingredient string")
        }
        if (id.startsWith("ore:")) return OreIngredient(id.removePrefix("ore:"))
        return Ingredient.fromStacks(parseStack(id))
    }

    private fun sameItem(a: ItemStack, b: ItemStack): Boolean {
        if (a.isEmpty || b.isEmpty) return false
        if (a.item !== b.item) return false
        return b.metadata == OreDictionary.WILDCARD_VALUE || a.metadata == b.metadata
    }
}
