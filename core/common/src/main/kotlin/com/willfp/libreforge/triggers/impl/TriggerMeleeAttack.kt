package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TriggerMeleeAttack : Trigger("melee_attack") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.VICTIM,
        TriggerParameter.LOCATION,
        TriggerParameter.EVENT,
        TriggerParameter.ITEM
    )

    // Guards against recursive re-entry on the same victim (e.g. an effect that deals damage
    // back). Thread-safe for Folia, where damage events fire on parallel region threads.
    private val processingEntities = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun handle(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? LivingEntity ?: return
        val victim = event.entity as? LivingEntity ?: return

        if (event.cause == EntityDamageEvent.DamageCause.THORNS) {
            return
        }

        // Atomic check-and-add; skip if this victim is already being processed (re-entry)
        if (!processingEntities.add(victim.uniqueId)) {
            return
        }

        try {
            this.dispatch(
                attacker.toDispatcher(),
                TriggerData(
                    player = attacker as? Player,
                    victim = victim,
                    location = victim.location,
                    event = event,
                    item = attacker.equipment?.itemInMainHand,
                    value = event.finalDamage
                )
            )
        } finally {
            processingEntities.remove(victim.uniqueId)
        }
    }
}
