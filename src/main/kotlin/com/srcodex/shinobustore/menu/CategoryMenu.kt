package com.srcodex.shinobustore.menu

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.util.ColorUtil
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * Menu displaying all store categories.
 */
class CategoryMenu(
    plugin: ShinobuStore,
    player: Player
) : BaseMenu(
    plugin,
    player,
    plugin.configManager.categoryMenuTitle,
    plugin.configManager.categoryMenuRows
) {

    override fun build() {
        val categories = plugin.configManager.categories

        for ((categoryId, category) in categories) {
            // Skip invalid positions
            if (category.position < 0 || category.position >= menuInventory.size) {
                plugin.logger.warning("Invalid position ${category.position} for category $categoryId")
                continue
            }

            // Create category item
            val item = createItem(
                materialString = category.material,
                name = category.display,
                lore = category.lore
            )

            // Set item with click handler
            setItem(category.position, item) {
                openCategoryStore(categoryId, category.display, category.rows)
            }
        }
    }

    override fun onClick(event: InventoryClickEvent) {
        // Cancel event to prevent item extraction
        event.isCancelled = true

        val slot = event.rawSlot

        // Check if slot is in our menuInventory
        if (slot < 0 || slot >= menuInventory.size) {
            return
        }

        // Check if interactive slot
        if (!interactiveSlots.contains(slot)) {
            return
        }

        // Play click sound
        playClickSound()

        // Process click
        if (processClick(slot)) {
            return
        }

        // Fallback: find category by position
        val categories = plugin.configManager.categories
        for ((categoryId, category) in categories) {
            if (category.position == slot) {
                openCategoryStore(categoryId, category.display, category.rows)
                return
            }
        }
    }

    /**
     * Opens the store menu for a specific category.
     */
    private fun openCategoryStore(categoryId: String, categoryDisplay: String, rows: Int) {
        player.closeInventory()

        // Open store menu
        StoreMenu(plugin, player, categoryId, categoryDisplay, rows).open()
    }
}
