package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.safeTeleportAsync
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit
import org.bukkit.Material

object EffectTeleportToGround : Effect<NoCompileData>("teleport_to_ground") {
    override val description = "Teleports the player straight down to the first solid ground beneath them."
    override val categories = setOf("movement")

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false
        val location = player.location
        val world = location.world ?: return false

        // Folia: the column below the player can only be read from the thread that owns it,
        // which a projectile trigger's (the victim's region) is not.
        if (!Bukkit.isOwnedByCurrentRegion(location)) {
            return false
        }

        var current = location.clone()
        if (current.y > world.maxHeight) {
            current.y = world.maxHeight.toDouble()
        }

        while (world.getBlockAt(current).type == Material.AIR) {
            if (current.y <= world.minHeight) {
                return false
            }
            current = current.subtract(0.0, 1.0, 0.0)
        }

        player.safeTeleportAsync(current)

        return true
    }
}
