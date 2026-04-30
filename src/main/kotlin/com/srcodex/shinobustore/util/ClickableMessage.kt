package com.srcodex.shinobustore.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

/**
 * Builder class for creating interactive clickable messages.
 * Uses Adventure API for modern text components.
 */
class ClickableMessage private constructor() {

    private val components = mutableListOf<Component>()
    private var currentComponent: Component? = null

    companion object {
        private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

        /**
         * Creates a new ClickableMessage with initial text.
         */
        fun create(text: String): ClickableMessage {
            return ClickableMessage().apply {
                currentComponent = legacySerializer.deserialize(ColorUtil.colorize(text))
            }
        }

        /**
         * Creates an empty ClickableMessage.
         */
        fun empty(): ClickableMessage = ClickableMessage()
    }

    /**
     * Adds hover text to the current component.
     */
    fun hover(text: String): ClickableMessage {
        currentComponent = currentComponent?.let {
            val hoverText = legacySerializer.deserialize(ColorUtil.colorize(text))
            it.hoverEvent(HoverEvent.showText(hoverText))
        }
        return this
    }

    /**
     * Adds a click event to run a command.
     */
    fun command(command: String): ClickableMessage {
        currentComponent = currentComponent?.let {
            val cmd = if (command.startsWith("/")) command else "/$command"
            it.clickEvent(ClickEvent.runCommand(cmd))
        }
        return this
    }

    /**
     * Adds a click event to suggest a command.
     */
    fun suggest(command: String): ClickableMessage {
        currentComponent = currentComponent?.let {
            val cmd = if (command.startsWith("/")) command else "/$command"
            it.clickEvent(ClickEvent.suggestCommand(cmd))
        }
        return this
    }

    /**
     * Adds a click event to open a URL.
     */
    fun link(url: String): ClickableMessage {
        currentComponent = currentComponent?.let {
            if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) {
                it.clickEvent(ClickEvent.openUrl(url))
            } else {
                it // Ignore invalid URLs
            }
        }
        return this
    }

    /**
     * Adds a click event to copy text to clipboard.
     */
    fun copy(text: String): ClickableMessage {
        currentComponent = currentComponent?.let {
            it.clickEvent(ClickEvent.copyToClipboard(text))
        }
        return this
    }

    /**
     * Commits the current component and starts a new one.
     */
    fun add(text: String): ClickableMessage {
        currentComponent?.let { components.add(it) }
        currentComponent = legacySerializer.deserialize(ColorUtil.colorize(text))
        return this
    }

    /**
     * Adds a space separator.
     */
    fun space(): ClickableMessage {
        currentComponent?.let { components.add(it) }
        currentComponent = Component.text(" ")
        return this
    }

    /**
     * Adds a newline.
     */
    fun newLine(): ClickableMessage {
        currentComponent?.let { components.add(it) }
        currentComponent = Component.newline()
        return this
    }

    /**
     * Builds the final component.
     */
    fun build(): Component {
        currentComponent?.let { components.add(it) }

        return if (components.isEmpty()) {
            Component.empty()
        } else {
            var result = components.first()
            for (i in 1 until components.size) {
                result = result.append(components[i])
            }
            result
        }
    }

    /**
     * Sends the message to a player.
     */
    fun send(player: Player) {
        player.sendMessage(build())
    }

    /**
     * Sends the message to multiple players.
     */
    fun send(players: Collection<Player>) {
        val component = build()
        players.forEach { it.sendMessage(component) }
    }
}

/**
 * Extension function to easily send clickable messages.
 */
fun Player.sendClickable(builder: ClickableMessage.() -> Unit) {
    val message = ClickableMessage.empty().apply(builder)
    message.send(this)
}
