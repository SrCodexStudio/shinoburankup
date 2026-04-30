package com.shinobu.rankup.util

import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine utilities for safe async operations in Bukkit/Paper.
 */

/**
 * A dispatcher that runs tasks on the main Bukkit thread.
 */
class BukkitMainDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (Bukkit.isPrimaryThread()) {
            block.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, block)
        }
    }
}

/**
 * A dispatcher that runs tasks asynchronously (off the main thread).
 */
class BukkitAsyncDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!Bukkit.isPrimaryThread()) {
            block.run()
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, block)
        }
    }
}

/**
 * Suspends the coroutine and runs the block on the main Bukkit thread.
 * Returns the result of the block.
 */
suspend fun <T> Plugin.runOnMain(block: () -> T): T {
    return if (Bukkit.isPrimaryThread()) {
        block()
    } else {
        suspendCancellableCoroutine { continuation ->
            Bukkit.getScheduler().runTask(this, Runnable {
                try {
                    continuation.resume(block())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            })
        }
    }
}

/**
 * Suspends the coroutine and runs the block asynchronously.
 * Returns the result of the block.
 */
suspend fun <T> Plugin.runAsync(block: () -> T): T {
    return if (!Bukkit.isPrimaryThread()) {
        block()
    } else {
        suspendCancellableCoroutine { continuation ->
            Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                try {
                    continuation.resume(block())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            })
        }
    }
}

/**
 * Runs a task on the main thread after a delay (in ticks).
 */
suspend fun Plugin.runLater(delayTicks: Long, block: () -> Unit) {
    suspendCancellableCoroutine<Unit> { continuation ->
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            try {
                block()
                continuation.resume(Unit)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, delayTicks)
    }
}

/**
 * Creates a repeating task that can be cancelled.
 */
fun Plugin.repeatTask(
    delayTicks: Long,
    periodTicks: Long,
    block: (BukkitTask) -> Unit
): BukkitTask {
    var taskRef: BukkitTask? = null
    taskRef = Bukkit.getScheduler().runTaskTimer(this, Runnable {
        taskRef?.let { block(it) }
    }, delayTicks, periodTicks)
    return taskRef
}

/**
 * Creates a coroutine scope tied to the plugin lifecycle.
 */
class PluginCoroutineScope(plugin: Plugin) : CoroutineScope {
    private val job = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        plugin.logger.severe("Unhandled coroutine exception: ${throwable.message}")
        throwable.printStackTrace()
    }

    val mainDispatcher = BukkitMainDispatcher(plugin)
    val asyncDispatcher = BukkitAsyncDispatcher(plugin)

    override val coroutineContext: CoroutineContext = job + Dispatchers.Default + exceptionHandler

    /**
     * Launch a coroutine on the main thread.
     */
    fun launchMain(block: suspend CoroutineScope.() -> Unit): Job {
        return launch(mainDispatcher, block = block)
    }

    /**
     * Launch a coroutine asynchronously.
     */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job {
        return launch(Dispatchers.IO, block = block)
    }

    /**
     * Cancel all coroutines in this scope.
     */
    fun shutdown() {
        job.cancel()
    }

    /**
     * Cancel all coroutines and wait for them to complete.
     */
    suspend fun shutdownAndJoin() {
        job.cancelAndJoin()
    }
}

/**
 * Extension to safely run something with a timeout.
 */
suspend fun <T> withTimeoutSafe(
    timeMillis: Long,
    defaultValue: T,
    block: suspend () -> T
): T {
    return try {
        withTimeout(timeMillis) {
            block()
        }
    } catch (e: TimeoutCancellationException) {
        defaultValue
    }
}

/**
 * Retry a block with exponential backoff.
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 100,
    maxDelay: Long = 2000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            // Log retry attempt
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // Last attempt
}
