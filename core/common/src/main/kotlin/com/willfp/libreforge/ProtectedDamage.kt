package com.willfp.libreforge

import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * If [source] is allowed to damage this entity, according to antigrief and protection plugins.
 *
 * Effects that deal damage outside of the event that triggered them - delayed bleed ticks, AoE
 * pulses, reflected damage - produce an EntityDamageEvent with no damager, so protection plugins
 * have nothing to attribute the hit to and cannot cancel it. Querying the antigrief integrations
 * directly closes that hole without attributing the damage to the source, which would apply
 * knockback and re-fire attack triggers.
 */
fun LivingEntity.canBeDamagedBy(source: Player?): Boolean {
    if (source == null) {
        return true
    }

    return AntigriefManager.canInjure(source, this)
}

/**
 * Damage this entity on behalf of [source], respecting antigrief and protection plugins.
 *
 * @return If the damage was applied.
 */
fun LivingEntity.damageFrom(amount: Double, source: Player?): Boolean {
    if (!this.canBeDamagedBy(source)) {
        return false
    }

    this.damage(amount)
    return true
}
