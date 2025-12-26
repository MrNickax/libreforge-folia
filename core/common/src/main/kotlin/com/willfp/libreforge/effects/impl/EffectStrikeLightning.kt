package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getIntFromExpression
import com.willfp.libreforge.getOrElse
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit

object EffectStrikeLightning : Effect<NoCompileData>("strike_lightning") {
    override val parameters = setOf(
        TriggerParameter.LOCATION
    )

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val location = data.location ?: return false
        val world = location.world ?: return false

        val amount = config.getOrElse("amount", 1) { getIntFromExpression(it, data) }

        for (i in 1..amount) {
            Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                { world.strikeLightning(location) },
                1
            )
        }

        return true
    }
}
