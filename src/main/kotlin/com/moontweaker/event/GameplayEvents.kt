package com.moontweaker.event

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityList
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.registry.ForgeRegistries
import org.luaj.vm2.LuaValue

class GameplayEvents {

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
        if (!EventApi.has("player.logged_in")) return
        val ctx = EventApi.context { }
        ctx.set("player", playerTable(event.player))
        EventApi.emit("player.logged_in", ctx)
    }

    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        if (!EventApi.has("block.break")) return
        val ctx = EventApi.context { event.isCanceled = true }
        ctx.set("player", playerTable(event.player))
        ctx.set("block", LuaValue.valueOf(blockId(event.state)))
        ctx.set("pos", posTable(event.pos))
        EventApi.emit("block.break", ctx)
    }

    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.PlaceEvent) {
        if (!EventApi.has("block.place")) return
        val ctx = EventApi.context { event.isCanceled = true }
        ctx.set("player", playerTable(event.player))
        ctx.set("block", LuaValue.valueOf(blockId(event.placedBlock)))
        ctx.set("pos", posTable(event.pos))
        EventApi.emit("block.place", ctx)
    }

    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        if (!EventApi.has("entity.death")) return
        val ctx = EventApi.context { event.isCanceled = true }
        ctx.set("entity", LuaValue.valueOf(entityId(event.entityLiving)))
        val source = event.source.trueSource
        ctx.set("player", if (source is EntityPlayer) playerTable(source) else LuaValue.NIL)
        EventApi.emit("entity.death", ctx)
    }

    @SubscribeEvent
    fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        if (!EventApi.has("item.use")) return
        val ctx = EventApi.context { event.isCanceled = true }
        ctx.set("player", playerTable(event.entityPlayer))
        ctx.set("item", itemTable(event.itemStack))
        EventApi.emit("item.use", ctx)
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (!EventApi.has("server.tick")) return
        EventApi.emit("server.tick", EventApi.context { })
    }

    private fun playerTable(player: EntityPlayer): LuaValue {
        val t = LuaValue.tableOf()
        t.set("name", LuaValue.valueOf(player.name))
        t.set("uuid", LuaValue.valueOf(player.uniqueID.toString()))
        t.set("x", LuaValue.valueOf(player.posX))
        t.set("y", LuaValue.valueOf(player.posY))
        t.set("z", LuaValue.valueOf(player.posZ))
        t.set("dimension", LuaValue.valueOf(player.dimension))
        return t
    }

    private fun posTable(pos: BlockPos): LuaValue {
        val t = LuaValue.tableOf()
        t.set("x", LuaValue.valueOf(pos.x))
        t.set("y", LuaValue.valueOf(pos.y))
        t.set("z", LuaValue.valueOf(pos.z))
        return t
    }

    private fun itemTable(stack: ItemStack): LuaValue {
        val t = LuaValue.tableOf()
        t.set("id", LuaValue.valueOf(stack.item.registryName?.toString() ?: "minecraft:air"))
        t.set("count", LuaValue.valueOf(stack.count))
        t.set("meta", LuaValue.valueOf(stack.metadata))
        return t
    }

    private fun blockId(state: net.minecraft.block.state.IBlockState): String =
        ForgeRegistries.BLOCKS.getKey(state.block)?.toString() ?: "minecraft:air"

    private fun entityId(entity: Entity): String =
        EntityList.getKey(entity)?.toString() ?: "unknown"
}
