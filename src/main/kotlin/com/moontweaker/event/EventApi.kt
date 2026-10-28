package com.moontweaker.event

import org.apache.logging.log4j.Logger
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction

object EventApi {

    private val listeners = mutableMapOf<String, MutableList<LuaValue>>()
    private lateinit var logger: Logger

    fun install(g: Globals, log: Logger) {
        logger = log
        val events = LuaValue.tableOf()
        events.set("on", object : TwoArgFunction() {
            override fun call(name: LuaValue, fn: LuaValue): LuaValue {
                if (!fn.isfunction()) throw LuaError("events.on expects a function")
                listeners.getOrPut(name.checkjstring()) { mutableListOf() }.add(fn)
                return LuaValue.NIL
            }
        })
        g.set("events", events)
    }

    fun clear() = listeners.clear()

    fun has(name: String) = !listeners[name].isNullOrEmpty()

    fun context(onCancel: () -> Unit): LuaValue {
        val ctx = LuaValue.tableOf()
        ctx.set("cancel", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                onCancel()
                return LuaValue.NIL
            }
        })
        return ctx
    }

    fun emit(name: String, ctx: LuaValue) {
        val list = listeners[name] ?: return
        for (fn in list) {
            try {
                fn.call(ctx)
            } catch (e: LuaError) {
                logger.error("MoonTweaker: error in event '{}': {}", name, e.message)
            } catch (e: Throwable) {
                logger.error("MoonTweaker: error in event '{}': {}", name, e.toString())
            }
        }
    }
}
