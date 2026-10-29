package com.moonregistry

import com.moontweaker.api.MoonTweakerApi
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLConstructionEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = MoonRegistry.MODID,
    name = MoonRegistry.NAME,
    version = MoonRegistry.VERSION,
    dependencies = "required-after:moontweaker;required-after:forgelin",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
object MoonRegistry {
    const val MODID = "moonregistry"
    const val NAME = "MoonRegistry"
    const val VERSION = "0.1.0"

    val logger: Logger = LogManager.getLogger(MODID)

    @Mod.EventHandler
    fun construction(event: FMLConstructionEvent) {
        MoonTweakerApi.registerExtension(MODID, RegistryApi::reset, RegistryApi::install)
        MinecraftForge.EVENT_BUS.register(RegistryHandler())
    }
}
