package com.moontweaker.registry

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.Logger

class RegistryHandler(private val logger: Logger) {

    @SubscribeEvent
    fun onRegisterBlocks(event: RegistryEvent.Register<Block>) {
        val blocks = RegistryApi.pendingBlocks()
        for (block in blocks) event.registry.register(block)
        if (blocks.isNotEmpty()) {
            logger.info("MoonTweaker: registered {} blocks", blocks.size)
        }
    }

    @SubscribeEvent
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        val items = RegistryApi.pendingItems()
        for (item in items) event.registry.register(item)
        if (items.isNotEmpty()) {
            logger.info("MoonTweaker: registered {} items", items.size)
        }
    }
}
