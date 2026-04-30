package com.srcodex.shinobustore.transaction

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Represents a pending transaction awaiting payment.
 */
data class PendingTransaction(
    @SerializedName("order_id")
    val orderId: String,

    @SerializedName("player_uuid")
    val playerUuid: UUID,

    @SerializedName("player_name")
    val playerName: String,

    @SerializedName("item_id")
    val itemId: String,

    @SerializedName("item_display")
    val itemDisplay: String,

    @SerializedName("cost")
    val cost: Double,

    @SerializedName("fee")
    val fee: Double,

    @SerializedName("total")
    val total: Double,

    @SerializedName("created_at")
    val createdAt: Long,

    @SerializedName("expires_at")
    val expiresAt: Long,

    @SerializedName("checkout_url")
    val checkoutUrl: String,

    @SerializedName("commands")
    val commands: List<String> = emptyList()
) {
    // Alias for backward compatibility
    val playerUUID: UUID get() = playerUuid

    /**
     * Checks if this transaction has expired.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt

    /**
     * Gets the time remaining until expiration.
     */
    fun getTimeRemaining(): Long {
        val remaining = expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }
}

/**
 * Represents a completed transaction in history.
 * Note: The `paypalCaptureId` field was removed as it was redundant with `captureId`.
 */
data class CompletedTransaction(
    @SerializedName("order_id")
    val orderId: String,

    @SerializedName("capture_id")
    val captureId: String? = null,

    @SerializedName("player_uuid")
    val playerUuid: UUID,

    @SerializedName("player_name")
    val playerName: String,

    @SerializedName("item_id")
    val itemId: String,

    @SerializedName("item_display")
    val itemDisplay: String,

    @SerializedName("cost")
    val cost: Double,

    @SerializedName("fee")
    val fee: Double = 0.0,

    @SerializedName("total")
    val total: Double,

    @SerializedName("created_at")
    val createdAt: Long,

    @SerializedName("completed_at")
    val completedAt: Long,

    @SerializedName("commands_executed")
    val commandsExecuted: List<String> = emptyList()
) {
    // Alias for backward compatibility
    val playerUUID: UUID get() = playerUuid
}

/**
 * Status of a transaction capture attempt.
 */
sealed class CaptureResult {
    data class Success(
        val orderId: String,
        val captureId: String
    ) : CaptureResult()

    data class Pending(
        val orderId: String,
        val message: String
    ) : CaptureResult()

    data class Failed(
        val orderId: String,
        val error: String
    ) : CaptureResult()

    object NotPaid : CaptureResult()
}

/**
 * Store item configuration.
 */
data class StoreItem(
    val id: String,
    val category: String,
    val position: Int,
    val material: String,
    val display: String,
    val cost: Double,
    val passFee: Boolean,
    val lore: List<String>,
    val commands: List<String>
) {
    /**
     * Calculates the total price including payment processor fees if applicable.
     * Default fee structure: 2.9% + $0.30 (PayPal standard).
     *
     * @param feePercentage the percentage fee charged by the payment processor (default 2.9)
     * @param feeFixed the fixed fee per transaction in dollars (default 0.30)
     * @return the total price the buyer pays
     */
    fun calculateTotal(feePercentage: Double = 2.9, feeFixed: Double = 0.30): Double {
        return if (passFee) {
            // Formula: (cost + feeFixed) / (1 - feePercentage / 100)
            (cost + feeFixed) / (1.0 - feePercentage / 100.0)
        } else {
            cost
        }
    }
}

/**
 * Store category configuration.
 */
data class StoreCategory(
    val id: String,
    val position: Int,
    val material: String,
    val display: String,
    val rows: Int,
    val lore: List<String>
)

/**
 * Transaction statistics.
 */
data class TransactionStatistics(
    val totalCompleted: Int,
    val totalRevenue: Double,
    val currentPending: Int,
    val todaySales: Int,
    val todayRevenue: Double
)
