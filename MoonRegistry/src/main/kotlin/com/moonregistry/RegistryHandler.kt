package com.moonregistry

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.event.RegistryEvent

class RegistryHandler {

    @SubscribeEvent
    fun onRegisterBlocks(event: RegistryEvent.Register<Block>) {
        val blocks = RegistryApi.pendingBlocks()
        for (block in blocks) event.registry.register(block)
        if (blocks.isNotEmpty()) {
            MoonRegistry.logger.info("MoonRegistry: registered {} blocks", blocks.size)
        }
    }

    @SubscribeEvent
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        val items = RegistryApi.pendingItems()
        for (item in items) event.registry.register(item)
        if (items.isNotEmpty()) {
            MoonRegistry.logger.info("MoonRegistry: registered {} items", items.size)
        }
    }
}
