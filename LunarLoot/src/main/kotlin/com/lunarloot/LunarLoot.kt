package com.lunarloot

import com.moontweaker.api.MoonTweakerApi
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = LunarLoot.MODID,
    name = LunarLoot.NAME,
    version = LunarLoot.VERSION,
    dependencies = "required-after:moontweaker;required-after:forgelin",
    modLanguageAdapter = "io.github.chaosunity.forgelin.KotlinAdapter"
)
object LunarLoot {
    const val MODID = "lunarloot"
    const val NAME = "LunarLoot"
    const val VERSION = "0.1.0"

    val logger: Logger = LogManager.getLogger(MODID)

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        MoonTweakerApi.registerExtension(MODID, LootApi::reset, LootApi::install)
        MinecraftForge.EVENT_BUS.register(LootTableHandler())
    }
}
