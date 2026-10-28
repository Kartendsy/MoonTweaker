package com.moontweaker.script

import com.moontweaker.event.EventApi
import com.moontweaker.lua.RecipeApi
import net.minecraftforge.fml.common.Loader
import org.apache.logging.log4j.Logger
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseMathLib
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

class ScriptManager(private val logger: Logger) {

    data class ScriptError(val file: String, val message: String)

    val errors = CopyOnWriteArrayList<ScriptError>()
    var loadedCount = 0
        private set

    private val modules = mutableMapOf<String, LuaValue>()
    private var recipeCounter = 0

    fun nextRecipeId(): String = "script_" + (recipeCounter++)

    fun scriptDir(): File {
        val configDir = Loader.instance().configDir
        val gameDir = configDir?.parentFile ?: File(".")
        return File(gameDir, "lua_scripts")
    }

    fun loadStartup() = load("startup")

    fun loadServer() = load("server")

    fun reload() {
        errors.clear()
        recipeCounter = 0
        loadedCount = 0
        loadServer()
    }

    private fun load(stage: String) {
        modules.clear()
        if (stage == "server") EventApi.clear()
        val dir = File(scriptDir(), stage)
        if (!dir.isDirectory) {
            dir.mkdirs()
            return
        }
        val globals = createGlobals()
        var count = 0
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "lua" }
            .sortedBy { it.path }
            .forEach { if (runFile(globals, it, dir)) count++ }
        loadedCount = count
        logger.info("MoonTweaker: stage '{}' loaded, {} scripts, {} errors", stage, count, errors.size)
    }

    private fun runFile(globals: Globals, file: File, root: File): Boolean {
        val name = "@" + root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        return try {
            globals.load(file.readText(StandardCharsets.UTF_8), name).call()
            true
        } catch (e: LuaError) {
            errors += ScriptError(name.removePrefix("@"), e.message ?: "unknown Lua error")
            false
        } catch (e: Throwable) {
            errors += ScriptError(name.removePrefix("@"), e.toString())
            false
        }
    }

    private fun createGlobals(): Globals {
        val g = Globals()
        g.load(BaseLib())
        g.load(PackageLib())
        g.load(Bit32Lib())
        g.load(TableLib())
        g.load(StringLib())
        g.load(CoroutineLib())
        g.load(JseMathLib())
        LoadState.install(g)
        LuaC.install(g)
        for (name in listOf("luajava", "io", "package", "debug", "dofile", "loadfile", "load")) {
            g.set(name, LuaValue.NIL)
        }
        g.set("os", safeOs())
        g.set("print", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                logger.info("[lua] {}", arg.tojstring())
                return LuaValue.NIL
            }
        })
        g.set("require", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = requireModule(g, arg.checkjstring())
        })
        g.set("log", logTable())
        g.set("ore", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.valueOf("ore:" + arg.checkjstring())
        })
        RecipeApi.install(g, logger, this)
        EventApi.install(g, logger)
        return g
    }

    private fun requireModule(g: Globals, name: String): LuaValue {
        modules[name]?.let { return it }
        val rel = name.replace('.', '/') + ".lua"
        val root = File(scriptDir(), "server").canonicalFile
        val file = File(root, rel).canonicalFile
        if (!file.toPath().startsWith(root.toPath())) throw LuaError("invalid module: $name")
        if (!file.isFile) throw LuaError("module not found: $name")
        val result = g.load(file.readText(StandardCharsets.UTF_8), "@$rel").call()
        val module = if (result.isnil()) LuaValue.TRUE else result
        modules[name] = module
        return module
    }

    private fun safeOs(): LuaValue {
        val safe = LuaValue.tableOf()
        safe.set("time", object : OneArgFunction() {
            override fun call(arg: LuaValue) = LuaValue.valueOf(System.currentTimeMillis() / 1000.0)
        })
        safe.set("clock", object : OneArgFunction() {
            override fun call(arg: LuaValue) = LuaValue.valueOf(System.nanoTime() / 1_000_000_000.0)
        })
        safe.set("difftime", object : org.luaj.vm2.lib.TwoArgFunction() {
            override fun call(a: LuaValue, b: LuaValue) = LuaValue.valueOf(a.todouble() - b.todouble())
        })
        return safe
    }

    private fun logTable(): LuaValue {
        val t = LuaValue.tableOf()
        t.set("info", object : OneArgFunction() {
            override fun call(arg: LuaValue) = LuaValue.NIL.also { logger.info("[lua] {}", arg.tojstring()) }
        })
        t.set("warn", object : OneArgFunction() {
            override fun call(arg: LuaValue) = LuaValue.NIL.also { logger.warn("[lua] {}", arg.tojstring()) }
        })
        t.set("error", object : OneArgFunction() {
            override fun call(arg: LuaValue) = LuaValue.NIL.also { logger.error("[lua] {}", arg.tojstring()) }
        })
        return t
    }
}
