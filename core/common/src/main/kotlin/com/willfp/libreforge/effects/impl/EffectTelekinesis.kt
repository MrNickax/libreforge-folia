package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.events.EntityDeathByEntityEvent
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.core.map.listMap
import com.willfp.eco.util.TelekinesisUtils
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.plugin
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import io.papermc.paper.event.block.PlayerShearBlockEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerShearEntityEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectTelekinesis : Effect<NoCompileData>("telekinesis") {
    override val description = "Automatically sends all drops and XP from blocks, entities, and fishing directly to the player's inventory."
    override val categories = setOf("inventory")

    override val arguments = arguments {
        optional(
            "on_tamed_mob_kills",
            description = "If true, telekinesis also applies when a tamed mob kills an entity.",
            type = ArgType.BOOLEAN,
            default = "false"
        )
    }

    private val players = listMap<UUID, UUID>()
    private val pendingExperience = ConcurrentHashMap<UUID, Int>()
    private var allowTamedMobKills: Boolean = false

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        players[dispatcher.uuid].add(identifiers.uuid)
        allowTamedMobKills = config.getBoolOrNull("on_tamed_mob_kills") ?: false
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        players[dispatcher.uuid].remove(identifiers.uuid)
    }

    override fun postRegister() {
        TelekinesisUtils.registerTest { players[it.uniqueId].isNotEmpty() }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handle(event: BlockDropItemEvent) {
        val player = event.player
        val block = event.block

        if (!plugin.configYml.getBool("effects.telekinesis.always-process-blocks")
            && !TelekinesisUtils.testPlayer(player)) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        val drops = event.items.map { it.itemStack }
        event.items.clear()

        DropQueue(player)
            .setLocation(block.location)
            .addItems(drops)
            .push()
    }

    // Claimed at LOWEST and granted at MONITOR rather than taken in a single pass at HIGH.
    // Plugins that take over a block's drops - block regeneration plugins in particular - read
    // expToDrop at the top of their own HIGH handler and re-spawn the experience themselves, so
    // a single handler here wins or loses purely on plugin registration order. Claiming the
    // amount at LOWEST settles that: whoever reads it afterwards reads zero and spawns nothing.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun claimExperience(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            return
        }

        // Filter out telekinesis spawner xp to prevent dupe
        if (block.type == Material.SPAWNER) {
            event.expToDrop = 0
            return
        }

        pendingExperience[player.uniqueId] = event.expToDrop
        event.expToDrop = 0
    }

    // Not ignoreCancelled: the claim has to be dropped on a cancelled break too, otherwise it
    // would survive until the player's next one, and granting it would turn any handler that
    // cancels the break into a free experience farm. Whatever other handlers added to expToDrop
    // after the claim is picked up here so their contribution is not lost.
    @EventHandler(priority = EventPriority.MONITOR)
    fun handle(event: BlockBreakEvent) {
        val player = event.player

        val claimed = pendingExperience.remove(player.uniqueId) ?: return

        if (event.isCancelled) {
            return
        }

        val total = claimed + event.expToDrop
        event.expToDrop = 0

        if (total <= 0) {
            return
        }

        DropQueue(player)
            .setLocation(event.block.location)
            .addXP(total)
            .push()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handle(event: EntityDeathByEntityEvent) {
        val victim = event.victim


        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            if (MythicBukkit.inst().mobManager.isMythicMob(victim)) {
                return
            }
        }

        if (victim is Player && plugin.configYml.getBool("effects.telekinesis.on-players")) {
            return
        }

        val player = when (val killer = event.killer) {
            is Player -> killer
            is Projectile -> killer.shooter as? Player
            is Tameable -> {
                if (!killer.isTamed || !allowTamedMobKills) return
                killer.owner as? Player
            }
            else -> null
        } ?: return

        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        // Read the live droppedExp rather than the EntityDeathByEntityEvent.xp
        // snapshot so that modifications from earlier handlers (e.g. the
        // entity_xp_drop trigger) are respected.
        val xp = event.deathEvent.droppedExp
        val drops = event.drops.filterNotNull()

        DropQueue(player)
            .setLocation(victim.location)
            .addItems(drops)
            .addXP(xp)
            .forceTelekinesis()
            .push()

        event.deathEvent.droppedExp = 0
        event.deathEvent.drops.clear()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handle(event: PlayerShearEntityEvent) {
        val victim = event.entity


        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            if (MythicBukkit.inst().mobManager.isMythicMob(victim)) {
                return
            }
        }


        val player = event.player

        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        val drops = event.drops.toList()

        if (drops.isEmpty()) {
            return
        }

        DropQueue(player)
            .setLocation(victim.location)
            .addItems(drops)
            .forceTelekinesis()
            .push()

        event.drops.clear()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handle(event: PlayerShearBlockEvent) {
        val player = event.player
        val block = event.block

        if (!plugin.configYml.getBool("effects.telekinesis.always-process-blocks")
            && !TelekinesisUtils.testPlayer(player)) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        val drops = event.drops.toList()

        if (drops.isEmpty()) {
            return
        }

        DropQueue(player)
            .setLocation(block.location)
            .addItems(drops)
            .push()

        event.drops.clear()
    }

    // Fires at HIGH so TriggerCatchFish (NORMAL) has already applied modifiers
    // and updated the caught entity's stack before we read it.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handle(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return

        val player = event.player

        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        val caught = event.caught as? org.bukkit.entity.Item ?: return

        // getItemStack() returns a defensive copy - that's fine, we just need the data.
        val stack = caught.itemStack

        DropQueue(player)
            .setLocation(event.hook.location)
            .addItems(listOf(stack))
            .forceTelekinesis()
            .push()

        // Remove the entity so it doesn't also fly to the player naturally.
        caught.remove()
    }
}