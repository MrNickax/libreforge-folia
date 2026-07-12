package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.listMap
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.plugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileLaunchEvent
import java.util.UUID

object EffectAntigravityProjectile : Effect<NoCompileData>("antigravity_projectile") {
    private val players = listMap<UUID, UUID>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        players[dispatcher.uuid].add(identifiers.uuid)
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        players[dispatcher.uuid].remove(identifiers.uuid)
    }

    @EventHandler
    fun handle(event: ProjectileLaunchEvent) {
        val player = event.entity.shooter as? Player ?: return
        if (players[player.uniqueId].isEmpty()) return
        val projectile = event.entity
        projectile.setGravity(false)
        val launchSpeed = projectile.velocity.length()
        // Folia: run on the projectile's own entity scheduler so it ticks on the projectile's
        // owning region thread and migrates with it, keeping velocity/chunk access on-region.
        projectile.scheduler.runAtFixedRate(
            plugin,
            { task ->
                if (projectile.isDead || projectile.isOnGround) {
                    task.cancel()
                    return@runAtFixedRate
                }
                val velocity = projectile.velocity
                val nextChunk = projectile.location.add(velocity).chunk
                if (!nextChunk.isLoaded) {
                    projectile.setGravity(true)
                    task.cancel()
                    return@runAtFixedRate
                }
                val currentSpeed = velocity.length()
                if (currentSpeed > 0) {
                    projectile.velocity = velocity.multiply(launchSpeed / currentSpeed)
                }
            },
            null,
            1L,
            1L
        )
    }
}