package com.srcodex.shinobustore.menu

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.transaction.StoreItem
import com.srcodex.shinobustore.util.ColorUtil
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.Locale

/**
 * Confirmation menu shown before executing a purchase.
 * Displays item details with cost breakdown and confirm/cancel buttons.
 */
class ConfirmMenu(
    plugin: ShinobuStore,
    player: Player,
    private val item: StoreItem,
    private val categoryId: String
) : BaseMenu(plugin, player, "&8Confirm Purchase", 3) {

    override fun build() {
        // Display item in center (slot 13) with cost breakdown
        val cost = item.cost
        val total = item.calculateTotal()
        val displayItem = createItem(
            materialString = item.material,
            name = item.display,
            lore = listOf(
                "",
                "&7Price: &f${plugin.configManager.currencySymbol}${String.format(Locale.US, "%.2f", cost)}",
                "&7Total: &f${plugin.configManager.currencySymbol}${String.format(Locale.US, "%.2f", total)}",
                "",
                "&7Are you sure you want to",
                "&7purchase this item?"
            )
        )
        setItem(13, displayItem, false)

        // Confirm button (slot 11) - lime wool with enchant glow
        val confirmItem = ItemStack(Material.LIME_WOOL, 1).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(ColorUtil.colorize("&a&lCONFIRM"))
                meta.lore = ColorUtil.colorize(listOf("&7Click to confirm purchase"))
                meta.addEnchant(Enchantment.DURABILITY, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }
        setItem(11, confirmItem) {
            player.closeInventory()
            StoreMenu.executePurchase(plugin, player, item)
        }

        // Cancel button (slot 15) - red wool
        val cancelItem = ItemStack(Material.RED_WOOL, 1).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(ColorUtil.colorize("&c&lCANCEL"))
                meta.lore = ColorUtil.colorize(listOf("&7Click to cancel"))
            }
        }
        setItem(15, cancelItem) {
            player.closeInventory()
            player.sendMessage(plugin.configManager.getMessage(
                "store.purchase-cancelled",
                mapOf("item" to item.display)
            ))
        }
    }

    override fun onClick(event: InventoryClickEvent) {
        // Cancel event to prevent item extraction
        event.isCancelled = true

        val slot = event.rawSlot

        // Check if slot is in our menu
        if (slot < 0 || slot >= menuInventory.size) {
            return
        }

        // Only respond to interactive slots
        if (!interactiveSlots.contains(slot)) {
            return
        }

        // Play click sound
        playClickSound()

        // Process registered click handlers (confirm / cancel)
        processClick(slot)
    }
}
