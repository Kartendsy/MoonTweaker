package com.moontweaker.loot

import net.minecraftforge.event.LootTableLoadEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.Logger

class LootTableHandler(private val logger: Logger) {

    @SubscribeEvent
    fun onLootTableLoad(event: LootTableLoadEvent) {
        val applied = LootApi.apply(event.name.toString(), event.table, logger)
        if (applied > 0) {
            logger.info("MoonTweaker: applied {} loot changes to {}", applied, event.name)
        }
    }
}
