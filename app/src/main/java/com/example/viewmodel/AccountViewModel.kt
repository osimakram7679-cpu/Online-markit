package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ShopRepository
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.PaymentStatus
import com.example.model.StoreSettings
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModel(
    private val repository: ShopRepository,
    private val currentUserId: String,
    private val firebaseUser: FirebaseUser?
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            _isAdmin.value = repository.checkIsAdmin(firebaseUser)
        }
    }

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val myOrdersState: StateFlow<UiState<List<Order>>> = repository.observeMyOrders(currentUserId)
        .map<List<Order>, UiState<List<Order>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Unable to load orders"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val adminOrdersState: StateFlow<UiState<List<Order>>> = _isAdmin
        .flatMapLatest { admin ->
            if (!admin) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAllOrders()
                    .map<List<Order>, UiState<List<Order>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        emit(UiState.Error(root.message ?: "Unable to load admin orders"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun saveProfile(
        displayName: String,
        email: String,
        phone: String?,
        defaultAddress: String?,
        city: String?,
        pincode: String?
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveUserProfile(
                displayName = displayName,
                email = email,
                phone = phone,
                defaultAddress = defaultAddress,
                city = city,
                pincode = pincode
            )
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Profile & address saved" },
                onFailure = { it.localizedMessage ?: "Failed to save profile" }
            )
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.cancelOrder(orderId)
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Order cancelled" },
                onFailure = { it.localizedMessage ?: "Failed to cancel order" }
            )
        }
    }

    fun adminUpdateOrder(
        orderId: String,
        orderStatus: OrderStatus,
        paymentStatus: PaymentStatus,
        transactionId: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.adminUpdateOrder(orderId, orderStatus, paymentStatus, transactionId)
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Order status updated to ${orderStatus.label}" },
                onFailure = { it.localizedMessage ?: "Failed to update order" }
            )
        }
    }

    fun upsertProduct(
        productId: String?,
        title: String,
        description: String,
        priceInr: Long,
        mrpInr: Long,
        category: String,
        imagePreset: String,
        imageUrl: String?,
        stock: Long,
        featured: Boolean,
        active: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.upsertProduct(
                productId = productId,
                title = title,
                description = description,
                priceInr = priceInr,
                mrpInr = mrpInr,
                category = category,
                imagePreset = imagePreset,
                imageUrl = imageUrl,
                stock = stock,
                featured = featured,
                active = active
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = if (productId == null) "Product added to catalog" else "Product updated"
                    onDone()
                },
                onFailure = {
                    _actionMessage.value = it.localizedMessage ?: "Failed to save product"
                }
            )
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteProduct(productId)
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Product removed" },
                onFailure = { it.localizedMessage ?: "Failed to delete product" }
            )
        }
    }

    fun seedStarterProducts() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.seedStarterProducts()
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Starter catalog published to Firestore!" },
                onFailure = { it.localizedMessage ?: "Failed to publish starter catalog" }
            )
        }
    }

    fun saveStoreSettings(settings: StoreSettings) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveStoreSettings(settings)
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Control Panel settings saved" },
                onFailure = { it.localizedMessage ?: "Failed to save settings" }
            )
        }
    }

    fun addAdmin(uid: String, email: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.addAdmin(uid, email)
            _isBusy.value = false
            _actionMessage.value = result.fold(
                onSuccess = { "Admin added" },
                onFailure = { it.localizedMessage ?: "Failed to add admin" }
            )
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
