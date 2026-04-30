package com.srcodex.shinobustore.menu

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for all plugin menus.
 * Provides common functionality and protection against item extraction.
 */
abstract class BaseMenu(
    protected val plugin: ShinobuStore,
    protected val player: Player,
    title: String,
    rows: Int
) : InventoryHolder {

    private val _menuInventory: Inventory = Bukkit.createInventory(this, rows * 9, ColorUtil.colorize(title))
    protected val menuInventory: Inventory get() = _menuInventory

    // Track which slots are interactive (can be clicked)
    protected val interactiveSlots = mutableSetOf<Int>()

    // Track menu-specific data
    protected val menuData = mutableMapOf<String, Any>()

    init {
        // Register this menu
        openMenus[player.uniqueId] = this
    }

    /**
     * Opens the menu for the player.
     */
    fun open() {
        // Build the menu content
        build()

        // Fill empty slots if enabled
        if (plugin.configManager.fillerEnabled) {
            fillEmptySlots()
        }

        // Open on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.openInventory(menuInventory)

            // Play open sound
            try {
                val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundMenuOpen)
                player.playSound(player.location, sound, 1.0f, 1.0f)
            } catch (_: Exception) {}
        })
    }

    /**
     * Builds the menu content. Override in subclasses.
     */
    protected abstract fun build()

    /**
     * Handles a click event. Override in subclasses.
     */
    abstract fun onClick(event: InventoryClickEvent)

    /**
     * Called when the menu is closed.
     */
    open fun onClose(event: InventoryCloseEvent) {
        openMenus.remove(player.uniqueId)
    }

    /**
     * Sets an item in a slot.
     */
    protected fun setItem(slot: Int, item: ItemStack, interactive: Boolean = true) {
        if (slot in 0 until menuInventory.size) {
            menuInventory.setItem(slot, item)
            if (interactive) {
                interactiveSlots.add(slot)
            }
        }
    }

    /**
     * Sets an item with click handler.
     */
    protected fun setItem(slot: Int, item: ItemStack, clickHandler: () -> Unit) {
        setItem(slot, item, true)
        clickHandlers[slot] = clickHandler
    }

    private val clickHandlers = mutableMapOf<Int, () -> Unit>()

    /**
     * Processes a click and returns true if handled.
     */
    protected fun processClick(slot: Int): Boolean {
        val handler = clickHandlers[slot]
        if (handler != null) {
            handler()
            return true
        }
        return false
    }

    /**
     * Fills empty slots with filler item.
     */
    private fun fillEmptySlots() {
        val fillerMaterial = try {
            Material.valueOf(plugin.configManager.fillerMaterial)
        } catch (_: Exception) {
            Material.GRAY_STAINED_GLASS_PANE
        }

        val fillerItem = ItemStack(fillerMaterial).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(ColorUtil.colorize(plugin.configManager.fillerName))
            }
        }

        for (i in 0 until menuInventory.size) {
            if (menuInventory.getItem(i) == null) {
                menuInventory.setItem(i, fillerItem.clone())
            }
        }
    }

    /**
     * Creates an item builder helper.
     */
    protected fun createItem(
        material: Material,
        name: String,
        lore: List<String> = emptyList(),
        amount: Int = 1
    ): ItemStack {
        return ItemStack(material, amount).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(ColorUtil.colorize(name))
                if (lore.isNotEmpty()) {
                    meta.lore = ColorUtil.colorize(lore)
                }
            }
        }
    }

    /**
     * Creates an item from a material string (supports "head:<name>").
     */
    protected fun createItem(
        materialString: String,
        name: String,
        lore: List<String> = emptyList(),
        amount: Int = 1
    ): ItemStack {
        return if (materialString.startsWith("head:", ignoreCase = true)) {
            val ownerName = materialString.substringAfter(":")
            val actualOwner = if (ownerName.equals("auto", ignoreCase = true)) {
                player.name
            } else {
                ownerName
            }
            createPlayerHead(actualOwner, name, lore)
        } else {
            val material = try {
                Material.valueOf(materialString.uppercase())
            } catch (_: Exception) {
                Material.STONE
            }
            createItem(material, name, lore, amount)
        }
    }

    /**
     * Creates a player head item.
     */
    private fun createPlayerHead(owner: String, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD, 1)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        if (meta != null) {
            meta.owningPlayer = Bukkit.getOfflinePlayer(owner)
            meta.setDisplayName(ColorUtil.colorize(name))
            if (lore.isNotEmpty()) {
                meta.lore = ColorUtil.colorize(lore)
            }
            item.itemMeta = meta
        }
        return item
    }

    /**
     * Adds an enchantment glow effect to an item (hidden enchant).
     */
    protected fun addGlow(item: ItemStack): ItemStack {
        item.addUnsafeEnchantment(Enchantment.DURABILITY, 1)
        val meta = item.itemMeta
        meta?.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    /**
     * Plays click sound.
     */
    protected fun playClickSound() {
        try {
            val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundMenuClick)
            player.playSound(player.location, sound, 1.0f, 1.0f)
        } catch (_: Exception) {}
    }

    /**
     * Plays error sound.
     */
    protected fun playErrorSound() {
        try {
            val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundError)
            player.playSound(player.location, sound, 1.0f, 1.0f)
        } catch (_: Exception) {}
    }

    override fun getInventory(): Inventory = _menuInventory

    companion object {
        // Track all open menus
        private val openMenus = ConcurrentHashMap<UUID, BaseMenu>()

        /**
         * Gets the open menu for a player.
         */
        fun getOpenMenu(player: Player): BaseMenu? = openMenus[player.uniqueId]

        /**
         * Checks if a player has an open menu.
         */
        fun hasOpenMenu(player: Player): Boolean = openMenus.containsKey(player.uniqueId)

        /**
         * Closes all open menus.
         */
        fun closeAll() {
            openMenus.keys.toList().forEach { uuid ->
                Bukkit.getPlayer(uuid)?.closeInventory()
            }
            openMenus.clear()
        }

        /**
         * Removes a menu from tracking.
         */
        fun remove(player: Player) {
            openMenus.remove(player.uniqueId)
        }
    }
}
