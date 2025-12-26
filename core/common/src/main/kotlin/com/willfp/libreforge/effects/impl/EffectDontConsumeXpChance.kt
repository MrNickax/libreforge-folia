package com.willfp.libreforge.effects.impl

import com.willfp.libreforge.effects.templates.ChanceMultiplierEffect
import com.willfp.libreforge.plugin
import com.willfp.libreforge.toDispatcher
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.enchantment.EnchantItemEvent

object EffectDontConsumeXpChance : ChanceMultiplierEffect("dont_consume_xp_chance") {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handle(event: EnchantItemEvent) {
        val player = event.enchanter
        val cost = event.whichButton() + 1

        if (!passesChance(player.toDispatcher())) return

        // 2 Ticks because that's what I did in EcoSkills!
        // also Uses the player's scheduler because it is interacting
        // with player methods, which are not thread-safe
        player.scheduler.runDelayed(
            plugin,
            { player.giveExpLevels(cost) },
            {},
            2
        )
    }
}
