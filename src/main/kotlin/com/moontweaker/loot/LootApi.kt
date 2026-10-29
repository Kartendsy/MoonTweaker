package com.moontweaker.loot

import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import net.minecraft.world.storage.loot.LootEntry
import net.minecraft.world.storage.loot.LootEntryItem
import net.minecraft.world.storage.loot.LootPool
import net.minecraft.world.storage.loot.LootTable
import net.minecraft.world.storage.loot.RandomValueRange
import net.minecraft.world.storage.loot.conditions.LootCondition
import net.minecraft.world.storage.loot.functions.LootFunction
import net.minecraft.world.storage.loot.functions.SetCount
import net.minecraft.world.storage.loot.functions.SetMetadata
import net.minecraftforge.fml.common.registry.ForgeRegistries
import org.apache.logging.log4j.Logger
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import java.lang.reflect.Field

object LootApi {

    private sealed class Op(val table: String) {
        class Add(
            table: String,
            val item: Item,
            val min: Int,
            val max: Int,
            val weight: Int,
            val quality: Int,
            val meta: Int
        ) : Op(table)

        class Remove(table: String, val item: Item) : Op(table)
        class Clear(table: String) : Op(table)
    }

    private val ops = mutableListOf<Op>()
    private var poolCounter = 0

    fun reset() {
        ops.clear()
        poolCounter = 0
    }

    fun install(g: Globals) {
        val loot = LuaValue.tableOf()
        loot.set("add", object : TwoArgFunction() {
            override fun call(table: LuaValue, spec: LuaValue): LuaValue {
                ops.add(parseAdd(tableName(table), spec))
                return LuaValue.NIL
            }
        })
        loot.set("remove", object : TwoArgFunction() {
            override fun call(table: LuaValue, item: LuaValue): LuaValue {
                ops.add(Op.Remove(tableName(table), parseItem(item)))
                return LuaValue.NIL
            }
        })
        loot.set("clear", object : OneArgFunction() {
            override fun call(table: LuaValue): LuaValue {
                ops.add(Op.Clear(tableName(table)))
                return LuaValue.NIL
            }
        })
        g.set("loot", loot)
    }

    fun apply(tableName: String, table: LootTable, logger: Logger): Int {
        var applied = 0
        for (op in ops) {
            if (op.table != tableName) continue
            try {
                when (op) {
                    is Op.Add -> add(table, op)
                    is Op.Remove -> remove(table, op.item)
                    is Op.Clear -> pools(table).clear()
                }
                applied++
            } catch (e: Throwable) {
                logger.error("MoonTweaker: failed to change loot table {}: {}", tableName, e.toString())
            }
        }
        return applied
    }

    private fun parseAdd(table: String, spec: LuaValue): Op.Add {
        val id: String
        var min = 1
        var max = 1
        var weight = 1
        var quality = 0
        var meta = 0
        var hasMeta = false

        if (spec.isstring()) {
            id = spec.tojstring()
        } else if (spec.istable()) {
            val item = spec.get("item")
            if (item.isnil()) throw LuaError("loot.add(): 'item' is required")
            id = item.checkjstring()
            min = spec.get("min").optint(1)
            max = spec.get("max").optint(min)
            weight = spec.get("weight").optint(1)
            quality = spec.get("quality").optint(0)
            val metaValue = spec.get("meta")
            meta = metaValue.optint(0)
            hasMeta = !metaValue.isnil()
        } else {
            throw LuaError("loot.add(): expected item id or table")
        }

        val (item, idMeta) = parseId(id)
        if (!hasMeta && idMeta != null) {
            meta = idMeta
            hasMeta = true
        }
        if (min < 1) throw LuaError("loot.add(): 'min' must be at least 1")
        if (max < min) throw LuaError("loot.add(): 'max' must be >= 'min'")
        if (weight < 0) throw LuaError("loot.add(): 'weight' must be >= 0")
        return Op.Add(table, item, min, max, weight, quality, if (hasMeta) meta else 0)
    }

    private fun add(table: LootTable, op: Op.Add) {
        val functions = mutableListOf<LootFunction>()
        functions.add(SetCount(conditions(), RandomValueRange(op.min.toFloat(), op.max.toFloat())))
        if (op.meta != 0) {
            functions.add(SetMetadata(conditions(), RandomValueRange(op.meta.toFloat())))
        }

        val id = "moontweaker_" + poolCounter++
        val entry = LootEntryItem(
            op.item,
            op.weight,
            op.quality,
            functions.toTypedArray(),
            conditions(),
            id
        )
        table.addPool(
            LootPool(arrayOf<LootEntry>(entry), conditions(), RandomValueRange(1f), RandomValueRange(0f), id)
        )
    }

    private fun remove(table: LootTable, item: Item) {
        val pools = pools(table)
        for (pool in pools) {
            entries(pool).removeIf { it is LootEntryItem && entryItemField.get(it) === item }
        }
        pools.removeIf { entries(it).isEmpty() }
    }

    private fun conditions() = emptyArray<LootCondition>()

    private fun tableName(value: LuaValue): String {
        val name = value.checkjstring()
        if (name.isBlank() || !name.contains(':')) throw LuaError("bad loot table name '$name'")
        return name
    }

    private fun parseItem(value: LuaValue): Item {
        val id = when {
            value.isstring() -> value.tojstring()
            value.istable() -> value.get("item").checkjstring()
            else -> throw LuaError("loot.remove(): expected item id")
        }
        return parseId(id).first
    }

    private fun parseId(id: String): Pair<Item, Int?> {
        val parts = id.split(":")
        if (parts.size < 2) throw LuaError("bad item id '$id'")
        val item = ForgeRegistries.ITEMS.getValue(ResourceLocation(parts[0], parts[1]))
            ?: throw LuaError("unknown item '$id'")
        val meta = if (parts.size >= 3) {
            parts[2].toIntOrNull() ?: throw LuaError("bad metadata in '$id'")
        } else {
            null
        }
        return item to meta
    }

    private val poolsField = findField(LootTable::class.java, "pools", "field_186466_c")
    private val poolEntriesField = findField(LootPool::class.java, "lootEntries", "field_186453_a")
    private val entryItemField = findField(LootEntryItem::class.java, "item", "field_186368_a")

    private fun findField(type: Class<*>, deobf: String, srg: String): Field {
        val field = try {
            type.getDeclaredField(deobf)
        } catch (e: NoSuchFieldException) {
            type.getDeclaredField(srg)
        }
        field.isAccessible = true
        return field
    }

    @Suppress("UNCHECKED_CAST")
    private fun pools(table: LootTable): MutableList<LootPool> =
        poolsField.get(table) as MutableList<LootPool>

    @Suppress("UNCHECKED_CAST")
    private fun entries(pool: LootPool): MutableList<LootEntry> =
        poolEntriesField.get(pool) as MutableList<LootEntry>
}
