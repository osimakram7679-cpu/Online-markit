package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ProductCategory(val label: String) {
    ELECTRONICS("Electronics"),
    FASHION("Fashion"),
    HOME("Home"),
    BEAUTY("Beauty"),
    ACCESSORIES("Accessories");

    companion object {
        fun fromRaw(raw: String?): ProductCategory =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: ELECTRONICS
    }
}

enum class PaymentMethod(val wireValue: String, val displayTitle: String, val subtitle: String) {
    UPI_GATEWAY("UPI_GATEWAY", "UPI Payment Gateway", "GPay, PhonePe, Paytm, BHIM & UPI ID"),
    CARD_GATEWAY("CARD_GATEWAY", "Card / NetBanking Gateway", "Visa, Mastercard, RuPay & NetBanking"),
    COD("COD", "Cash on Delivery", "Pay via cash or UPI when order arrives");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.wireValue == raw } ?: UPI_GATEWAY
    }
}

enum class PaymentStatus(val wireValue: String) {
    PAID("PAID"),
    PENDING("PENDING"),
    FAILED("FAILED"),
    REFUNDED("REFUNDED");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PENDING
    }
}

enum class OrderStatus(val wireValue: String, val label: String) {
    PLACED("PLACED", "Placed"),
    CONFIRMED("CONFIRMED", "Confirmed"),
    PACKED("PACKED", "Packed"),
    SHIPPED("SHIPPED", "Shipped"),
    DELIVERED("DELIVERED", "Delivered"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PLACED
    }
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val priceInr: Long = 0L,
    val mrpInr: Long = 0L,
    val category: String = ProductCategory.ELECTRONICS.label,
    val imagePreset: String = "electronics",
    val imageUrl: String? = null,
    val stock: Long = 0L,
    val featured: Boolean = false,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val discountPercent: Int
        get() = if (mrpInr > priceInr && mrpInr > 0) {
            (((mrpInr - priceInr).toDouble() / mrpInr.toDouble()) * 100).toInt()
        } else 0
}

@Keep
@IgnoreExtraProperties
data class StoreSettings(
    val storeName: String = "BazaarHub",
    val tagline: String = "Curated Everyday Lifestyle & Tech",
    val contactPhone: String = "7679100108",
    val contactEmail: String = "osimakram90644@gmail.com",
    val merchantUpiId: String = "7679100108@ybl",
    val announcementText: String = "Use code BAZAAR10 for 10% off • Support: 7679100108",
    val storeOpen: Boolean = true,
    val upiEnabled: Boolean = true,
    val cardGatewayEnabled: Boolean = true,
    val codEnabled: Boolean = true,
    val promoEnabled: Boolean = true,
    val promoCode: String = "BAZAAR10",
    val promoDiscountPercent: Long = 10L,
    val deliveryFeeInr: Long = 49L,
    val freeDeliveryAboveInr: Long = 999L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String? = null,
    val defaultAddress: String? = null,
    val city: String? = null,
    val pincode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderItem(
    val productId: String = "",
    val title: String = "",
    val priceInr: Long = 0L,
    val quantity: Long = 1L
)

@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val shippingAddress: String = "",
    val city: String = "",
    val pincode: String = "",
    val itemsSummary: String = "",
    val items: List<OrderItem> = emptyList(),
    val itemCount: Long = 0L,
    val subtotalInr: Long = 0L,
    val discountInr: Long = 0L,
    val deliveryFeeInr: Long = 0L,
    val totalInr: Long = 0L,
    val promoCodeApplied: String? = null,
    val paymentMethod: String = PaymentMethod.UPI_GATEWAY.wireValue,
    val paymentStatus: String = PaymentStatus.PENDING.wireValue,
    val transactionId: String = "",
    val orderStatus: String = OrderStatus.PLACED.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CartItem(
    val product: Product,
    val quantity: Int
)

object StarterCatalog {
    const val ADMIN_BOOTSTRAP_EMAIL = "osimakram90644@gmail.com"
    const val DEFAULT_SELLER_PHONE = "7679100108"

    val defaultProducts: List<Product> = listOf(
        Product(
            id = "starter_headphones",
            title = "Aether Studio ANC Headphones",
            description = "40-hour battery, copper acoustic drivers, plush memory foam earcups, and hybrid active noise cancellation for studio-grade clarity.",
            priceInr = 2499L,
            mrpInr = 3999L,
            category = ProductCategory.ELECTRONICS.label,
            imagePreset = "electronics",
            stock = 18L,
            featured = true,
            active = true
        ),
        Product(
            id = "starter_tote",
            title = "Artisan Cognac Leather Tote",
            description = "Full-grain vegetable-tanned leather tote with padded 14-inch laptop sleeve, brass zip pocket, and matching sun-shield frame pouch.",
            priceInr = 1899L,
            mrpInr = 2999L,
            category = ProductCategory.FASHION.label,
            imagePreset = "fashion",
            stock = 12L,
            featured = true,
            active = true
        ),
        Product(
            id = "starter_pourover",
            title = "Kansa Ceramic Pour-Over Set",
            description = "Hand-glazed matte stoneware coffee carafe with matching tasting cup and warm amber cedarwood scented soy candle.",
            priceInr = 1499L,
            mrpInr = 2199L,
            category = ProductCategory.HOME.label,
            imagePreset = "home",
            stock = 24L,
            featured = true,
            active = true
        ),
        Product(
            id = "starter_watch",
            title = "Chrono Minimalist Smartband",
            description = "AMOLED crisp display, 10-day battery life, SpO2 & heart rate tracking, and IP68 water resistance with brushed steel bezel.",
            priceInr = 3299L,
            mrpInr = 4999L,
            category = ProductCategory.ELECTRONICS.label,
            imagePreset = "banner",
            stock = 9L,
            featured = false,
            active = true
        ),
        Product(
            id = "starter_sunglasses",
            title = "Solstice Polarized Eyewear",
            description = "UV400 polarized acetate frame with anti-glare coating and handcrafted travel case included.",
            priceInr = 899L,
            mrpInr = 1499L,
            category = ProductCategory.ACCESSORIES.label,
            imagePreset = "fashion",
            stock = 30L,
            featured = false,
            active = true
        ),
        Product(
            id = "starter_candle_kit",
            title = "Saffron & Oud Glow Gift Set",
            description = "Aromatic home sanctuary bundle featuring botanical essential oil diffuser, amber candle, and ceramic holder.",
            priceInr = 1199L,
            mrpInr = 1799L,
            category = ProductCategory.BEAUTY.label,
            imagePreset = "home",
            stock = 15L,
            featured = false,
            active = true
        )
    )
}
