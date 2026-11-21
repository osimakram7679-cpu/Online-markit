package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CartDataStore
import com.example.data.ShopRepository
import com.example.model.CartItem
import com.example.model.OrderItem
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.Product
import com.example.model.StarterCatalog
import com.example.model.StoreSettings
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartPricing(
    val subtotalInr: Long = 0L,
    val discountInr: Long = 0L,
    val deliveryFeeInr: Long = 0L,
    val totalInr: Long = 0L,
    val appliedPromoCode: String? = null
)

class StoreViewModel(
    private val repository: ShopRepository,
    private val cartDataStore: CartDataStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _promoInput = MutableStateFlow("")
    val promoInput: StateFlow<String> = _promoInput.asStateFlow()

    private val _promoApplied = MutableStateFlow(false)
    val promoApplied: StateFlow<Boolean> = _promoApplied.asStateFlow()

    private val _checkoutBusy = MutableStateFlow(false)
    val checkoutBusy: StateFlow<Boolean> = _checkoutBusy.asStateFlow()

    private val _checkoutFeedback = MutableStateFlow<String?>(null)
    val checkoutFeedback: StateFlow<String?> = _checkoutFeedback.asStateFlow()

    // Live catalog from Firestore; gracefully falls back to curated starter catalog if Firestore is empty
    val productsState: StateFlow<UiState<List<Product>>> = repository.observeProducts()
        .map<List<Product>, UiState<List<Product>>> { cloudProducts ->
            val effective = if (cloudProducts.isEmpty()) StarterCatalog.defaultProducts else cloudProducts
            UiState.Success(effective)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            // Keep storefront usable even if offline by falling back only with message or showing error
            emit(UiState.Error(firestoreError?.message ?: error.message ?: "Unable to load catalog"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val allProducts: StateFlow<List<Product>> = productsState
        .map { state ->
            when (state) {
                is UiState.Success -> state.data
                else -> StarterCatalog.defaultProducts
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = StarterCatalog.defaultProducts
        )

    val storeSettings: StateFlow<StoreSettings> = repository.observeStoreSettings()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(StoreSettings())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = StoreSettings()
        )

    val cartQuantities: StateFlow<Map<String, Int>> = cartDataStore.cartQuantitiesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyMap()
        )

    val cartItems: StateFlow<List<CartItem>> = combine(allProducts, cartQuantities) { products, qtyMap ->
        qtyMap.mapNotNull { (prodId, qty) ->
            val product = products.firstOrNull { it.id == prodId && it.active }
            if (product != null && qty > 0) CartItem(product, qty) else null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val totalCartCount: StateFlow<Int> = cartItems
        .map { items -> items.sumOf { it.quantity } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    val cartPricing: StateFlow<CartPricing> = combine(
        cartItems,
        storeSettings,
        promoApplied
    ) { items, settings, isPromoApplied ->
        if (items.isEmpty()) {
            CartPricing()
        } else {
            val subtotal = items.sumOf { it.product.priceInr * it.quantity }
            val discount = if (isPromoApplied && settings.promoEnabled && settings.promoDiscountPercent > 0) {
                ((subtotal * settings.promoDiscountPercent) / 100L).coerceAtMost(subtotal - 1L).coerceAtLeast(0L)
            } else 0L
            val afterDiscount = (subtotal - discount).coerceAtLeast(1L)
            val deliveryFee = if (afterDiscount >= settings.freeDeliveryAboveInr || settings.deliveryFeeInr <= 0L) {
                0L
            } else settings.deliveryFeeInr
            CartPricing(
                subtotalInr = subtotal,
                discountInr = discount,
                deliveryFeeInr = deliveryFee,
                totalInr = afterDiscount + deliveryFee,
                appliedPromoCode = if (discount > 0L) settings.promoCode else null
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CartPricing()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updatePromoInput(code: String) {
        _promoInput.value = code.uppercase()
    }

    fun applyPromoCode(): Boolean {
        val settings = storeSettings.value
        val code = _promoInput.value.trim()
        return if (settings.promoEnabled && settings.promoCode.isNotBlank() && code.equals(settings.promoCode, ignoreCase = true)) {
            _promoApplied.value = true
            _checkoutFeedback.value = "Promo code ${settings.promoCode} applied (${settings.promoDiscountPercent}% off)!"
            true
        } else {
            _promoApplied.value = false
            _checkoutFeedback.value = "Invalid or inactive promo code."
            false
        }
    }

    fun removePromoCode() {
        _promoApplied.value = false
        _promoInput.value = ""
    }

    fun addToCart(product: Product, count: Int = 1) {
        viewModelScope.launch {
            val current = cartQuantities.value.toMutableMap()
            val maxStock = product.stock.toInt().coerceAtLeast(1)
            val nextQty = ((current[product.id] ?: 0) + count).coerceIn(1, maxStock)
            current[product.id] = nextQty
            cartDataStore.saveQuantities(current)
        }
    }

    fun updateCartQuantity(productId: String, newQty: Int) {
        viewModelScope.launch {
            val current = cartQuantities.value.toMutableMap()
            if (newQty <= 0) {
                current.remove(productId)
            } else {
                current[productId] = newQty.coerceAtMost(99)
            }
            cartDataStore.saveQuantities(current)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            cartDataStore.clear()
            _promoApplied.value = false
            _promoInput.value = ""
        }
    }

    fun clearFeedback() {
        _checkoutFeedback.value = null
    }

    fun submitOrder(
        customerName: String,
        customerEmail: String,
        customerPhone: String,
        shippingAddress: String,
        city: String,
        pincode: String,
        paymentMethod: PaymentMethod,
        paymentStatus: PaymentStatus,
        transactionId: String,
        onOrderPlaced: (String) -> Unit
    ) {
        val currentItems = cartItems.value
        val pricing = cartPricing.value
        if (currentItems.isEmpty()) {
            _checkoutFeedback.value = "Your cart is empty."
            return
        }
        viewModelScope.launch {
            _checkoutBusy.value = true
            val orderItems = currentItems.map {
                OrderItem(
                    productId = it.product.id,
                    title = it.product.title,
                    priceInr = it.product.priceInr,
                    quantity = it.quantity.toLong()
                )
            }
            val result = repository.placeOrder(
                customerName = customerName,
                customerEmail = customerEmail,
                customerPhone = customerPhone,
                shippingAddress = shippingAddress,
                city = city,
                pincode = pincode,
                items = orderItems,
                subtotalInr = pricing.subtotalInr,
                discountInr = pricing.discountInr,
                deliveryFeeInr = pricing.deliveryFeeInr,
                totalInr = pricing.totalInr,
                promoCodeApplied = pricing.appliedPromoCode,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                transactionId = transactionId
            )
            // Also save profile defaults for future convenience
            repository.saveUserProfile(
                displayName = customerName,
                email = customerEmail,
                phone = customerPhone,
                defaultAddress = shippingAddress,
                city = city,
                pincode = pincode
            )
            _checkoutBusy.value = false
            result.fold(
                onSuccess = { orderId ->
                    clearCart()
                    onOrderPlaced(orderId)
                },
                onFailure = { error ->
                    _checkoutFeedback.value = error.localizedMessage ?: "Failed to place order"
                }
            )
        }
    }
}
