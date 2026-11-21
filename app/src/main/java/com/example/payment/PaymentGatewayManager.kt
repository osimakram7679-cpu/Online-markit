package com.example.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import java.util.UUID

data class UpiPaymentResult(
    val succeeded: Boolean,
    val transactionId: String,
    val statusMessage: String
)

object PaymentGatewayManager {

    fun isLiveGatewayKeyConfigured(): Boolean {
        val key = BuildConfig.PAYMENT_GATEWAY_KEY
        return key.isNotBlank() && key != "YOUR_PAYMENT_GATEWAY_KEY"
    }

    fun maskedGatewayKey(): String {
        val key = BuildConfig.PAYMENT_GATEWAY_KEY
        return if (!isLiveGatewayKeyConfigured()) {
            "Sandbox / UPI Mode (Set PAYMENT_GATEWAY_KEY in Secrets)"
        } else {
            val prefix = key.take(8)
            "$prefix••••••••"
        }
    }

    fun generateTransactionRef(prefix: String = "BZH"): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
    }

    fun buildUpiPaymentIntent(
        merchantUpiId: String,
        merchantName: String,
        amountInr: Long,
        transactionRef: String,
        note: String
    ): Intent {
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", merchantUpiId.trim())
            .appendQueryParameter("pn", merchantName.trim())
            .appendQueryParameter("tr", transactionRef)
            .appendQueryParameter("tn", note)
            .appendQueryParameter("am", "$amountInr.00")
            .appendQueryParameter("cu", "INR")
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun hasUpiAppsInstalled(context: Context, intent: Intent): Boolean {
        val resolved = context.packageManager.queryIntentActivities(intent, 0)
        return resolved.isNotEmpty()
    }

    fun parseUpiIntentResponse(data: Intent?, fallbackTxnRef: String): UpiPaymentResult {
        val rawResponse = data?.getStringExtra("response").orEmpty()
        if (rawResponse.isBlank()) {
            return UpiPaymentResult(
                succeeded = false,
                transactionId = fallbackTxnRef,
                statusMessage = "Payment was cancelled in the UPI app."
            )
        }
        val pairs = rawResponse.split("&")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) part.substring(0, idx).lowercase() to part.substring(idx + 1) else null
            }
            .toMap()

        val status = pairs["status"]?.lowercase().orEmpty()
        val txnId = pairs["txnid"]?.takeIf { it.isNotBlank() }
            ?: pairs["txnref"]?.takeIf { it.isNotBlank() }
            ?: fallbackTxnRef

        return when (status) {
            "success" -> UpiPaymentResult(
                succeeded = true,
                transactionId = txnId,
                statusMessage = "UPI payment verified ($txnId)"
            )
            "submitted" -> UpiPaymentResult(
                succeeded = true,
                transactionId = txnId,
                statusMessage = "UPI payment submitted ($txnId)"
            )
            else -> UpiPaymentResult(
                succeeded = false,
                transactionId = txnId,
                statusMessage = "UPI payment failed or cancelled."
            )
        }
    }
}
