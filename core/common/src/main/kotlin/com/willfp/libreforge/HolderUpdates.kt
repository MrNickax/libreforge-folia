package com.willfp.libreforge

import com.willfp.eco.core.cache.EcoCache
import com.willfp.eco.core.events.ArmorChangeEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.time.Duration
import java.util.UUID

@Suppress("unused", "UNUSED_PARAMETER")
object ItemRefreshListener : Listener {
    private val inventoryClickTimeoutMillis =
        plugin.configYml.getInt("refresh.inventory-click.timeout").toLong()

    private val inventoryClickTimeouts = EcoCache.builder<UUID, Unit>()
        .expireAfterWrite(Duration.ofMillis(inventoryClickTimeoutMillis))
        .build()

    // Ticks to wait before the trailing refresh: long enough for the rate-limit window to
    // have elapsed, so the refresh sees the final state of a burst of clicks.
    private val trailingRefreshDelay = ((inventoryClickTimeoutMillis + 49) / 50).coerceAtLeast(1L) + 1L

    // Players that already have a trailing refresh queued, so a burst of rate-limited
    // clicks only ever queues one. Self-expiring so a task that never runs (and never
    // reports itself retired) can't wedge a player out of trailing refreshes for good.
    private val trailingRefreshQueued = EcoCache.builder<UUID, Unit>()
        .expireAfterWrite(Duration.ofMillis(trailingRefreshDelay * 50 + 1000))
        .build()

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

        refreshAfterInventoryChange(player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return

        refreshAfterInventoryChange(player)
    }

    /**
     * Refresh holders after an inventory interaction has actually been applied.
     *
     * Two things have to hold for this to be correct:
     *
     * 1. The refresh must be deferred. [InventoryClickEvent] fires *before* the click is
     *    applied to the inventory, so refreshing inline caches a pre-click snapshot (with a
     *    fresh holder-cache TTL) and leaves holders permanently one interaction behind.
     * 2. The rate limit must be trailing-edge, not leading-edge. Dropping rate-limited
     *    clicks outright meant the click that deposits a cursor item into the held hotbar
     *    slot - which almost always lands inside the timeout window opened by the click
     *    that picked the item up - never produced a refresh at all, so held-item
     *    enchantments stayed inactive until the holder cache expired on its own.
     */
    private fun refreshAfterInventoryChange(player: Player) {
        if (inventoryClickTimeouts.get(player.uniqueId) != null) {
            queueTrailingRefresh(player)
            return
        }

        inventoryClickTimeouts.put(player.uniqueId, Unit)

        player.scheduler.runDelayed(
            plugin,
            {
                player.toDispatcher().refreshHolders()
            },
            {},
            1L
        )
    }

    private fun queueTrailingRefresh(player: Player) {
        val uuid = player.uniqueId

        if (trailingRefreshQueued.get(uuid) != null) {
            return
        }

        trailingRefreshQueued.put(uuid, Unit)

        player.scheduler.runDelayed(
            plugin,
            {
                trailingRefreshQueued.invalidate(uuid)
                player.toDispatcher().refreshHolders()
            },
            {
                trailingRefreshQueued.invalidate(uuid)
            },
            trailingRefreshDelay
        )
    }
}