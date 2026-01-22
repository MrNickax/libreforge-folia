package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.normalize
import com.willfp.libreforge.toFloat3
import com.willfp.libreforge.toVector
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import dev.romainguy.kotlin.math.Float3

object EffectPullToLocation : Effect<NoCompileData>("pull_to_location") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION
    )

    override val arguments = arguments {
        require("velocity", "You must specify the movement velocity!")
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false
        val location = data.location ?: return false

        if (player.world != location.world) {
            return false
        }

        val direction = location.toFloat3().minus(player.location.toFloat3())
        
        // Check if the direction vector is zero (player is at target location)
        // Using component check since Float3 doesn't have a length() method
        if (direction.x == 0f && direction.y == 0f && direction.z == 0f) {
            return false
        }

        val vector = direction
            .normalize()
            .plus(Float3(0f, config.getDoubleFromExpression("jump", data).toFloat(), 0f))
            .times(config.getDoubleFromExpression("velocity", data).toFloat())
            .toVector()

        player.velocity = vector

        return true
    }
}
