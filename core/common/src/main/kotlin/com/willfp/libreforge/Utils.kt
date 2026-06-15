package com.willfp.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.util.SoundUtils
import com.willfp.eco.util.namespacedKeyOf
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import kotlin.math.roundToInt

inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? {
    return try {
        enumValueOf<T>(name)
    } catch (e: IllegalArgumentException) {
        null
    }
}

val Any.deprecationMessage: String?
    get() {
        val annotation = this::class.java.getAnnotation(Deprecated::class.java)
        return annotation?.message
    }

fun Location.getNearbyBlocks(
    x: Double,
    y: Double,
    z: Double
): Collection<Block> {
    val blocks = mutableListOf<Block>()
    val world = this.world ?: return blocks
    val baseX = this.blockX
    val baseY = this.blockY
    val baseZ = this.blockZ

    val xRadius = (x / 2).roundToInt()
    val yRadius = (y / 2).roundToInt()
    val zRadius = (z / 2).roundToInt()

    for (xPos in -xRadius..xRadius) {
        for (yPos in -yRadius..yRadius) {
            for (zPos in -zRadius..zRadius) {
                blocks.add(world.getBlockAt(baseX + xPos, baseY + yPos, baseZ + zPos))
            }
        }
    }

    return blocks
}

fun Location.getNearbyBlocksInSphere(
    radius: Double
): Collection<Block> = getNearbyBlocks(radius, radius, radius)
    .filter { it.location.distanceSquared(this) <= radius * radius }

fun Collection<ItemStack?>.filterNotEmpty() =
    this.filterNot { Items.isEmpty(it) }
        .filterNotNull()

internal val ItemStack?.isEcoEmpty: Boolean
    get() = Items.isEmpty(this)

fun ItemStack.applyDamage(damage: Int, player: Player?): Boolean {
    val meta = this.itemMeta as? Damageable ?: return false
    meta.damage += damage
    if (meta.damage >= this.type.maxDurability) {
        meta.damage = this.type.maxDurability.toInt()
        if (player != null) {
            Bukkit.getPluginManager().callEvent(PlayerItemBreakEvent(player, this))
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1f, 1f)
        }
        @Suppress("DEPRECATION")
        this.type = Material.AIR
    } else {
        this.itemMeta = meta
    }

    return true
}

/**
 * Play a sound configured under [config] to [player].
 *
 * This replaces eco's [com.willfp.eco.core.sound.PlayableSound.playTo], which (as of eco 7.6.3)
 * computes the pitch via `ThreadLocalRandom.nextDouble(minPitch, maxPitch)`. That call throws
 * `IllegalArgumentException: bound must be greater than origin` whenever the configured pitch is a
 * single value (so `minPitch == maxPitch`), which is the default in libreforge's own config.yml.
 *
 * Reads the same keys as eco (`enabled`, `sound`, `pitch`, `volume`, `category`) and supports the
 * `"min..max"` pitch range notation, but only randomises the pitch when the range is actually
 * non-empty.
 */
fun playConfigSound(config: Config, player: Player) {
    if (config.getBoolOrNull("enabled") == false) {
        return
    }

    val soundName = config.getStringOrNull("sound") ?: return
    val sound = SoundUtils.getSound(soundName) ?: return

    val volume = config.getDoubleOrNull("volume") ?: 1.0

    val pitchString = config.getStringOrNull("pitch") ?: "1.0"
    val pitch = if (pitchString.contains("..")) {
        val (min, max) = pitchString.split("..", limit = 2)
            .let { (it.getOrNull(0)?.toDoubleOrNull() ?: 1.0) to (it.getOrNull(1)?.toDoubleOrNull() ?: 1.0) }

        if (max > min) {
            min + Math.random() * (max - min)
        } else {
            min
        }
    } else {
        pitchString.toDoubleOrNull() ?: 1.0
    }

    val category = config.getStringOrNull("category")
        ?.let { enumValueOfOrNull<SoundCategory>(it.uppercase()) }
        ?: SoundCategory.MASTER

    player.playSound(player.location, sound, category, volume.toFloat(), pitch.toFloat())
}

// 1.21 compat
fun getEnchantment(id: String): Enchantment? {
    if (id.contains(':')) {
        val key = namespacedKeyOf(id)
        @Suppress("DEPRECATION", "REMOVAL")
        return Enchantment.getByKey(key)
    } else {
        @Suppress("DEPRECATION", "REMOVAL")
        return Enchantment.getByKey(NamespacedKey.minecraft(id))
    }
}