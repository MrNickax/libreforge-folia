package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getIntFromExpression
import com.willfp.libreforge.safeTeleportAsync
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit

object EffectBlink : Effect<NoCompileData>("blink") {
    override val description = "Teleports the player forward in their look direction, stopping at solid blocks."
    override val categories = setOf("movement")

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require(
            "distance",
            "You must specify the blink distance!",
            description = "The maximum number of blocks to teleport forward. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "5 + %level%"
        )
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false

        // Folia: the path is read block by block around the player, which is only possible from
        // the thread that owns them; a projectile trigger's (the victim's region) is not.
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            return false
        }

        val distance = config.getIntFromExpression("distance", data)

        val direction = player.location.direction.normalize()
        var destination = player.location.clone()

        for (i in 1..distance) {
            val next = player.location.clone().add(direction.clone().multiply(i))
            // The region's edge stops the blink like a solid block: what lies past it can't be read.
            if (!Bukkit.isOwnedByCurrentRegion(next) || !next.block.isPassable) break
            destination = next
        }

        destination.yaw = player.location.yaw
        destination.pitch = player.location.pitch

        player.safeTeleportAsync(destination)

        return true
    }
}
