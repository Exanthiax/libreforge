package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryOpenEvent

object TriggerOpenInventory : Trigger("open_inventory") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.EVENT,
    )

    @EventHandler(ignoreCancelled = true)
    fun handle(event: InventoryOpenEvent) {

        val player = event.player
        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player as Player,
                event = event
            )
        )
    }
}