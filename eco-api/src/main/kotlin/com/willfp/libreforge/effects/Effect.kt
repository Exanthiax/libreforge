package com.willfp.libreforge.effects

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.core.integrations.economy.EconomyManager
import com.willfp.eco.util.NumberUtils
import com.willfp.eco.util.PlayerUtils
import com.willfp.eco.util.StringUtils
import com.willfp.libreforge.ConfigurableProperty
import com.willfp.libreforge.LibReforgePlugin
import com.willfp.libreforge.conditions.ConfiguredCondition
import com.willfp.libreforge.events.EffectActivateEvent
import com.willfp.libreforge.filters.Filter
import com.willfp.libreforge.triggers.InvocationData
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.UUID
import kotlin.math.ceil

abstract class Effect(
    id: String,
    val supportsFilters: Boolean = false,
    val applicableTriggers: Collection<Trigger> = emptyList()
) : ConfigurableProperty(id), Listener {
    private val cooldownTracker = mutableMapOf<UUID, MutableMap<UUID, Long>>()

    init {
        postInit()
    }

    private fun postInit() {
        Effects.addNewEffect(this)
    }

    fun getCooldown(player: Player, uuid: UUID): Int {
        val endTime = (cooldownTracker[player.uniqueId] ?: return 0)[uuid] ?: return 0
        val msLeft = endTime - System.currentTimeMillis()
        val secondsLeft = ceil(msLeft.toDouble() / 1000).toLong()
        return secondsLeft.toInt()
    }

    fun sendCooldownMessage(player: Player, uuid: UUID) {
        val cooldown = getCooldown(player, uuid)

        val message = plugin.langYml.getMessage("on-cooldown").replace("%seconds%", cooldown.toString())
        if (plugin.configYml.getBool("cooldown.in-actionbar")) {
            PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
        } else {
            player.sendMessage(message)
        }

        if (plugin.configYml.getBool("cooldown.sound.enabled")) {
            player.playSound(
                player.location,
                Sound.valueOf(plugin.configYml.getString("cooldown.sound.sound").uppercase()),
                1.0f,
                plugin.configYml.getDouble("cooldown.sound.pitch").toFloat()
            )
        }
    }

    fun sendCannotAffordMessage(player: Player, cost: Double) {
        val message = plugin.langYml.getMessage("cannot-afford").replace("%cost%", cost.toString())
        if (plugin.configYml.getBool("cannot-afford.in-actionbar")) {
            PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
        } else {
            player.sendMessage(message)
        }

        if (plugin.configYml.getBool("cannot-afford.sound.enabled")) {
            player.playSound(
                player.location,
                Sound.valueOf(plugin.configYml.getString("cannot-afford.sound.sound").uppercase()),
                1.0f,
                plugin.configYml.getDouble("cannot-afford.sound.pitch").toFloat()
            )
        }
    }

    fun sendCannotAffordTypeMessage(player: Player, cost: Double, type: String) {
        val message = plugin.langYml.getMessage("cannot-afford-type").replace("%cost%", cost.toString())
            .replace("%type%", type)
        if (plugin.configYml.getBool("cannot-afford-type.in-actionbar")) {
            PlayerUtils.getAudience(player).sendActionBar(StringUtils.toComponent(message))
        } else {
            player.sendMessage(message)
        }

        if (plugin.configYml.getBool("cannot-afford-type.sound.enabled")) {
            player.playSound(
                player.location,
                Sound.valueOf(plugin.configYml.getString("cannot-afford-type.sound.sound").uppercase()),
                1.0f,
                plugin.configYml.getDouble("cannot-afford-type.sound.pitch").toFloat()
            )
        }
    }

    fun resetCooldown(player: Player, config: Config, uuid: UUID) {
        if (!config.has("cooldown")) {
            return
        }
        val current = cooldownTracker[player.uniqueId] ?: mutableMapOf()
        current[uuid] = System.currentTimeMillis() + (config.getDoubleFromExpression("cooldown") * 1000L).toLong()
        cooldownTracker[player.uniqueId] = current
    }

    /**
     * Generate a UUID with a specified offset.
     *
     * @param offset The offset.
     * @return The UUID.
     */
    fun getUUID(
        offset: Int
    ): UUID {
        return UUID.nameUUIDFromBytes("$id$offset".toByteArray())
    }

    /**
     * Generate a NamespacedKey with a specified offset.
     *
     * @param offset The offset.
     * @return The NamespacedKey.
     */
    fun getNamespacedKey(
        offset: Int
    ): NamespacedKey {
        return this.plugin.namespacedKeyFactory.create("${id}_$offset")
    }

    /**
     * Handle application of this effect.
     *
     * @param player The player.
     * @param config The config.
     */
    fun enableForPlayer(
        player: Player,
        config: Config
    ) {
        player.pushEffect(this)
        handleEnable(player, config)
    }

    protected open fun handleEnable(
        player: Player,
        config: Config
    ) {
        // Override when needed.
    }

    /**
     * Handle removal of this effect.
     *
     * @param player The player.
     */
    fun disableForPlayer(player: Player) {
        handleDisable(player)
        player.popEffect(this)
    }

    protected open fun handleDisable(player: Player) {
        // Override when needed.
    }

    open fun handle(data: TriggerData, config: Config) {
        // Override when needed
    }

    open fun handle(invocation: InvocationData, config: Config) {
        // Override when needed
    }
}

private val everyHandler = mutableMapOf<UUID, MutableMap<UUID, Int>>()

data class ConfiguredEffect(
    val effect: Effect,
    val args: Config,
    val filter: Filter,
    val triggers: Collection<Trigger>,
    val uuid: UUID,
    val conditions: Collection<ConfiguredCondition>
) {
    operator fun invoke(invocation: InvocationData) {
        val (player, data, holder, trigger) = invocation

        var effectAreMet = true
        for ((condition, conditionConfig) in conditions) {
            if (!condition.isConditionMet(player, conditionConfig)) {
                effectAreMet = false
            }
        }

        if (!effectAreMet) {
            return
        }

        if (NumberUtils.randFloat(0.0, 100.0) > (args.getDoubleOrNull("chance") ?: 100.0)) {
            return
        }

        if (args.has("check_antigrief") && args.getBool("check_antigrief") && data.player != null && data.victim != null) {
            if (!AntigriefManager.canInjure(data.player, data.victim)) {
                return
            }
        }

        val every = args.getIntOrNull("every") ?: 0

        if (every > 0) {
            val everyMap = everyHandler[player.uniqueId] ?: mutableMapOf()
            var current = everyMap[uuid] ?: 0

            if (current != 0) {
                current++

                if (current >= every) {
                    current = 0
                }

                everyHandler[player.uniqueId] = everyMap.apply {
                    this[uuid] = current
                }

                return
            }
        }

        if (!triggers.contains(trigger)) {
            return
        }

        if (!filter.matches(data)) {
            return
        }

        if (effect.getCooldown(player, uuid) > 0) {
            if (args.getBoolOrNull("send_cooldown_message") != false) {
                effect.sendCooldownMessage(player, uuid)
            }
            return
        }

        if (args.has("cost")) {
            if (!EconomyManager.hasAmount(player, args.getDoubleFromExpression("cost"))) {
                effect.sendCannotAffordMessage(player, args.getDoubleFromExpression("cost"))
                return
            }

            EconomyManager.removeMoney(player, args.getDoubleFromExpression("cost"))
        }

        val activateEvent = EffectActivateEvent(player, holder, effect, args)
        LibReforgePlugin.instance.server.pluginManager.callEvent(activateEvent)

        if (!activateEvent.isCancelled) {
            effect.resetCooldown(player, args, uuid)

            effect.handle(data, args)
            effect.handle(invocation, args)
        }
    }
}

data class MultiplierModifier(val uuid: UUID, val multiplier: Double)
