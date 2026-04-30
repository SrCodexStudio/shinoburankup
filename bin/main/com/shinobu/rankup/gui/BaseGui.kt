package com.shinobu.rankup.gui

import com.shinobu.rankup.util.GlassColor
import com.shinobu.rankup.util.ItemBuilder
import com.shinobu.rankup.util.SoundUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Abstract base class for all GUIs in ShinobuRankup.
 *
 * Provides comprehensive anti-dupe protection and common GUI utilities.
 *
 * ANTI-DUPE PROTECTION:
 * - All click events are cancelled by default
 * - Drag events are blocked completely
 * - Shift-click, number keys, and hotbar swaps are blocked
 * - Drop events are blocked while GUI is open
 * - Click cooldown prevents rapid clicking exploits
 * - Inventory holder pattern prevents external manipulation
 *
 * FEATURES:
 * - Fluent API for building GUIs
 * - Sound effect utilities
 * - Progress bar generation
 * - Pagination support
 * - Slot calculation utilities
 */
abstract class BaseGui(
    protected val player: Player,
    protected val title: String,
    protected val rows: Int = 6
) : InventoryHolder {

    /**
     * The inventory instance for this GUI.
     * Lazily created on first access.
     */
    private val _inventory: Inventory by lazy {
        Bukkit.createInventory(this, rows * 9, ItemBuilder.colorize(title))
    }

    /**
     * Protected accessor for subclasses.
     */
    protected val guiInventory: Inventory get() = _inventory

    /**
     * Map of slot indices to click handlers.
     * Allows defining custom behavior per slot.
     */
    protected val clickHandlers: MutableMap<Int, ClickHandler> = mutableMapOf()

    /**
     * Default handler for clicks outside defined handlers.
     */
    protected var defaultClickHandler: ClickHandler? = null

    /**
     * Whether sounds should be played on interactions.
     */
    protected var soundsEnabled: Boolean = true

    /**
     * GUI manager reference.
     */
    protected val guiManager: GuiManager = GuiManager.getInstance()

    /**
     * Functional interface for click handlers.
     */
    fun interface ClickHandler {
        /**
         * Handles a click event in the GUI.
         *
         * @param event The click event (already cancelled)
         * @param clickType The type of click
         * @return True if the click was handled successfully
         */
        fun handle(event: InventoryClickEvent, clickType: ClickType): Boolean
    }

    /**
     * Returns this GUI's inventory.
     * Part of InventoryHolder interface - critical for anti-dupe.
     */
    override fun getInventory(): Inventory = _inventory

    /**
     * Opens the GUI for the player.
     * Handles registration and sound playing.
     */
    open fun open() {
        // Build the GUI content
        build()

        // Register with manager before opening
        guiManager.registerGui(player, this)

        // Open inventory on main thread
        if (Bukkit.isPrimaryThread()) {
            player.openInventory(_inventory)
        } else {
            Bukkit.getScheduler().runTask(guiManager.getPlugin()) { _ ->
                player.openInventory(_inventory)
            }
        }

        // Play open sound
        if (soundsEnabled) {
            SoundUtil.playGuiOpen(player)
        }
    }

    /**
     * Closes the GUI for the player.
     */
    open fun close() {
        guiManager.closeGui(player)
    }

    /**
     * Called when the inventory is closed.
     * Override to add custom cleanup logic.
     */
    open fun onClose() {
        if (soundsEnabled) {
            SoundUtil.playGuiClose(player)
        }
    }

    /**
     * Abstract method to build the GUI content.
     * Implementations should populate the inventory here.
     */
    protected abstract fun build()

    /**
     * Handles a click event in this GUI.
     * This is the main entry point from GuiListener.
     *
     * ANTI-DUPE: Event is ALWAYS cancelled before this is called.
     *
     * @param event The click event
     * @return True if the click was handled
     */
    open fun handleClick(event: InventoryClickEvent): Boolean {
        val slot = event.rawSlot
        val clickType = event.click

        // Ignore clicks outside the GUI inventory
        if (slot < 0 || slot >= _inventory.size) {
            return false
        }

        // Check click cooldown
        if (!guiManager.canClick(player)) {
            return false
        }

        // Try slot-specific handler first
        val handler = clickHandlers[slot]
        if (handler != null) {
            if (soundsEnabled) {
                SoundUtil.playClick(player)
            }
            return handler.handle(event, clickType)
        }

        // Try default handler
        defaultClickHandler?.let { default ->
            return default.handle(event, clickType)
        }

        return false
    }

    // ==================== SLOT UTILITIES ====================

    /**
     * Sets an item in the inventory at the specified slot.
     */
    protected fun setItem(slot: Int, item: ItemStack?) {
        if (slot in 0 until _inventory.size) {
            _inventory.setItem(slot, item)
        }
    }

    /**
     * Sets an item with a click handler.
     */
    protected fun setItem(slot: Int, item: ItemStack?, handler: ClickHandler) {
        setItem(slot, item)
        clickHandlers[slot] = handler
    }

    /**
     * Sets an item using an ItemBuilder.
     */
    protected fun setItem(slot: Int, builder: ItemBuilder) {
        setItem(slot, builder.build())
    }

    /**
     * Sets an item using an ItemBuilder with a click handler.
     */
    protected fun setItem(slot: Int, builder: ItemBuilder, handler: ClickHandler) {
        setItem(slot, builder.build(), handler)
    }

    /**
     * Fills a range of slots with an item.
     */
    protected fun fillSlots(slots: IntRange, item: ItemStack?) {
        slots.forEach { slot -> setItem(slot, item) }
    }

    /**
     * Fills a range of slots with an item and handler.
     */
    protected fun fillSlots(slots: IntRange, item: ItemStack?, handler: ClickHandler) {
        slots.forEach { slot -> setItem(slot, item, handler) }
    }

    /**
     * Fills specific slots with an item.
     */
    protected fun fillSlots(slots: List<Int>, item: ItemStack?) {
        slots.forEach { slot -> setItem(slot, item) }
    }

    /**
     * Converts row and column to slot index.
     * Rows and columns are 0-indexed.
     */
    protected fun toSlot(row: Int, col: Int): Int = row * 9 + col

    /**
     * Gets slots for an entire row.
     */
    protected fun getRowSlots(row: Int): IntRange = (row * 9) until ((row + 1) * 9)

    /**
     * Gets slots for a column.
     */
    protected fun getColumnSlots(col: Int): List<Int> = (0 until rows).map { it * 9 + col }

    /**
     * Gets the border slots (first row, last row, first col, last col).
     */
    protected fun getBorderSlots(): List<Int> {
        val slots = mutableListOf<Int>()

        // First row
        slots.addAll(getRowSlots(0).toList())

        // Last row
        slots.addAll(getRowSlots(rows - 1).toList())

        // First and last columns (excluding corners already added)
        for (row in 1 until rows - 1) {
            slots.add(toSlot(row, 0))
            slots.add(toSlot(row, 8))
        }

        return slots
    }

    /**
     * Gets the inner slots (excluding border).
     */
    protected fun getInnerSlots(): List<Int> {
        val slots = mutableListOf<Int>()
        for (row in 1 until rows - 1) {
            for (col in 1 until 8) {
                slots.add(toSlot(row, col))
            }
        }
        return slots
    }

    // ==================== DECORATION UTILITIES ====================

    /**
     * Fills the border with a glass pane pattern.
     */
    protected fun fillBorder(color: GlassColor = GlassColor.GRAY) {
        val item = ItemBuilder.glassPane(color)
            .name(" ")
            .build()
        fillSlots(getBorderSlots(), item)
    }

    /**
     * Fills the border with a gradient pattern.
     */
    protected fun fillGradientBorder(colors: List<GlassColor> = GlassColor.gradient()) {
        val borderSlots = getBorderSlots()
        borderSlots.forEachIndexed { index, slot ->
            val color = colors[index % colors.size]
            setItem(slot, ItemBuilder.glassPane(color).name(" ").build())
        }
    }

    /**
     * Fills the top row with a gradient.
     */
    protected fun fillTopGradient(colors: List<GlassColor> = GlassColor.gradient()) {
        for (col in 0 until 9) {
            val color = colors[col % colors.size]
            setItem(toSlot(0, col), ItemBuilder.glassPane(color).name(" ").build())
        }
    }

    /**
     * Fills the bottom row with a gradient.
     */
    protected fun fillBottomGradient(colors: List<GlassColor> = GlassColor.gradient()) {
        for (col in 0 until 9) {
            val color = colors[col % colors.size]
            setItem(toSlot(rows - 1, col), ItemBuilder.glassPane(color).name(" ").build())
        }
    }

    /**
     * Creates a filler item (empty glass pane).
     */
    protected fun fillerItem(color: GlassColor = GlassColor.GRAY): ItemStack {
        return ItemBuilder.glassPane(color).name(" ").build()
    }

    // ==================== PROGRESS BAR UTILITIES ====================

    /**
     * Creates a text-based progress bar.
     *
     * @param current Current progress value
     * @param max Maximum progress value
     * @param length Length of the bar in characters
     * @param filledChar Character for filled portion
     * @param emptyChar Character for empty portion
     * @param filledColor Color code for filled portion
     * @param emptyColor Color code for empty portion
     * @return Formatted progress bar string
     */
    protected fun createProgressBar(
        current: Double,
        max: Double,
        length: Int = 10,
        filledChar: Char = '█',
        emptyChar: Char = '░',
        filledColor: String = "&a",
        emptyColor: String = "&7",
        bracketColor: String = "&8"
    ): String {
        val progress = if (max > 0) (current / max).coerceIn(0.0, 1.0) else 0.0
        val filledLength = (progress * length).toInt()
        val emptyLength = length - filledLength

        val filled = filledChar.toString().repeat(filledLength)
        val empty = emptyChar.toString().repeat(emptyLength)

        return "$bracketColor[$filledColor$filled$emptyColor$empty$bracketColor]"
    }

    /**
     * Creates a progress bar with percentage display.
     */
    protected fun createProgressBarWithPercent(
        current: Double,
        max: Double,
        length: Int = 10,
        percentColor: String = "&a"
    ): String {
        val percent = if (max > 0) ((current / max) * 100).coerceIn(0.0, 100.0) else 0.0
        val bar = createProgressBar(current, max, length)
        return "$bar $percentColor${String.format("%.1f", percent)}%"
    }

    // ==================== NAVIGATION UTILITIES ====================

    /**
     * Creates a navigation button.
     */
    protected fun createNavigationButton(
        type: NavigationType,
        enabled: Boolean = true
    ): ItemStack {
        return when (type) {
            NavigationType.PREVIOUS_PAGE -> {
                if (enabled) {
                    ItemBuilder.of(Material.ARROW)
                        .name("&e&l<< Previous Page")
                        .lore(
                            "&7Click to go to the",
                            "&7previous page."
                        )
                        .build()
                } else {
                    ItemBuilder.glassPane(GlassColor.GRAY)
                        .name("&7No Previous Page")
                        .build()
                }
            }
            NavigationType.NEXT_PAGE -> {
                if (enabled) {
                    ItemBuilder.of(Material.ARROW)
                        .name("&e&lNext Page >>")
                        .lore(
                            "&7Click to go to the",
                            "&7next page."
                        )
                        .build()
                } else {
                    ItemBuilder.glassPane(GlassColor.GRAY)
                        .name("&7No Next Page")
                        .build()
                }
            }
            NavigationType.CLOSE -> {
                ItemBuilder.of(Material.BARRIER)
                    .name("&c&lClose")
                    .lore(
                        "&7Click to close this",
                        "&7menu."
                    )
                    .build()
            }
            NavigationType.BACK -> {
                ItemBuilder.of(Material.ARROW)
                    .name("&c&l<< Back")
                    .lore(
                        "&7Click to go back to",
                        "&7the previous menu."
                    )
                    .build()
            }
            NavigationType.INFO -> {
                ItemBuilder.of(Material.BOOK)
                    .name("&b&lInformation")
                    .lore(
                        "&7Helpful information",
                        "&7about this menu."
                    )
                    .build()
            }
        }
    }

    /**
     * Sets up pagination controls in the bottom row.
     *
     * @param currentPage Current page (0-indexed)
     * @param totalPages Total number of pages
     * @param onPrevious Handler for previous page click
     * @param onNext Handler for next page click
     */
    protected fun setupPagination(
        currentPage: Int,
        totalPages: Int,
        onPrevious: () -> Unit,
        onNext: () -> Unit
    ) {
        val bottomRow = rows - 1

        // Previous page button (slot 0)
        val hasPrevious = currentPage > 0
        setItem(
            toSlot(bottomRow, 0),
            createNavigationButton(NavigationType.PREVIOUS_PAGE, hasPrevious)
        ) { _, _ ->
            if (hasPrevious) {
                SoundUtil.playPageTurn(player)
                onPrevious()
            } else {
                SoundUtil.playDenied(player)
            }
            true
        }

        // Page info (slot 4)
        setItem(
            toSlot(bottomRow, 4),
            ItemBuilder.of(Material.PAPER)
                .name("&e&lPage ${currentPage + 1} / $totalPages")
                .lore(
                    "&7You are viewing page",
                    "&f${currentPage + 1} &7of &f$totalPages&7."
                )
                .build()
        )

        // Next page button (slot 8)
        val hasNext = currentPage < totalPages - 1
        setItem(
            toSlot(bottomRow, 8),
            createNavigationButton(NavigationType.NEXT_PAGE, hasNext)
        ) { _, _ ->
            if (hasNext) {
                SoundUtil.playPageTurn(player)
                onNext()
            } else {
                SoundUtil.playDenied(player)
            }
            true
        }

        // Close button (slot 6 or 7)
        setItem(
            toSlot(bottomRow, 6),
            createNavigationButton(NavigationType.CLOSE)
        ) { _, _ ->
            close()
            true
        }
    }

    // ==================== FORMATTING UTILITIES ====================

    /**
     * Formats a number with thousands separators.
     */
    protected fun formatNumber(number: Number): String {
        return String.format("%,d", number.toLong())
    }

    /**
     * Formats a decimal number with specified precision.
     */
    protected fun formatDecimal(number: Double, decimals: Int = 2): String {
        return String.format("%,.${decimals}f", number)
    }

    /**
     * Formats money with currency symbol.
     */
    protected fun formatMoney(amount: Double): String {
        return "$${formatDecimal(amount)}"
    }

    /**
     * Creates a divider line for lore.
     */
    protected fun dividerLine(color: String = "&8", char: Char = '▬', length: Int = 20): String {
        return "$color${char.toString().repeat(length)}"
    }

    /**
     * Wraps text to multiple lines with a maximum length.
     */
    protected fun wrapText(text: String, maxLength: Int = 30, prefix: String = "&7"): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.length + word.length + 1 > maxLength) {
                lines.add("$prefix${currentLine.toString().trim()}")
                currentLine = StringBuilder()
            }
            currentLine.append("$word ")
        }

        if (currentLine.isNotEmpty()) {
            lines.add("$prefix${currentLine.toString().trim()}")
        }

        return lines
    }
}

/**
 * Navigation button types.
 */
enum class NavigationType {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    CLOSE,
    BACK,
    INFO
}
