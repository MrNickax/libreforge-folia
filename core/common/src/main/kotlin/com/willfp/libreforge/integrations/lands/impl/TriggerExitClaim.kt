package com.willfp.libreforge.integrations.lands.impl

import com.willfp.libreforge.plugin
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import me.angeschossen.lands.api.events.player.area.PlayerAreaLeaveEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler

object TriggerExitClaim : Trigger("exit_claim") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.EVENT,
        TriggerParameter.LOCATION,
        TriggerParameter.TEXT
    )

    @EventHandler(ignoreCancelled = true)
    fun handle(event: PlayerAreaLeaveEvent) {
        val player = Bukkit.getPlayer(event.playerUUID) ?: return
        if (!player.isConnected) return

        player.scheduler.run(
            plugin,
            {
                // TriggerDispatchEvent may only be triggered synchronously.
                this.dispatch(
                    player.toDispatcher(),
                    TriggerData(
                        player = player,
                        event = event,
                        location = player.location.clone(),
                        text = event.area.name
                    )
                )
            },
            {}
        )
    }
}