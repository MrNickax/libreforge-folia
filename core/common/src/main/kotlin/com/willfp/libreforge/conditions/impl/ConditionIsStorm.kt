package com.willfp.libreforge.conditions.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.plugin
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.updateEffects
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.weather.WeatherChangeEvent

object ConditionIsStorm: Condition<NoCompileData>("is_storm") {
    override val description = "Passes when the world is experiencing a storm."
    override val categories = setOf("world")
    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val location = dispatcher.location ?: return false
        return location.world.hasStorm()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handle(event: WeatherChangeEvent) {
        val world = event.world

        // Folia: this fires on the global region thread, where neither the world's entities nor
        // their effects can be touched. Each player is updated from their own scheduler (which also
        // runs after the weather has actually changed); other entities can't be listed safely from
        // here and pick the change up on their next effect update.
        for (player in Bukkit.getOnlinePlayers()) {
            player.scheduler.run(plugin, {
                if (player.world == world) {
                    player.toDispatcher().updateEffects()
                }
            }, null)
        }
    }
}