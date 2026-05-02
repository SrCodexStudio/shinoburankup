package com.shinobu.rankup.gui.listener

import com.shinobu.rankup.gui.BaseGui
import com.shinobu.rankup.gui.GuiManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory

/**
 * Event listener providing MAXIMUM anti-dupe protection for all GUIs.
 *
 * ANTI-DUPE PROTECTION MECHANISMS:
 * 1. InventoryClickEvent - Cancels ALL clicks in our GUIs
 * 2. InventoryDragEvent - Cancels ALL drag operations
 * 3. InventoryMoveItemEvent - Cancels hopper/automation interactions
 * 4. PlayerDropItemEvent - Cancels drops while GUI is open
 * 5. InventoryCloseEvent - Proper cleanup and state management
 * 6. Number key swap protection
 * 7. Shift-click protection
 * 8. Double-click collection protection
 * 9. Creative mode middle-click protection
 *
 * This listener uses HIGHEST priority to ensure our cancellations
 * take precedence over other plugins.
 */
class GuiListener : Listener {

    private val guiManager: GuiManager by lazy { GuiManager.getInstance() }

    /**
     * Handles all inventory click events.
     * This is the PRIMARY anti-dupe protection mechanism.
     *
     * Priority: HIGHEST to ensure we process before other plugins
     * ignoreCancelled: false to handle already-cancelled events
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory ?: return

        // Get the top inventory (the GUI)
        val topInventory = event.view.topInventory

        // Check if the top inventory is one of our GUIs
        if (!isOurGui(topInventory)) {
            return
        }

        val gui = guiManager.getOpenGui(player) ?: return

        // CRITICAL: Cancel the event FIRST, before any processing
        event.isCancelled = true

        // Block ALL click types that could potentially dupe items
        when (event.click) {
            // Standard clicks in our inventory - allow handling
            ClickType.LEFT,
            ClickType.RIGHT -> {
                // Only handle if clicked in the top inventory (our GUI)
                if (event.rawSlot < topInventory.size) {
                    gui.handleClick(event)
                }
                // Clicks in player inventory while GUI open - just cancel
            }

            // BLOCKED: Shift clicking (can move items between inventories)
            ClickType.SHIFT_LEFT,
            ClickType.SHIFT_RIGHT -> {
                // Explicitly cancelled, do nothing
            }

            // BLOCKED: Number keys (hotbar swap)
            ClickType.NUMBER_KEY -> {
                // Explicitly cancelled, do nothing
            }

            // BLOCKED: Double click (collects items)
            ClickType.DOUBLE_CLICK -> {
                // Explicitly cancelled, do nothing
            }

            // BLOCKED: Middle click (creative clone)
            ClickType.MIDDLE,
            ClickType.CREATIVE -> {
                // Explicitly cancelled, do nothing
            }

            // BLOCKED: Drop actions
            ClickType.DROP,
            ClickType.CONTROL_DROP -> {
                // Explicitly cancelled, do nothing
            }

            // BLOCKED: Swap offhand
            ClickType.SWAP_OFFHAND -> {
                // Explicitly cancelled, do nothing
            }

            // Handle any other click type by just cancelling
            else -> {
                // Already cancelled, nothing to do
            }
        }
    }

    /**
     * Handles inventory drag events.
     * Dragging can be used to distribute items - block completely.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val topInventory = event.view.topInventory

        if (!isOurGui(topInventory)) {
            return
        }

        // Cancel ALL drags when a custom GUI is open (Fairy Library recommendation)
        event.isCancelled = true
    }

    /**
     * Handles inventory move item events (hoppers, etc.).
     * Prevents automation from interacting with our GUIs.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        // Block if either source or destination is our GUI
        if (isOurGui(event.source) || isOurGui(event.destination)) {
            event.isCancelled = true
        }
    }

    /**
     * Handles player drop item events.
     * Prevents dropping items while GUI is open.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player

        // If player has our GUI open, cancel drop
        if (guiManager.hasOpenGui(player)) {
            event.isCancelled = true
        }
    }

    /**
     * Handles inventory close events.
     * Performs cleanup when GUI is closed.
     *
     * IMPORTANT: We must verify that the closing inventory matches the registered GUI.
     * This prevents a race condition when switching between GUIs where:
     * 1. New GUI is registered (replaces old in map)
     * 2. New inventory opens
     * 3. Old inventory close event fires
     * 4. Without this check, we would unregister the NEW GUI instead of the old one
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val closingInventory = event.inventory

        // Check if this is our GUI
        if (!isOurGui(closingInventory)) {
            return
        }

        // Get the currently registered GUI for this player
        val registeredGui = guiManager.getOpenGui(player) ?: return

        // CRITICAL: Only unregister if the closing inventory matches the registered GUI's inventory
        // This prevents unregistering a newly opened GUI when an old one closes
        if (registeredGui.inventory !== closingInventory) {
            // The closing inventory is not the currently registered GUI
            // This happens when switching between GUIs - the old one closes after the new one registers
            return
        }

        // Safe to unregister - the closing inventory IS the registered one
        guiManager.unregisterGui(player)

        // Call the GUI's close handler
        registeredGui.onClose()
    }

    /**
     * Handles player quit events.
     * Ensures cleanup when player leaves with GUI open.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Clean up any open GUI
        guiManager.unregisterGui(player)
    }

    /**
     * Handles inventory open events.
     * Additional validation when inventory is opened.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        // This is primarily for monitoring/logging purposes
        // The actual GUI registration happens in BaseGui.open()
    }

    /**
     * Handles creative inventory events.
     * Additional protection for creative mode shenanigans.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onCreativeInventory(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return

        // If player has our GUI open, block creative inventory actions
        if (guiManager.hasOpenGui(player)) {
            event.isCancelled = true
        }
    }

    /**
     * Checks if an inventory belongs to one of our GUIs.
     *
     * This uses the InventoryHolder pattern - our GUIs implement
     * InventoryHolder and return themselves, making identification reliable.
     */
    private fun isOurGui(inventory: Inventory?): Boolean {
        if (inventory == null) return false
        return inventory.holder is BaseGui
    }
}

/**
 * Additional safety utilities for the GUI system.
 */
object GuiSafety {

    /**
     * Validates that an inventory operation is safe.
     * Can be used for additional checks before processing.
     */
    fun isOperationSafe(player: Player, inventory: Inventory): Boolean {
        // Check if player is in a valid state
        if (player.isDead) return false
        if (!player.isOnline) return false

        // Check if inventory is valid
        if (inventory.holder !is BaseGui) return false

        return true
    }

    /**
     * Performs a safety check before any item modification.
     */
    fun performSafetyCheck(player: Player): Boolean {
        // Verify player state
        if (player.isDead || !player.isOnline) {
            return false
        }

        // Verify GUI state
        val guiManager = GuiManager.getInstance()
        if (!guiManager.hasOpenGui(player)) {
            return false
        }

        return true
    }
}
