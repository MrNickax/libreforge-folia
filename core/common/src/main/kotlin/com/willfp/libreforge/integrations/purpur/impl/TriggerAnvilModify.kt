package com.willfp.libreforge.integrations.purpur.impl

import com.willfp.eco.core.gui.player
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.AnvilInventory
import java.util.concurrent.ConcurrentHashMap

object TriggerAnvilModify : Trigger("anvil_modify") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE
    )

    private val playerCosts = ConcurrentHashMap<org.bukkit.entity.Player, Int>()

    // Capture the cost when the result is prepared
    @EventHandler(ignoreCancelled = true)
    fun onPrepare(event: PrepareAnvilEvent) {
        val player = event.viewers.firstOrNull() as? org.bukkit.entity.Player ?: return

        @Suppress("DEPRECATION", "REMOVAL")
        playerCosts[player] = event.inventory.repairCost
    }

    // Fire trigger when item is actually taken
    @EventHandler(ignoreCancelled = true)
    fun onTake(event: InventoryClickEvent) {
        val player = event.player
        if (!player.isConnected) return

        val inventory = event.clickedInventory as? AnvilInventory ?: return
        val result = inventory.result ?: return

        val cost = playerCosts.remove(player) ?: return

        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player,
                item = result,
                value = cost.toDouble()
            )
        )
    }
}
