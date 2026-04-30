package com.srcodex.shinobustore.paypal

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.srcodex.shinobustore.ShinobuStore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * PayPal REST API Client using OkHttp for modern HTTP operations.
 * Supports both Sandbox and Live environments.
 */
class PayPalClient(private val plugin: ShinobuStore) {

    private val gson = Gson()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseUrl: String
        get() = if (plugin.configManager.paypalEnvironment.equals("SANDBOX", ignoreCase = true)) {
            "https://api-m.sandbox.paypal.com"
        } else {
            "https://api-m.paypal.com"
        }

    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    /**
     * Result wrapper for API responses.
     */
    sealed class ApiResult<T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error<T>(val message: String, val statusCode: Int = 0) : ApiResult<T>()
    }

    /**
     * Gets a valid access token, refreshing if necessary.
     */
    @Synchronized
    fun getAccessToken(): String? {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken
        }

        val clientId = plugin.configManager.paypalClientId
        val secret = plugin.configManager.paypalSecret

        if (clientId.isBlank() || secret.isBlank()) {
            plugin.logger.severe("PayPal credentials not configured!")
            return null
        }

        val credentials = Base64.getEncoder().encodeToString("$clientId:$secret".toByteArray())

        val request = Request.Builder()
            .url("$baseUrl/v1/oauth2/token")
            .addHeader("Authorization", "Basic $credentials")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post("grant_type=client_credentials".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    accessToken = json.get("access_token")?.asString
                    val expiresIn = json.get("expires_in")?.asLong ?: 3600

                    // Set expiry 5 minutes before actual expiry for safety
                    tokenExpiry = System.currentTimeMillis() + ((expiresIn - 300) * 1000)

                    if (plugin.configManager.logPaypalRequests) {
                        plugin.logger.info("PayPal access token obtained successfully")
                    }

                    accessToken
                } else {
                    plugin.logger.log(Level.SEVERE, "Failed to get PayPal access token: ${response.code} - $body")
                    null
                }
            }
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to connect to PayPal", e)
            null
        }
    }

    /**
     * Creates a new order in PayPal.
     */
    fun createOrder(
        amount: Double,
        currency: String,
        description: String,
        customId: String? = null
    ): ApiResult<OrderResponse> {
        val token = getAccessToken() ?: return ApiResult.Error("Failed to authenticate with PayPal")

        val orderRequest = JsonObject().apply {
            addProperty("intent", "CAPTURE")

            add("purchase_units", gson.toJsonTree(listOf(
                mapOf(
                    "amount" to mapOf(
                        "currency_code" to currency,
                        "value" to String.format(java.util.Locale.US, "%.2f", amount)
                    ),
                    "description" to description,
                    "custom_id" to (customId ?: "")
                )
            )))

            add("application_context", JsonObject().apply {
                addProperty("brand_name", plugin.configManager.paypalBrandName)
                addProperty("landing_page", "LOGIN")
                addProperty("user_action", "PAY_NOW")
                addProperty("shipping_preference", "NO_SHIPPING")
                add("payment_method", JsonObject().apply {
                    addProperty("payee_preferred", "IMMEDIATE_PAYMENT_REQUIRED")
                })
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/v2/checkout/orders")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(orderRequest.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (plugin.configManager.logPaypalRequests) {
                    plugin.logger.info("PayPal create order response: ${response.code}")
                }

                if (response.isSuccessful && body != null) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val orderId = json.get("id")?.asString ?: return@use ApiResult.Error("Missing order ID in response")
                    val status = json.get("status")?.asString ?: "UNKNOWN"

                    // Find approve link
                    val links = json.getAsJsonArray("links")
                    var approveUrl = ""
                    links?.forEach { link ->
                        val linkObj = link.asJsonObject
                        if (linkObj.get("rel")?.asString == "approve") {
                            approveUrl = linkObj.get("href")?.asString ?: ""
                        }
                    }

                    ApiResult.Success(OrderResponse(orderId, status, approveUrl))
                } else {
                    val errorMessage = parsePayPalError(body)
                    ApiResult.Error(errorMessage, response.code)
                }
            }
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to create PayPal order", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Captures an approved order.
     */
    fun captureOrder(orderId: String): ApiResult<CaptureResponse> {
        val token = getAccessToken() ?: return ApiResult.Error("Failed to authenticate with PayPal")

        val request = Request.Builder()
            .url("$baseUrl/v2/checkout/orders/$orderId/capture")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (plugin.configManager.logPaypalRequests) {
                    plugin.logger.info("PayPal capture response: ${response.code}")
                }

                when {
                    response.isSuccessful && body != null -> {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val status = json.get("status")?.asString ?: "UNKNOWN"

                        // Get capture ID and amount from purchase units
                        var captureId = ""
                        var capturedAmount = 0.0
                        var capturedCurrency = ""
                        try {
                            val captureObj = json.getAsJsonArray("purchase_units")
                                ?.get(0)?.asJsonObject
                                ?.getAsJsonObject("payments")
                                ?.getAsJsonArray("captures")
                                ?.get(0)?.asJsonObject
                            captureId = captureObj?.get("id")?.asString ?: ""
                            capturedAmount = captureObj?.getAsJsonObject("amount")?.get("value")?.asString?.toDoubleOrNull() ?: 0.0
                            capturedCurrency = captureObj?.getAsJsonObject("amount")?.get("currency_code")?.asString ?: ""
                        } catch (_: Exception) {}

                        ApiResult.Success(CaptureResponse(orderId, status, captureId, capturedAmount, capturedCurrency))
                    }
                    response.code == 422 -> {
                        // Order not yet approved or already captured
                        val errorMessage = parsePayPalError(body)
                        if (errorMessage.contains("ORDER_NOT_APPROVED", ignoreCase = true)) {
                            ApiResult.Error("ORDER_NOT_APPROVED", 422)
                        } else {
                            ApiResult.Error(errorMessage, 422)
                        }
                    }
                    else -> {
                        val errorMessage = parsePayPalError(body)
                        ApiResult.Error(errorMessage, response.code)
                    }
                }
            }
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to capture PayPal order", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Gets the status of an order.
     */
    fun getOrderStatus(orderId: String): ApiResult<String> {
        val token = getAccessToken() ?: return ApiResult.Error("Failed to authenticate with PayPal")

        val request = Request.Builder()
            .url("$baseUrl/v2/checkout/orders/$orderId")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val status = json.get("status")?.asString ?: "UNKNOWN"
                    ApiResult.Success(status)
                } else {
                    ApiResult.Error(parsePayPalError(body), response.code)
                }
            }
        } catch (e: IOException) {
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Parses error message from PayPal response.
     */
    private fun parsePayPalError(body: String?): String {
        if (body.isNullOrBlank()) return "Unknown PayPal error"

        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.get("message")?.asString
                ?: json.get("error_description")?.asString
                ?: json.getAsJsonArray("details")?.get(0)?.asJsonObject?.get("description")?.asString
                ?: "Unknown PayPal error"
        } catch (_: Exception) {
            "Unknown PayPal error"
        }
    }

    /**
     * Validates PayPal configuration.
     */
    fun validateConfiguration(): Boolean {
        return getAccessToken() != null
    }

    /**
     * Shuts down the HTTP client.
     */
    fun shutdown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}

/**
 * Response from order creation.
 */
data class OrderResponse(
    val orderId: String,
    val status: String,
    val approveUrl: String
)

/**
 * Response from order capture.
 */
data class CaptureResponse(
    val orderId: String,
    val status: String,
    val captureId: String,
    val capturedAmount: Double = 0.0,
    val capturedCurrency: String = ""
)
