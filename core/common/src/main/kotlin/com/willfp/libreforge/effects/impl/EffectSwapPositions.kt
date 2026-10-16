package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.safeTeleportAsync
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter

object EffectSwapPositions : Effect<NoCompileData>("swap_positions") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.VICTIM
    )

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false
        val victim = data.victim ?: return false

        // Both targets are taken before either one moves; each entity then teleports from its
        // own scheduler, as the two can be owned by different regions.
        val playerLoc = player.location.clone()
        val victimLoc = victim.location.clone()

        if (player.safeTeleportAsync(victimLoc)) {
            victim.safeTeleportAsync(playerLoc)
        }

        return true
    }
}