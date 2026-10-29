package com.moonexpansion

import com.moontweaker.api.MoonTweakerApi
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLConstructionEvent
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = MoonExpansion.MODID,
    name = MoonExpansion.NAME,
    version = MoonExpansion.VERSION,
    dependencies = "required-after:moontweaker;required-after:forgelin",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
object MoonExpansion {
    const val MODID = "moonexpansion"
    const val NAME = "MoonExpansion"
    const val VERSION = "0.1.0"

    val logger: Logger = LogManager.getLogger(MODID)

    @Mod.EventHandler
    fun construction(event: FMLConstructionEvent) {
        MoonTweakerApi.registerExtension(MODID, ExpansionApi::reset, ExpansionApi::install)
    }

    @Mod.EventHandler
    fun loadComplete(event: FMLLoadCompleteEvent) {
        ExpansionApi.apply(logger)
    }
}
