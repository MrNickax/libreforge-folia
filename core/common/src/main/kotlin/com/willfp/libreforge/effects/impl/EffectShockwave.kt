package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.getIntFromExpression
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity

object EffectShockwave : Effect<NoCompileData>("shockwave") {
    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require("radius", "You must specify the shockwave radius!")
        require("pulses", "You must specify the number of pulses!")
        require("damage", "You must specify the damage per entity!")
        require("knockback", "You must specify the knockback force!")
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false
        val origin = player.location.clone()
        val radius = config.getDoubleFromExpression("radius", data)
        val pulses = config.getIntFromExpression("pulses", data)
        val damage = config.getDoubleFromExpression("damage", data)
        val knockback = config.getDoubleFromExpression("knockback", data)

        val hit = mutableSetOf<LivingEntity>()
        var pulse = 0

        Bukkit.getRegionScheduler().runAtFixedRate(
            plugin,
            origin,
            { task ->
                pulse++
                val currentRadius = radius * pulse / pulses

                safeRead { origin.world?.getNearbyEntities(origin, currentRadius, currentRadius, currentRadius) }
                    ?.filterIsInstance<LivingEntity>()
                    ?.filter { it !in hit && it != player }
                    ?.forEach { entity ->
                        hit.add(entity)
                        val dir = entity.location.toVector()
                            .subtract(origin.toVector())
                            .normalize()
                        entity.velocity = dir.multiply(knockback)
                        entity.damage(damage)
                    }

                if (pulse >= pulses) task.cancel()
            },
            1L,
            3L
        )

        return true
    }

    // Folia-safe read: a getNearbyEntities AABB that reaches into a neighbouring region fails
    // the region tick-thread check (IllegalStateException); degrade to null instead of failing.
    private inline fun <T> safeRead(block: () -> T): T? =
        try {
            block()
        } catch (_: IllegalStateException) {
            null
        }
}