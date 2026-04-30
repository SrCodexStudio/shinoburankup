package com.srcodex.shinobustore.listener

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.menu.BaseMenu
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listener that protects menu inventories from item extraction and duplication.
 * Implements comprehensive protection against all known duplication methods.
 */
class MenuProtectionListener(private val plugin: ShinobuStore) : Listener {

    /**
     * Handles inventory click events.
     * Prevents item extraction from plugin menus.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder

        // Check if this is one of our menus
        if (holder !is BaseMenu) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        val menu = holder

        // ALWAYS cancel the event first for safety
        event.isCancelled = true

        // Protection checks if enabled
        if (plugin.configManager.menuProtectionEnabled) {
            // Block shift-click (can be used to quickly move items)
            if (event.isShiftClick) {
                return
            }

            // Block number keys (can swap items to hotbar)
            if (event.click == ClickType.NUMBER_KEY) {
                return
            }

            // Block drop key while in inventory
            if (event.click == ClickType.DROP || event.click == ClickType.CONTROL_DROP) {
                return
            }

            // Block offhand swap
            if (event.click == ClickType.SWAP_OFFHAND) {
                return
            }

            // Block double click (can collect items)
            if (event.click == ClickType.DOUBLE_CLICK) {
                return
            }

            // Block middle click (creative mode item clone)
            if (event.click == ClickType.MIDDLE) {
                return
            }

            // Block creative mode actions
            if (event.click == ClickType.CREATIVE) {
                return
            }
        }

        // Only process clicks in the top inventory (our menu)
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        // Delegate to menu handler
        try {
            menu.onClick(event)
        } catch (e: Exception) {
            plugin.logger.warning("Error handling menu click: ${e.message}")
            if (plugin.configManager.debugEnabled) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Handles inventory drag events.
     * Prevents item dragging into plugin menus.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder

        // Check if this is one of our menus
        if (holder !is BaseMenu) {
            return
        }

        // Cancel all drag events in our menus
        event.isCancelled = true
    }

    /**
     * Handles inventory close events.
     * Cleans up menu state.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder

        // Check if this is one of our menus
        if (holder !is BaseMenu) {
            return
        }

        val player = event.player as? Player ?: return
        val menu = holder

        try {
            menu.onClose(event)
        } catch (e: Exception) {
            plugin.logger.warning("Error handling menu close: ${e.message}")
        }

        // Remove from tracking
        BaseMenu.remove(player)
    }

    /**
     * Handles inventory move item events.
     * Prevents hoppers/other inventories from extracting items.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        // Check if source or destination is one of our menus
        if (event.source.holder is BaseMenu || event.destination.holder is BaseMenu) {
            event.isCancelled = true
        }
    }

    /**
     * Handles player quit events.
     * Ensures proper cleanup.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        BaseMenu.remove(event.player)
    }

    /**
     * Handles inventory open events.
     * Additional safety check.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val holder = event.inventory.holder

        if (holder !is BaseMenu) {
            return
        }

        // Clear cursor just in case
        val player = event.player as? Player ?: return
        if (!player.itemOnCursor.type.isAir) {
            // Give item back to player
            val remaining = player.inventory.addItem(player.itemOnCursor)
            if (remaining.isNotEmpty()) {
                // Drop if inventory is full
                remaining.values.forEach { item ->
                    player.world.dropItemNaturally(player.location, item)
                }
            }
            player.setItemOnCursor(null)
        }
    }
}
