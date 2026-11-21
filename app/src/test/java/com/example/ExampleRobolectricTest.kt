package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.StarterCatalog
import com.example.payment.PaymentGatewayManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and seller phone from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("BazaarHub", context.getString(R.string.app_name))
        assertEquals("7679100108", context.getString(R.string.default_contact_phone))
    }

    @Test
    fun `upi payment intent contains merchant upi and INR currency`() {
        val intent = PaymentGatewayManager.buildUpiPaymentIntent(
            merchantUpiId = "7679100108@ybl",
            merchantName = "BazaarHub",
            amountInr = 2499L,
            transactionRef = "BZH_TEST123",
            note = "BazaarHub Order"
        )
        val uri = intent.data!!
        assertEquals("upi", uri.scheme)
        assertEquals("pay", uri.host)
        assertEquals("7679100108@ybl", uri.getQueryParameter("pa"))
        assertEquals("2499.00", uri.getQueryParameter("am"))
        assertEquals("INR", uri.getQueryParameter("cu"))
    }

    @Test
    fun `starter catalog has valid products`() {
        assertTrue(StarterCatalog.defaultProducts.isNotEmpty())
        assertTrue(StarterCatalog.defaultProducts.all { it.priceInr > 0 && it.mrpInr >= it.priceInr })
    }
}
