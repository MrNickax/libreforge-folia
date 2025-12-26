package com.willfp.libreforge.integrations.purpur.impl

import com.willfp.eco.core.gui.player
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.inventory.GrindstoneInventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TriggerGrindItem : Trigger("grind_item") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE
    )

    private val grindStoneXpQueue = ConcurrentHashMap<UUID, ItemStack>()

    @EventHandler
    fun postGrindstone(event: InventoryClickEvent) {
        val player = event.player
        if (!player.isConnected) return

        val inventory = event.clickedInventory as? GrindstoneInventory ?: return
        if (event.slot != 2) return

        val item = inventory.result ?: return

        this.grindStoneXpQueue[player.uniqueId] = item
    }

    @EventHandler
    fun onPlayerXpChange(event: PlayerExpChangeEvent) {
        val player = event.player

        val item = this.grindStoneXpQueue.remove(player.uniqueId) ?: return

        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player,
                item = item,
                value = event.amount.toDouble()
            )
        )
    }
}
