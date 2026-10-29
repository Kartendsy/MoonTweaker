package com.moontweaker.expansion

import com.moontweaker.api.MoonTweakerApi
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.oredict.OreDictionary
import org.apache.logging.log4j.Logger
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import java.lang.reflect.Array
import java.lang.reflect.InvocationTargetException

object ExpansionApi {

    private const val THERMAL_MODID = "thermalexpansion"
    private const val ENDERIO_MODID = "enderio"
    private const val IE_MODID = "immersiveengineering"

    private const val PULVERIZER = "cofh.thermalexpansion.util.managers.machine.PulverizerManager"
    private const val SMELTER = "cofh.thermalexpansion.util.managers.machine.SmelterManager"
    private const val CRUCIBLE = "cofh.thermalexpansion.util.managers.machine.CrucibleManager"

    private const val SAG_MILL = "crazypants.enderio.base.recipe.sagmill.SagMillRecipeManager"
    private const val ALLOY_SMELTER = "crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager"
    private const val EIO_RECIPE = "crazypants.enderio.base.recipe.Recipe"
    private const val EIO_RECIPE_INPUT = "crazypants.enderio.base.recipe.RecipeInput"
    private const val EIO_RECIPE_INPUT_INTERFACE = "crazypants.enderio.base.recipe.IRecipeInput"
    private const val EIO_RECIPE_OUTPUT = "crazypants.enderio.base.recipe.RecipeOutput"
    private const val EIO_BONUS = "crazypants.enderio.base.recipe.RecipeBonusType"
    private const val EIO_LEVEL = "crazypants.enderio.base.recipe.RecipeLevel"
    private const val EIO_LIST = "com.enderio.core.common.util.NNList"

    private const val IE_CRUSHER = "blusunrize.immersiveengineering.api.crafting.CrusherRecipe"
    private const val IE_BLAST = "blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe"
    private const val IE_ARC = "blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe"

    private class Op(val description: String, val action: () -> Unit)

    private val ops = mutableListOf<Op>()
    private var applied = false

    fun reset() {
        ops.clear()
    }

    fun apply(logger: Logger) {
        if (ops.isEmpty()) {
            applied = true
            return
        }
        var count = 0
        for (op in ops) {
            try {
                op.action()
                count++
            } catch (e: Throwable) {
                logger.error("MoonTweaker: failed to add {}: {}", op.description, rootMessage(e))
            }
        }
        ops.clear()
        applied = true
        logger.info("MoonTweaker: applied {} machine recipes", count)
    }

    fun install(g: Globals) {
        val thermalLoaded = Loader.isModLoaded(THERMAL_MODID)
        val enderioLoaded = Loader.isModLoaded(ENDERIO_MODID)
        val ieLoaded = Loader.isModLoaded(IE_MODID)

        val root = LuaValue.tableOf()

        val mods = LuaValue.tableOf()
        mods.set("thermal", LuaValue.valueOf(thermalLoaded))
        mods.set("enderio", LuaValue.valueOf(enderioLoaded))
        mods.set("ie", LuaValue.valueOf(ieLoaded))
        root.set("mods", mods)

        val thermal = LuaValue.tableOf()
        thermal.set("pulverizer", machine(thermalLoaded, "Thermal Expansion") { args ->
            val output = stack(args.arg(1))
            val inputs = inputStacks(args.arg(2), "thermal.pulverizer")
            val energy = energy(args.arg(3), 3000)
            val secondary = args.arg(4).takeIf { !it.isnil() }?.let { stack(it) }
            val chance = chance(args.arg(5))
            queue("thermal pulverizer ${name(output)}") { thermalPulverizer(output, inputs, energy, secondary, chance) }
        })
        thermal.set("smelter", machine(thermalLoaded, "Thermal Expansion") { args ->
            val output = stack(args.arg(1))
            val primary = inputStacks(args.arg(2), "thermal.smelter")
            if (!args.arg(3).isstring()) throw LuaError("thermal.smelter needs a secondary input")
            val secondary = inputStacks(args.arg(3), "thermal.smelter")
            var index = 4
            val energy = energy(args.arg(index++), 4000)
            val secondaryOutput = args.arg(index).takeIf { !it.isnil() }?.let { stack(it) }
            val chance = chance(args.arg(index + 1))
            queue("thermal smelter ${name(output)}") {
                thermalSmelter(output, primary, secondary, energy, secondaryOutput, chance)
            }
        })
        thermal.set("crucible", machine(thermalLoaded, "Thermal Expansion") { args ->
            val output = fluid(args.arg(1))
            val inputs = inputStacks(args.arg(2), "thermal.crucible")
            val energy = energy(args.arg(3), 2000)
            queue("thermal crucible ${name(output)}") { thermalCrucible(output, inputs, energy) }
        })
        root.set("thermal", thermal)

        val enderio = LuaValue.tableOf()
        enderio.set("sag_mill", machine(enderioLoaded, "Ender IO") { args ->
            val output = stack(args.arg(1))
            val inputs = inputStacks(args.arg(2), "enderio.sag_mill")
            val energy = energy(args.arg(3), 240)
            val secondary = args.arg(4).takeIf { !it.isnil() }?.let { stack(it) }
            val chance = chance(args.arg(5))
            queue("enderio sag mill ${name(output)}") { eioSagMill(output, inputs, energy, secondary, chance) }
        })
        enderio.set("alloy_smelter", machine(enderioLoaded, "Ender IO") { args ->
            val output = stack(args.arg(1))
            val first = inputStacks(args.arg(2), "enderio.alloy_smelter")
            var index = 3
            val second = if (args.arg(index).isstring()) inputStacks(args.arg(index++), "enderio.alloy_smelter") else emptyList()
            val energy = energy(args.arg(index), 2400)
            queue("enderio alloy smelter ${name(output)}") { eioAlloySmelter(output, listOf(first, second), energy) }
        })
        root.set("enderio", enderio)

        val ie = LuaValue.tableOf()
        ie.set("crusher", machine(ieLoaded, "Immersive Engineering") { args ->
            val output = stack(args.arg(1))
            val input = inputObject(args.arg(2), "ie.crusher")
            val energy = energy(args.arg(3), 3000)
            queue("ie crusher ${name(output)}") { ieCrusher(output, input, energy) }
        })
        ie.set("blast_furnace", machine(ieLoaded, "Immersive Engineering") { args ->
            val output = stack(args.arg(1))
            val input = inputObject(args.arg(2), "ie.blast_furnace")
            val time = energy(args.arg(3), 1200)
            val slag = args.arg(4).takeIf { !it.isnil() }?.let { stack(it) } ?: ItemStack.EMPTY
            queue("ie blast furnace ${name(output)}") { ieBlastFurnace(output, input, time, slag) }
        })
        ie.set("arc_furnace", machine(ieLoaded, "Immersive Engineering") { args ->
            val output = stack(args.arg(1))
            val input = inputObject(args.arg(2), "ie.arc_furnace")
            val time = energy(args.arg(3), 200)
            val energyPerTick = energy(args.arg(4), 100)
            val slag = args.arg(5).takeIf { !it.isnil() }?.let { stack(it) } ?: ItemStack.EMPTY
            queue("ie arc furnace ${name(output)}") { ieArcFurnace(output, input, time, energyPerTick, slag) }
        })
        root.set("ie", ie)

        g.set("moonexpansion", root)
    }

    private fun machine(loaded: Boolean, modName: String, handler: (Varargs) -> Unit): LuaValue {
        val table = LuaValue.tableOf()
        table.set("add_recipe", recipe(loaded, modName, handler))
        return table
    }

    private fun recipe(loaded: Boolean, modName: String, handler: (Varargs) -> Unit): LuaValue {
        return object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (MoonTweakerApi.currentStage == "registry") {
                    throw LuaError("moonexpansion: use lua_scripts/server/ or lua_scripts/startup/")
                }
                if (applied) {
                    throw LuaError("moonexpansion: machine recipes are applied at startup; restart the game")
                }
                if (!loaded) throw LuaError("MoonTweaker: $modName is not installed")
                handler(args)
                return LuaValue.NIL
            }
        }
    }

    private fun queue(description: String, action: () -> Unit) {
        ops += Op(description, action)
    }

    private fun thermalPulverizer(
        output: ItemStack,
        inputs: List<ItemStack>,
        energy: Int,
        secondary: ItemStack?,
        chance: Int
    ) {
        for (input in inputs) {
            val result = if (secondary == null) {
                invokeStatic(PULVERIZER, "addRecipe", energy, input, output)
            } else {
                invokeStatic(PULVERIZER, "addRecipe", energy, input, output, secondary, chance)
            }
            checkAdded(result, "thermal pulverizer ${name(input)}")
        }
    }

    private fun thermalSmelter(
        output: ItemStack,
        primaryInputs: List<ItemStack>,
        secondaryInputs: List<ItemStack>,
        energy: Int,
        secondaryOutput: ItemStack?,
        chance: Int
    ) {
        for (primary in primaryInputs) {
            for (secondary in secondaryInputs) {
                val result = invokeStatic(
                    SMELTER, "addRecipe", energy, primary, secondary, output, secondaryOutput ?: ItemStack.EMPTY, chance
                )
                checkAdded(result, "thermal smelter ${name(primary)} + ${name(secondary)}")
            }
        }
    }

    private fun thermalCrucible(output: FluidStack, inputs: List<ItemStack>, energy: Int) {
        for (input in inputs) {
            val result = invokeStatic(CRUCIBLE, "addRecipe", energy, input, output)
            checkAdded(result, "thermal crucible ${name(input)}")
        }
    }

    private fun checkAdded(result: Any?, description: String) {
        if (result == null) {
            throw IllegalStateException("$description: recipe already exists or arguments are invalid")
        }
    }

    private fun eioSagMill(
        output: ItemStack,
        inputs: List<ItemStack>,
        energy: Int,
        secondary: ItemStack?,
        chance: Int
    ) {
        val recipeInputClass = Class.forName(EIO_RECIPE_INPUT)
        val recipeInputInterface = Class.forName(EIO_RECIPE_INPUT_INTERFACE)
        val recipeOutputClass = Class.forName(EIO_RECIPE_OUTPUT)
        val recipeClass = Class.forName(EIO_RECIPE)
        val bonusClass = Class.forName(EIO_BONUS)
        val levelClass = Class.forName(EIO_LEVEL)
        val bonus = bonusClass.getField("CHANCE_ONLY").get(null)
        val level = levelClass.getField("IGNORE").get(null)

        for (input in inputs) {
            val recipeInput = recipeInputClass.getConstructor(ItemStack::class.java).newInstance(input)
            val outputs = Array.newInstance(recipeOutputClass, if (secondary == null) 1 else 2)

            Array.set(
                outputs, 0,
                recipeOutputClass.getConstructor(ItemStack::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                    .newInstance(output, 1.0f, 0.0f)
            )
            if (secondary != null) {
                Array.set(
                    outputs, 1,
                    recipeOutputClass.getConstructor(ItemStack::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                        .newInstance(secondary, chance / 100.0f, 0.0f)
                )
            }

            val recipe = recipeClass
                .getConstructor(recipeInputInterface, Int::class.javaPrimitiveType, bonusClass, levelClass, outputs.javaClass)
                .newInstance(recipeInput, energy, bonus, level, outputs)
            val manager = Class.forName(SAG_MILL).getMethod("getInstance").invoke(null)
                ?: throw IllegalStateException("SagMillRecipeManager unavailable")
            manager.javaClass.getMethod("addRecipe", recipeClass).invoke(manager, recipe)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun eioAlloySmelter(output: ItemStack, slots: List<List<ItemStack>>, energy: Int) {
        val recipeInputClass = Class.forName(EIO_RECIPE_INPUT)
        val levelClass = Class.forName(EIO_LEVEL)
        val listClass = Class.forName(EIO_LIST)
        val level = levelClass.getField("IGNORE").get(null)
        val manager = Class.forName(ALLOY_SMELTER).getMethod("getInstance").invoke(null)
            ?: throw IllegalStateException("AlloyRecipeManager unavailable")

        for (combination in combinations(slots)) {
            val list = listClass.getConstructor().newInstance() as MutableList<Any>
            combination.forEachIndexed { index, stack ->
                val input = recipeInputClass
                    .getConstructor(
                        ItemStack::class.java,
                        java.lang.Boolean.TYPE,
                        Float::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    .newInstance(stack, stack.itemDamage != OreDictionary.WILDCARD_VALUE, 1.0f, index)
                list.add(input)
            }

            manager.javaClass.getMethod(
                "addRecipe",
                java.lang.Boolean.TYPE,
                listClass,
                ItemStack::class.java,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                levelClass
            ).invoke(manager, false, list, output, energy, 0.0f, level)
        }
    }

    private fun combinations(slots: List<List<ItemStack>>): List<List<ItemStack>> {
        val present = slots.filter { it.isNotEmpty() }
        if (present.isEmpty()) return emptyList()
        var result = listOf(emptyList<ItemStack>())
        for (slot in present) {
            result = result.flatMap { prefix -> slot.map { prefix + it } }
        }
        return result
    }

    private fun ieCrusher(output: ItemStack, input: Any, energy: Int) {
        invokeStatic(IE_CRUSHER, "addRecipe", output, input, energy)
    }

    private fun ieBlastFurnace(output: ItemStack, input: Any, time: Int, slag: ItemStack) {
        invokeStatic(IE_BLAST, "addRecipe", output, input, time, slag)
    }

    private fun ieArcFurnace(output: ItemStack, input: Any, time: Int, energyPerTick: Int, slag: ItemStack) {
        invokeStatic(IE_ARC, "addRecipe", output, input, slag, time, energyPerTick, emptyArray<Any>())
    }

    private fun invokeStatic(className: String, methodName: String, vararg args: Any?): Any? {
        val type = Class.forName(className)
        val method = type.methods.firstOrNull { it.name == methodName && it.parameterCount == args.size }
            ?: throw IllegalStateException("$className.$methodName/${args.size} not found")
        try {
            return method.invoke(null, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun stack(value: LuaValue): ItemStack {
        val text = value.checkjstring().trim()
        val parts = text.split("*", limit = 2)
        val id = parts[0].trim()
        val count = if (parts.size == 2) {
            parts[1].trim().toIntOrNull() ?: throw LuaError("bad count in '$text'")
        } else {
            1
        }
        if (count < 1 || count > 64) throw LuaError("bad count $count in '$text'")

        val idParts = id.split(":")
        if (idParts.size < 2) throw LuaError("bad item id '$id'")
        val item = ForgeRegistries.ITEMS.getValue(ResourceLocation(idParts[0], idParts[1]))
            ?: throw LuaError("unknown item '$id'")
        val meta = if (idParts.size >= 3) {
            idParts[2].toIntOrNull() ?: throw LuaError("bad metadata in '$id'")
        } else {
            0
        }
        return ItemStack(item, count, meta)
    }

    private fun inputStacks(value: LuaValue, label: String): List<ItemStack> {
        val text = value.checkjstring().trim()
        if (text.startsWith("ore:")) {
            val name = text.removePrefix("ore:")
            val ores = OreDictionary.getOres(name, false)
            if (ores.isEmpty()) throw LuaError("$label: unknown ore '$name'")
            return ores.map { ItemStack(it.item, 1, it.metadata) }
        }
        return listOf(stack(value))
    }

    private fun inputObject(value: LuaValue, label: String): Any {
        val text = value.checkjstring().trim()
        if (text.startsWith("ore:")) {
            val name = text.removePrefix("ore:")
            if (OreDictionary.getOres(name, false).isEmpty()) throw LuaError("$label: unknown ore '$name'")
            return name
        }
        return stack(value)
    }

    private fun fluid(value: LuaValue): FluidStack {
        val text = value.checkjstring().trim()
        if (!text.startsWith("fluid:")) throw LuaError("expected fluid:<name> [* amount]")
        val parts = text.removePrefix("fluid:").split("*", limit = 2)
        val name = parts[0].trim()
        val amount = if (parts.size == 2) {
            parts[1].trim().toIntOrNull() ?: throw LuaError("bad fluid amount in '$text'")
        } else {
            1000
        }
        if (amount < 1) throw LuaError("bad fluid amount $amount")
        val registered = FluidRegistry.getFluid(name) ?: throw LuaError("unknown fluid '$name'")
        return FluidStack(registered, amount)
    }

    private fun energy(value: LuaValue, default: Int): Int {
        if (value.isnil()) return default
        if (!value.isnumber()) throw LuaError("expected a number, got '${value.tojstring()}'")
        val number = value.toint()
        if (number < 1) throw LuaError("value must be at least 1")
        return number
    }

    private fun chance(value: LuaValue): Int {
        val number = value.optint(100)
        if (number < 0 || number > 100) throw LuaError("chance must be between 0 and 100")
        return number
    }

    private fun name(stack: ItemStack): String =
        stack.item.registryName?.toString() ?: "unknown"

    private fun name(stack: FluidStack): String =
        stack.fluid.name

    private fun rootMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current.message ?: current.toString()
    }
}
