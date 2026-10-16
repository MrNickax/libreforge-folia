package com.willfp.libreforge

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Entities with an effect teleport scheduled or running, mapped to the time it was claimed.
private val teleportsInFlight = ConcurrentHashMap<UUID, Long>()

// A claim older than this is treated as lost (a future that never completed) and can be taken
// over, so one stuck teleport never locks an entity out of teleport effects for good.
private const val TELEPORT_CLAIM_CEILING_MS = 10_000L

/**
 * Runs [action] on the thread that owns this entity: straight away when the current region
 * already owns it, and through the entity's scheduler only when not.
 *
 * Triggers fire on whichever thread raised the event, which is not always the one that owns the
 * entity whose effects run (a projectile hit fires on the victim's region, a command on the
 * global region or the sender's).
 */
internal fun Entity.runOnOwnerThread(action: () -> Unit) {
    if (Bukkit.isOwnedByCurrentRegion(this)) {
        action()
    } else {
        this.scheduler.run(plugin, { action() }, null)
    }
}

/**
 * Teleports this entity from its own scheduler on the next tick, instead of calling
 * [Entity.teleportAsync] inline from the trigger's thread.
 *
 * Folia allows one region hand-off per entity at a time, and an inline call fails in two ways:
 * - From a thread that does not own the entity (bow_attack, projectile_hit, kill... fire on the
 *   victim's region), the server schedules its own task on the entity. If a hand-off is still
 *   pending when that task runs (a /home in progress, a second arrow), it throws where nothing
 *   catches it, the region stops ticking and the whole server goes down.
 * - From an attack or interact packet's event, the hand-off starts while vanilla is still
 *   handling that packet on the now in-transit player, which kicks them ("Cannot trigger
 *   criterion async").
 *
 * Running on the entity's scheduler always calls it from the owner, after the packet, where a
 * pending hand-off throws synchronously and is caught here. Only one effect teleport per entity
 * is in flight at a time: further ones are skipped until it lands.
 *
 * @return If the teleport was scheduled.
 */
internal fun Entity.safeTeleportAsync(location: Location): Boolean {
    val entity = this
    val id = entity.uniqueId
    val target = location.clone()
    val claim = System.currentTimeMillis()

    var claimed = false
    teleportsInFlight.compute(id) { _, since ->
        if (since != null && claim - since < TELEPORT_CLAIM_CEILING_MS) {
            since
        } else {
            claimed = true
            claim
        }
    }

    if (!claimed) {
        return false
    }

    // Only ever release our own claim, never one that took over a lost one.
    fun release() {
        teleportsInFlight.remove(id, claim)
    }

    val task = entity.scheduler.run(plugin, { _ ->
        val future = try {
            entity.teleportAsync(target)
        } catch (_: RuntimeException) {
            // Another hand-off (a plugin teleport, a portal) is still pending on the entity.
            null
        }

        if (future == null) {
            release()
        } else {
            future.whenComplete { _, _ -> release() }
        }
    }, { release() })

    // Already removed (dead, logged out): the retired callback is never called in that case.
    if (task == null) {
        release()
        return false
    }

    return true
}
