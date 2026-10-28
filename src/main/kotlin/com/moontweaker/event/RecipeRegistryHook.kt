package com.moontweaker.event

import com.moontweaker.script.ScriptManager
import net.minecraft.item.crafting.IRecipe
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class RecipeRegistryHook(private val scripts: ScriptManager) {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRegisterRecipes(event: RegistryEvent.Register<IRecipe>) {
        scripts.loadServer()
    }
}
