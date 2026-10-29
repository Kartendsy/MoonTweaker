package com.moontweaker.api

/**
 * Shared state for MoonTweaker's script stages.
 *
 * All Lua APIs are built into MoonTweaker. This object only tracks which
 * `lua_scripts` folder is currently being loaded, so stage-specific APIs can
 * refuse to run in the wrong place.
 */
object MoonTweakerApi {

    var currentStage: String = ""
}
