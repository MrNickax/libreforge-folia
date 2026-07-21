package com.willfp.libreforge

import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.events.ArmorChangeEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("unused", "UNUSED_PARAMETER")
object ItemRefreshListener : Listener {
    private val inventoryClickTimeouts = Caffeine.newBuilder()
        .expireAfterWrite(
            plugin.configYml.getInt("refresh.inventory-click.timeout").toLong(),
            TimeUnit.MILLISECONDS
        )
        .build<UUID, Unit>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (!plugin.configYml.getBool("refresh.pickup.enabled")) {
            return
        }

        if (plugin.configYml.getBool("refresh.pickup.require-meta")) {
            if (!event.item.itemStack.hasItemMeta()) {
                return
            }
        }

        event.entity.toDispatcher().refreshHolders()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        for (player in Bukkit.getServer().onlinePlayers) {
            player.scheduler.run(
                plugin,
                {
                    player.toDispatcher().refreshHolders()
                },
                {}
            )
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryDrop(event: PlayerDropItemEvent) {
        event.player.toDispatcher().refreshHolders()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChangeSlot(event: PlayerItemHeldEvent) {
        val player = event.player

        if (plugin.configYml.getBool("refresh.held.require-meta")) {
            val oldItem = player.inventory.getItem(event.previousSlot)
            val newItem = player.inventory.getItem(event.newSlot)
            if (((oldItem == null) || !oldItem.hasItemMeta()) && ((newItem == null) || !newItem.hasItemMeta())) {
                return
            }
        }


        val dispatcher = player.toDispatcher()

        // Immediately disable main-hand effects so a stale held-item enchant (e.g. blast
        // mining) can't fire against the newly-selected item during the 1-tick window
        // before the deferred refresh runs. The refresh re-enables them if still held.
        // markMainhandRefreshPending keeps the periodic poll from re-adding them meanwhile.
        dispatcher.markMainhandRefreshPending()
        dispatcher.disableMainhandEffects()

        player.scheduler.runDelayed(
            plugin,
            {
                dispatcher.refreshHolders()
            },
            {},
            1L
        )
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        event.player.toDispatcher().refreshHolders()
    }

    @EventHandler
    fun onArmorChange(event: ArmorChangeEvent) {
        event.player.toDispatcher().refreshHolders()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (inventoryClickTimeouts.getIfPresent(player.uniqueId) != null) {
            return
        }

        inventoryClickTimeouts.put(player.uniqueId, Unit)

        player.toDispatcher().refreshHolders()
    }
}