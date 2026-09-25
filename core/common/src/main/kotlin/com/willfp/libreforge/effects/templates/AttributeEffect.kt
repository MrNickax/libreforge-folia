package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import com.willfp.libreforge.plugin
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.EquipmentSlotGroup

abstract class AttributeEffect(
    id: String,
    private val attribute: Attribute,
    private val operation: AttributeModifier.Operation
) : Effect<NoCompileData>(id) {
    protected abstract fun getValue(config: Config, entity: LivingEntity): Double

    protected open fun canApplyTo(entity: LivingEntity): Boolean = true

    private fun AttributeInstance.clean(name: String, identifiers: Identifiers) {
        for (modifier in this.modifiers.toList()) {
            if (modifier.name == id || modifier.name == name || modifier.name == identifiers.key.key) {
                this.removeModifier(modifier)
            }
        }
    }

    open fun constrainAttribute(entity: LivingEntity, value: Double) {
        // Override this to constrain the attribute value, e.g. to set health below max health.
    }

    /**
     * Runs an attribute change on the thread that owns the entity: straight away when already on it,
     * and through the entity's scheduler only when not.
     *
     * A reload disables an effect and enables it again in the same call. Deferring only the enable
     * left the modifier off for a tick every time, which players saw as their speed (and field of
     * view) flickering whenever they switched items. Both directions go through here, so a change
     * that does have to be deferred keeps its order.
     */
    private fun LivingEntity.onOwnerThread(action: () -> Unit) {
        if (Bukkit.isOwnedByCurrentRegion(this)) {
            action()
        } else {
            this.scheduler.run(plugin, { action() }, {})
        }
    }

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val entity = dispatcher.get<LivingEntity>() ?: return

        if (!canApplyTo(entity)) {
            return
        }

        entity.onOwnerThread {
            val instance = entity.getAttribute(attribute) ?: return@onOwnerThread
            val modifierName = "libreforge:${this.id} - ${identifiers.key.key} (${holder.holder.id})"

            instance.clean(modifierName, identifiers)

            val modifier = attributeModifier(
                identifiers,
                modifierName,
                getValue(config, entity),
                operation
            )

            instance.removeModifier(modifier)
            instance.addModifier(modifier)
        }
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        val entity = dispatcher.get<LivingEntity>() ?: return

        if (!canApplyTo(entity)) {
            return
        }

        entity.onOwnerThread {
            val instance = entity.getAttribute(attribute) ?: return@onOwnerThread
            val modifierName = "libreforge:${this.id} - ${identifiers.key.key} (${holder.holder.id})"

            instance.clean(modifierName, identifiers)

            instance.removeModifier(
                attributeModifier(
                    identifiers,
                    modifierName,
                    0.0,
                    operation
                )
            )

            // Run on next tick to prevent constraining to the lower value during reloads.
            entity.scheduler.run(
                plugin,
                { constrainAttribute(entity, instance.value) },
                {}
            )
        }
    }

    private fun attributeModifier(
        identifiers: Identifiers,
        name: String,
        value: Double,
        operation: AttributeModifier.Operation
    ) = AttributeModifier(
        identifiers.key,
        value,
        operation,
        EquipmentSlotGroup.ANY
    )
}