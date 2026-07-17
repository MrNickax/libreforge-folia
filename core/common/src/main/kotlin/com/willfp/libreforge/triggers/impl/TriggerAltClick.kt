package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.plugin
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID

object TriggerAltClick : Trigger("alt_click") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.VICTIM,
        TriggerParameter.LOCATION,
        TriggerParameter.EVENT,
        TriggerParameter.ITEM,
        TriggerParameter.BLOCK
    )

    private val LEFT_CLICK_ITEMS = listOf(
        Material.FISHING_ROD,
        Material.BOW,
        Material.CROSSBOW,
        Material.TRIDENT
    )

    private val BLOCK_BLACKLIST = mutableListOf(
        Material.CRAFTING_TABLE,
        Material.GRINDSTONE,
        Material.ENCHANTING_TABLE,
        Material.FURNACE,
        Material.SMITHING_TABLE,
        Material.LEVER,
        Material.REPEATER,
        Material.COMPARATOR,
        Material.RESPAWN_ANCHOR,
        Material.NOTE_BLOCK,
        Material.ITEM_FRAME,
        Material.CHEST,
        Material.BARREL,
        Material.BEACON,
        Material.LECTERN,
        Material.FLETCHING_TABLE,
        Material.SMITHING_TABLE,
        Material.STONECUTTER,
        Material.SMOKER,
        Material.BLAST_FURNACE,
        Material.BREWING_STAND,
        Material.DISPENSER,
        Material.DROPPER
    )

    private val preventDoubleTriggers = mutableSetOf<UUID>()

    init {
        BLOCK_BLACKLIST.addAll(Tag.BUTTONS.values)
        BLOCK_BLACKLIST.addAll(Tag.BEDS.values)
        BLOCK_BLACKLIST.addAll(Tag.DOORS.values)
        BLOCK_BLACKLIST.addAll(Tag.FENCE_GATES.values)
        BLOCK_BLACKLIST.addAll(Tag.TRAPDOORS.values)
        BLOCK_BLACKLIST.addAll(Tag.ANVIL.values)
        BLOCK_BLACKLIST.addAll(Tag.SHULKER_BOXES.values)
    }

    @EventHandler
    fun handle(event: PlayerInteractEvent) {
        val player = event.player

        if (player.uniqueId in preventDoubleTriggers) {
            return
        }

        val itemStack = player.inventory.itemInMainHand

        if (event.action == Action.PHYSICAL) {
            return
        }

        if (LEFT_CLICK_ITEMS.contains(itemStack.type)) {
            if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                return
            }
        } else {
            if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) {
                return
            }
        }

        if (event.clickedBlock != null) {
            if (BLOCK_BLACKLIST.contains(event.clickedBlock!!.type)) {
                return
            }
        }

        val location: Location?
        val world = player.location.world ?: return

        // Folia/Canvas: rayTraceBlocks (block access) and rayTraceEntities (Level.getEntities)
        // fail the region tick-thread check when the ray reaches into a neighbouring region.
        // moonrise's TickThread.ensureTickThread LOGS a Throwable *before* it throws the
        // IllegalStateException ("Thread failed main thread check"), so catching the throw
        // (safeRead, below) stops the functional breakage but NOT the log spam. To keep the log
        // silent we must never invoke the ray-trace when it could reach cross-region: each call
        // is gated on region ownership of every chunk the ray can touch. When the ray is
        // cross-region we skip the call and leave the result null, which degrades to exactly the
        // same "no hit" fallback the code already uses below (same fallback location, null victim).
        // safeRead stays as the final safety net for any residual edge case.
        val blockDistance = plugin.configYml.getDouble("raytrace-distance")
        val result =
            if (rayIsRegionLocal(player.eyeLocation, blockDistance, 0)) {
                safeRead {
                    player.rayTraceBlocks(blockDistance, FluidCollisionMode.NEVER)
                }
            } else {
                null
            }

        val entityResult =
            if (rayIsRegionLocal(player.eyeLocation, 50.0, 1)) {
                safeRead {
                    world.rayTraceEntities(
                        player.eyeLocation,
                        player.eyeLocation.direction, 50.0, 3.0
                    ) { entity: Entity? -> entity is LivingEntity }
                }
            } else {
                null
            }

        location = result?.hitPosition?.toLocation(world)
            ?: if (entityResult != null) {
                entityResult.hitPosition.toLocation(world)
            } else {
                val dir = player.location.direction.normalize()
                    .multiply(plugin.configYml.getDoubleFromExpression("raytrace-distance"))
                player.location.add(dir)
            }

        val victim = entityResult?.hitEntity as? LivingEntity

        preventDoubleTriggers += player.uniqueId

        Bukkit.getGlobalRegionScheduler().runDelayed(
            plugin,
            { preventDoubleTriggers -= player.uniqueId },
            1L
        )

        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player,
                victim = victim,
                location = location,
                event = event,
                item = player.inventory.itemInMainHand,
                block = event.clickedBlock ?: result?.hitBlock ?: victim?.location?.block
            )
        )
    }

    // Folia-safe read: swallow the region tick-thread check failure (IllegalStateException)
    // so a cross-region ray-trace degrades to "no hit" instead of failing the event.
    private inline fun <T> safeRead(block: () -> T): T? =
        try {
            block()
        } catch (_: IllegalStateException) {
            null
        }

    // Chunk sampling step (blocks) for the region-ownership walk along a ray.
    private const val CHUNK_STEP = 16.0

    // True only if every chunk the ray can touch is owned by the current region thread.
    // We sample along the ray one chunk at a time (plus the exact endpoint), each check
    // covering a [radiusChunks] square radius to absorb the ray-trace AABB expansion
    // (raySize). A Folia region is a contiguous block of chunks, so sampling each chunk
    // plus the endpoint is sufficient to prove the whole ray stays region-local. If any
    // sample is cross-region we must NOT ray-trace, because moonrise's tick-thread check
    // would log-then-throw before we could catch it.
    private fun rayIsRegionLocal(origin: Location, maxDistance: Double, radiusChunks: Int): Boolean {
        val direction = origin.direction
        var travelled = 0.0
        while (travelled < maxDistance) {
            val point = origin.clone().add(direction.clone().multiply(travelled))
            if (!Bukkit.isOwnedByCurrentRegion(point, radiusChunks)) {
                return false
            }
            travelled += CHUNK_STEP
        }
        val end = origin.clone().add(direction.clone().multiply(maxDistance))
        return Bukkit.isOwnedByCurrentRegion(end, radiusChunks)
    }
}
