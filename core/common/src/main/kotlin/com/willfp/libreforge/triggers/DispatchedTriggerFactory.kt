package com.willfp.libreforge.triggers

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.map.listMap
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.toDispatcher
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/*

Prevents multiple identical triggers from being triggered in the same tick.

 */

class DispatchedTriggerFactory(
    private val plugin: EcoPlugin
) {
    private val dispatcherTriggers = listMap<UUID, Int>()

    @Deprecated(
        "Use create(dispatcher, trigger, data) instead",
        ReplaceWith("create(dispatcher, trigger, data)"),
        DeprecationLevel.ERROR
    )
    fun create(player: Player, trigger: Trigger, data: TriggerData): DispatchedTrigger? {
        return create(player.toDispatcher(), trigger, data)
    }

    fun create(dispatcher: Dispatcher<*>, trigger: Trigger, data: TriggerData): DispatchedTrigger? {
        if (!trigger.isEnabled) {
            return null
        }

        val hash = (trigger.hashCode() shl 5) xor data.hashCode()
        val dispatcherUuid = dispatcher.uuid

        // Initialize the entry if it doesn't exist, then check and add
        val hashes = dispatcherTriggers.getOrPut(dispatcherUuid) { mutableListOf() }
        if (hash in hashes) {
            return null
        }

        hashes.add(hash)
        return DispatchedTrigger(dispatcher, trigger, data.copy(dispatcher = dispatcher))
    }

    internal fun startTicking() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { dispatcherTriggers.clear() },
            1L,
            1L
        )
    }
}