package com.moontweaker.command

import com.moontweaker.script.ScriptManager
import net.minecraft.command.CommandException
import net.minecraft.command.ICommand
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString

class MoonTweakerCommand(private val scripts: ScriptManager) : ICommand {

    override fun getName(): String = "moontweaker"

    override fun getUsage(sender: ICommandSender): String = "/moontweaker <reload|errors>"

    override fun getAliases(): List<String> = emptyList()

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = true

    @Throws(CommandException::class)
    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        when (args.firstOrNull()) {
            "reload" -> {
                scripts.reload()
                sender.sendMessage(TextComponentString(
                    "MoonTweaker reloaded: ${scripts.loadedCount} scripts, ${scripts.errors.size} errors"
                ))
                if (scripts.errors.isNotEmpty()) {
                    sender.sendMessage(TextComponentString("Run /moontweaker errors for details"))
                }
            }
            "errors" -> {
                if (scripts.errors.isEmpty()) {
                    sender.sendMessage(TextComponentString("No script errors"))
                } else {
                    scripts.errors.forEach { sender.sendMessage(TextComponentString("${it.file}: ${it.message}")) }
                }
            }
            else -> sender.sendMessage(TextComponentString(getUsage(sender)))
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): List<String> = if (args.size == 1) listOf("reload", "errors").filter { it.startsWith(args[0]) } else emptyList()

    override fun isUsernameIndex(args: Array<String>, index: Int): Boolean = false

    override fun compareTo(other: ICommand): Int = name.compareTo(other.name)
}
