package com.moontweaker

import com.moontweaker.command.MoonTweakerCommand
import com.moontweaker.event.GameplayEvents
import com.moontweaker.event.RecipeRegistryHook
import com.moontweaker.expansion.ExpansionApi
import com.moontweaker.loot.LootTableHandler
import com.moontweaker.registry.RegistryHandler
import com.moontweaker.script.ScriptManager
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = MoonTweaker.MODID,
    name = MoonTweaker.NAME,
    version = MoonTweaker.VERSION,
    dependencies = "required-after:forgelin",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
object MoonTweaker {
    const val MODID = "moontweaker"
    const val NAME = "MoonTweaker"
    const val VERSION = "0.1.0"

    val logger: Logger = LogManager.getLogger(MODID)
    val scripts = ScriptManager(logger)

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        scripts.loadRegistry()
        MinecraftForge.EVENT_BUS.register(RegistryHandler(logger))
        MinecraftForge.EVENT_BUS.register(LootTableHandler(logger))
        MinecraftForge.EVENT_BUS.register(RecipeRegistryHook(scripts))
        MinecraftForge.EVENT_BUS.register(GameplayEvents())
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        scripts.loadStartup()
    }

    @Mod.EventHandler
    fun loadComplete(event: FMLLoadCompleteEvent) {
        ExpansionApi.apply(logger)
    }

    @Mod.EventHandler
    fun serverStarting(event: FMLServerStartingEvent) {
        event.registerServerCommand(MoonTweakerCommand(scripts))
    }
}
