package com.lunarloot

import net.minecraftforge.event.LootTableLoadEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class LootTableHandler {

    @SubscribeEvent
    fun onLootTableLoad(event: LootTableLoadEvent) {
        val applied = LootApi.apply(event.name.toString(), event.table, LunarLoot.logger)
        if (applied > 0) {
            LunarLoot.logger.info("LunarLoot: applied {} changes to {}", applied, event.name)
        }
    }
}
