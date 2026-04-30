package com.shinobu.rankup.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Level

/**
 * Service for managing player permissions on rankup.
 * Supports LuckPerms with fallback to Bukkit's permission attachment.
 *
 * LuckPerms integration uses reflection to avoid a compile-time dependency,
 * allowing the plugin to function even when LuckPerms is not installed.
 * This is a soft dependency pattern -- the service detects LuckPerms at
 * runtime and gracefully degrades to Bukkit permission attachments.
 *
 * Thread Safety:
 * - [luckPermsAvailable] is lazily initialized once and immutable thereafter.
 * - LuckPerms API calls are thread-safe by design (LuckPerms guarantees this).
 * - Bukkit permission attachments must be called from the main thread.
 *   Callers are responsible for ensuring main-thread execution when using
 *   the Bukkit fallback path.
 */
class PermissionService(private val plugin: Plugin) {

    /**
     * Lazy detection of LuckPerms availability.
     * Evaluated once on first access, cached for the plugin lifetime.
     */
    private val luckPermsAvailable: Boolean by lazy {
        Bukkit.getPluginManager().getPlugin("LuckPerms") != null
    }

    /**
     * Grant a permission to a player.
     * Uses LuckPerms if available, otherwise uses Bukkit permission attachment.
     *
     * @param player The target player
     * @param permission The permission node to grant (e.g. "rank.vip")
     */
    fun grantPermission(player: Player, permission: String) {
        if (permission.isBlank()) return

        try {
            if (luckPermsAvailable) {
                grantViaLuckPerms(player, permission)
            } else {
                grantViaBukkit(player, permission)
            }
            plugin.logger.fine("Granted permission '$permission' to ${player.name}")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to grant permission '$permission' to ${player.name}", e)
        }
    }

    /**
     * Revoke a permission from a player.
     * Uses LuckPerms if available, otherwise attempts removal via Bukkit.
     *
     * @param player The target player
     * @param permission The permission node to revoke
     */
    fun revokePermission(player: Player, permission: String) {
        if (permission.isBlank()) return

        try {
            if (luckPermsAvailable) {
                revokeViaLuckPerms(player, permission)
            } else {
                revokeViaBukkit(player, permission)
            }
            plugin.logger.fine("Revoked permission '$permission' from ${player.name}")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to revoke permission '$permission' from ${player.name}", e)
        }
    }

    /**
     * Check whether LuckPerms is detected on this server.
     */
    fun isLuckPermsAvailable(): Boolean = luckPermsAvailable

    // ==================== LuckPerms (Reflection) ====================

    /**
     * Grant a permission via LuckPerms using reflection.
     *
     * Reflection call chain:
     *   1. Bukkit.getServicesManager().getRegistration(LuckPerms.class)
     *   2. luckPerms.getUserManager()
     *   3. userManager.getUser(uuid)
     *   4. Node.builder(permission).build()
     *   5. user.data().add(node)
     *   6. userManager.saveUser(user)
     *
     * Falls back to Bukkit if any reflection step fails.
     */
    private fun grantViaLuckPerms(player: Player, permission: String) {
        try {
            val provider = Bukkit.getServicesManager().getRegistration(
                Class.forName("net.luckperms.api.LuckPerms")
            ) ?: run {
                plugin.logger.warning("LuckPerms service not registered, falling back to Bukkit")
                grantViaBukkit(player, permission)
                return
            }

            val luckPerms = provider.provider
            val userManager = luckPerms.javaClass.getMethod("getUserManager").invoke(luckPerms)
            val user = userManager.javaClass.getMethod("getUser", java.util.UUID::class.java)
                .invoke(userManager, player.uniqueId) ?: run {
                plugin.logger.warning("LuckPerms user not found for ${player.name}")
                return
            }

            // Build permission node: Node.builder(permission).build()
            val nodeClass = Class.forName("net.luckperms.api.node.Node")
            val builderMethod = nodeClass.getMethod("builder", String::class.java)
            val builder = builderMethod.invoke(null, permission)
            val node = builder.javaClass.getMethod("build").invoke(builder)

            // Add node to user data: user.data().add(node)
            val data = user.javaClass.getMethod("data").invoke(user)
            data.javaClass.getMethod("add", nodeClass).invoke(data, node)

            // Persist changes: userManager.saveUser(user)
            userManager.javaClass.getMethod("saveUser", user.javaClass).invoke(userManager, user)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "LuckPerms reflection failed, falling back to Bukkit", e)
            grantViaBukkit(player, permission)
        }
    }

    /**
     * Revoke a permission via LuckPerms using reflection.
     *
     * Mirrors [grantViaLuckPerms] but calls data().remove(node) instead of add.
     * Silently returns if any step fails (permission may not exist).
     */
    private fun revokeViaLuckPerms(player: Player, permission: String) {
        try {
            val provider = Bukkit.getServicesManager().getRegistration(
                Class.forName("net.luckperms.api.LuckPerms")
            ) ?: return

            val luckPerms = provider.provider
            val userManager = luckPerms.javaClass.getMethod("getUserManager").invoke(luckPerms)
            val user = userManager.javaClass.getMethod("getUser", java.util.UUID::class.java)
                .invoke(userManager, player.uniqueId) ?: return

            val nodeClass = Class.forName("net.luckperms.api.node.Node")
            val builderMethod = nodeClass.getMethod("builder", String::class.java)
            val builder = builderMethod.invoke(null, permission)
            val node = builder.javaClass.getMethod("build").invoke(builder)

            val data = user.javaClass.getMethod("data").invoke(user)
            data.javaClass.getMethod("remove", nodeClass).invoke(data, node)

            userManager.javaClass.getMethod("saveUser", user.javaClass).invoke(userManager, user)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "LuckPerms revoke failed", e)
        }
    }

    // ==================== Bukkit Fallback ====================

    /**
     * Grant a permission via Bukkit's PermissionAttachment system.
     *
     * Note: Bukkit attachments are session-scoped -- they do not persist
     * across server restarts. This is a limitation of the Bukkit API.
     * For persistent permissions, LuckPerms (or similar) is required.
     *
     * Must be called from the main server thread.
     */
    private fun grantViaBukkit(player: Player, permission: String) {
        player.addAttachment(plugin, permission, true)
        player.recalculatePermissions()
    }

    /**
     * Revoke a permission via Bukkit's PermissionAttachment system.
     *
     * Iterates effective permissions to find and remove matching attachments.
     * Must be called from the main server thread.
     */
    private fun revokeViaBukkit(player: Player, permission: String) {
        player.effectivePermissions
            .filter { it.permission.equals(permission, ignoreCase = true) }
            .mapNotNull { it.attachment }
            .forEach { player.removeAttachment(it) }
        player.recalculatePermissions()
    }
}
