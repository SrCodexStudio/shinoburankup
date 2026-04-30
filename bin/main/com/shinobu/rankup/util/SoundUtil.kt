package com.shinobu.rankup.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Sound effect utilities for the GUI system.
 * Provides immersive audio feedback for all interactions.
 *
 * All sounds are played at the player's location with appropriate volume and pitch.
 * Designed to be non-intrusive while providing clear feedback.
 */
object SoundUtil {

    /**
     * Gets the plugin instance safely.
     */
    private fun getPlugin(): Plugin? {
        return Bukkit.getPluginManager().getPlugin("ShinobuRankup")
    }

    /**
     * Plays a sound effect at the player's location.
     *
     * @param player The player to play the sound for
     * @param sound The sound to play
     * @param volume Volume of the sound (0.0 - 2.0, default 1.0)
     * @param pitch Pitch of the sound (0.5 - 2.0, default 1.0)
     * @param category Sound category for volume settings
     */
    fun playSound(
        player: Player,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        category: SoundCategory = SoundCategory.MASTER
    ) {
        player.playSound(player.location, sound, category, volume, pitch)
    }

    /**
     * Plays a sound effect at a specific location.
     */
    fun playSoundAt(
        location: Location,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        category: SoundCategory = SoundCategory.MASTER
    ) {
        location.world?.playSound(location, sound, category, volume, pitch)
    }

    /**
     * Plays the rankup success sound sequence.
     * A triumphant combination of level up and note sounds.
     */
    fun playRankupSuccess(player: Player) {
        val plugin = getPlugin() ?: return

        // Primary level up sound
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        // Delayed firework sound for extra celebration
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (player.isOnline) {
                    playSound(player, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f)
                }
            },
            5L // 0.25 seconds delay
        )

        // Additional celebratory chime
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (player.isOnline) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.5f)
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.8f)
                }
            },
            10L // 0.5 seconds delay
        )
    }

    /**
     * Plays the rankup failure sound.
     * A clear but non-harsh indication that something went wrong.
     */
    fun playRankupFail(player: Player) {
        val plugin = getPlugin() ?: return

        // Villager "no" sound - universally understood as rejection
        playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)

        // Subtle bass note for emphasis
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (player.isOnline) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f)
                }
            },
            3L
        )
    }

    /**
     * Plays GUI open sound.
     * A satisfying chest/book open sound.
     */
    fun playGuiOpen(player: Player) {
        playSound(player, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f)
    }

    /**
     * Plays GUI close sound.
     * Softer than open to not be jarring.
     */
    fun playGuiClose(player: Player) {
        playSound(player, Sound.BLOCK_CHEST_CLOSE, 0.3f, 1.1f)
    }

    /**
     * Plays a click sound for button interactions.
     * Quick and responsive.
     */
    fun playClick(player: Player) {
        playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
    }

    /**
     * Plays a denied/error click sound.
     * Used when player tries to do something they can't.
     */
    fun playDenied(player: Player) {
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f)
    }

    /**
     * Plays a success/confirm sound.
     * Positive feedback for successful actions.
     */
    fun playSuccess(player: Player) {
        playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f)
    }

    /**
     * Plays page turn/navigation sound.
     * Used for pagination.
     */
    fun playPageTurn(player: Player) {
        playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f)
    }

    /**
     * Plays a notification/alert sound.
     * Draws attention without being annoying.
     */
    fun playNotification(player: Player) {
        playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.0f)
    }

    /**
     * Plays a coin/money sound.
     * Used for financial transactions.
     */
    fun playMoney(player: Player) {
        playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f)
    }

    /**
     * Plays an ascending note sequence for progression.
     */
    fun playProgressUp(player: Player) {
        val plugin = getPlugin() ?: return
        val notes = listOf(0.5f, 0.7f, 0.9f, 1.1f, 1.3f)

        notes.forEachIndexed { index, pitch ->
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (player.isOnline) {
                        playSound(player, Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, pitch)
                    }
                },
                (index * 2).toLong()
            )
        }
    }

    /**
     * Plays a descending note sequence for demotion/failure.
     */
    fun playProgressDown(player: Player) {
        val plugin = getPlugin() ?: return
        val notes = listOf(1.3f, 1.1f, 0.9f, 0.7f, 0.5f)

        notes.forEachIndexed { index, pitch ->
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (player.isOnline) {
                        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, pitch)
                    }
                },
                (index * 2).toLong()
            )
        }
    }

    /**
     * Plays ambient hover sound.
     * Very subtle, for menu item hovering.
     */
    fun playHover(player: Player) {
        playSound(player, Sound.UI_BUTTON_CLICK, 0.2f, 1.5f)
    }

    /**
     * Plays unlock achievement sound.
     */
    fun playUnlock(player: Player) {
        playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f)
    }

    /**
     * Plays a warning sound.
     */
    fun playWarning(player: Player) {
        val plugin = getPlugin() ?: return
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f)
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (player.isOnline) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f)
                }
            },
            5L
        )
    }
}

/**
 * Enum for predefined sound effects with configurations.
 */
enum class GuiSound(
    val sound: Sound,
    val volume: Float,
    val pitch: Float
) {
    CLICK(Sound.UI_BUTTON_CLICK, 0.5f, 1.0f),
    OPEN(Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f),
    CLOSE(Sound.BLOCK_CHEST_CLOSE, 0.3f, 1.1f),
    SUCCESS(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f),
    DENIED(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f),
    PAGE_TURN(Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f),
    NOTIFICATION(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.0f),
    LEVELUP(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f),
    VILLAGER_NO(Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

    /**
     * Plays this sound for the given player.
     */
    fun play(player: Player) {
        SoundUtil.playSound(player, sound, volume, pitch)
    }
}

/**
 * Extension function to easily play sounds for players.
 */
fun Player.playGuiSound(guiSound: GuiSound) {
    guiSound.play(this)
}
