package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.GlobalDispatcher
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerGroup
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit

object TriggerGroupGlobalStatic : TriggerGroup("global_static") {
    private val registry = mutableMapOf<Int, TriggerGlobalStatic>()
    private var tick = 0

    override fun create(value: String): Trigger? {
        val interval = value.toIntOrNull() ?: return null
        if (interval < 1) {
            return null
        }

        return registry.getOrPut(interval) { TriggerGlobalStatic(interval) }
    }

    override fun postRegister() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _ ->
            tick++

            for ((interval, trigger) in registry) {
                if (tick % interval == 0) {
                    trigger.dispatch(
                        GlobalDispatcher,
                        TriggerData()
                    )
                }
            }
        }, 1L, 1L)
    }

    private class TriggerGlobalStatic(interval: Int) : Trigger("global_static_$interval") {
        override val parameters = emptySet<TriggerParameter>()
    }
}