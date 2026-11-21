package com.example.data

import android.content.Context
import com.example.R
import com.example.model.Order
import com.example.model.OrderItem
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.StarterCatalog
import com.example.model.StoreSettings
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ShopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google before completing this action.")
    }

    // --- Public Storefront Streams (No Login Wall on Launch) ---

    fun observeProducts(): Flow<List<Product>> =
        db.collection("products")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Product::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, "products")
                throw error
            }

    fun observeStoreSettings(): Flow<StoreSettings> =
        db.collection("store_settings").document("config")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        StoreSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: StoreSettings()
                } else {
                    StoreSettings()
                }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(
                    firestoreError ?: error,
                    OperationType.GET,
                    "store_settings/config"
                )
                throw error
            }

    // --- Authenticated Customer Streams & Mutations ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emit(null)
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeMyOrders(userId: String): Flow<List<Order>> {
        val path = "orders"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Order::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun saveUserProfile(
        displayName: String,
        email: String,
        phone: String?,
        defaultAddress: String?,
        city: String?,
        pincode: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(80)
            val cleanEmail = email.trim().take(120)
            require(cleanName.isNotEmpty()) { "Name is required" }
            require(cleanEmail.length >= 3) { "Valid email is required" }

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existingSnap = docRef.get().await()

            val optionalFields = buildMap<String, Any> {
                phone?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone", it.take(20)) }
                defaultAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { put("defaultAddress", it.take(300)) }
                city?.trim()?.takeIf { it.isNotEmpty() }?.let { put("city", it.take(60)) }
                pincode?.trim()?.takeIf { it.isNotEmpty() }?.let { put("pincode", it.take(12)) }
            }

            if (existingSnap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                updates.putAll(optionalFields)
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                payload.putAll(optionalFields)
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun placeOrder(
        customerName: String,
        customerEmail: String,
        customerPhone: String,
        shippingAddress: String,
        city: String,
        pincode: String,
        items: List<OrderItem>,
        subtotalInr: Long,
        discountInr: Long,
        deliveryFeeInr: Long,
        totalInr: Long,
        promoCodeApplied: String?,
        paymentMethod: PaymentMethod,
        paymentStatus: PaymentStatus,
        transactionId: String
    ): Result<String> {
        var docPath = "orders"
        return try {
            val uid = requireUserId()
            require(customerName.trim().length in 2..80) { "Enter a valid full name (2-80 chars)" }
            require(customerPhone.trim().length in 8..20) { "Enter a valid 10-digit phone number" }
            require(shippingAddress.trim().length in 5..300) { "Enter complete delivery address" }
            require(city.trim().length in 2..60) { "Enter a valid city" }
            require(pincode.trim().length in 4..12) { "Enter a valid pincode" }
            require(items.isNotEmpty() && items.size <= 30) { "Cart must contain 1 to 30 distinct items" }
            require(subtotalInr >= 1L && totalInr >= 1L) { "Order total must be at least ₹1" }

            val docRef = db.collection("orders").document()
            docPath = docRef.path

            val summary = items.joinToString(", ") { "${it.title} x${it.quantity}" }.take(1000)
            val totalQty = items.sumOf { it.quantity }.coerceIn(1L, 500L)
            val serializedItems = items.map { item ->
                mapOf(
                    "productId" to item.productId,
                    "title" to item.title,
                    "priceInr" to item.priceInr,
                    "quantity" to item.quantity
                )
            }

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "customerName" to customerName.trim().take(80),
                "customerEmail" to customerEmail.trim().ifBlank { "customer@bazaarhub.in" }.take(120),
                "customerPhone" to customerPhone.trim().take(20),
                "shippingAddress" to shippingAddress.trim().take(300),
                "city" to city.trim().take(60),
                "pincode" to pincode.trim().take(12),
                "itemsSummary" to summary.ifBlank { "Order items" },
                "items" to serializedItems,
                "itemCount" to totalQty,
                "subtotalInr" to subtotalInr,
                "discountInr" to discountInr.coerceIn(0L, subtotalInr),
                "deliveryFeeInr" to deliveryFeeInr.coerceIn(0L, 5000L),
                "totalInr" to totalInr,
                "paymentMethod" to paymentMethod.wireValue,
                "paymentStatus" to paymentStatus.wireValue,
                "transactionId" to transactionId.trim().take(100),
                "orderStatus" to OrderStatus.PLACED.wireValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            promoCodeApplied?.trim()?.takeIf { it.isNotEmpty() }?.let {
                payload["promoCodeApplied"] = it.take(24)
            }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(orderId: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "Invalid order ID" }
            db.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "orderStatus" to OrderStatus.CANCELLED.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- Admin Role & Management Operations ---

    suspend fun checkIsAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        if (user.email.equals(StarterCatalog.ADMIN_BOOTSTRAP_EMAIL, ignoreCase = true) && user.isEmailVerified) {
            return true
        }
        return try {
            val snap = db.collection("admins").document(user.uid).get().await()
            snap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    fun observeAllOrders(): Flow<List<Order>> =
        db.collection("orders")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Order::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, "orders")
                throw error
            }

    suspend fun adminUpdateOrder(
        orderId: String,
        orderStatus: OrderStatus,
        paymentStatus: PaymentStatus,
        transactionId: String
    ): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "Invalid order ID" }
            db.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "orderStatus" to orderStatus.wireValue,
                        "paymentStatus" to paymentStatus.wireValue,
                        "transactionId" to transactionId.trim().take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertProduct(
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
        active: Boolean
    ): Result<Unit> {
        var docPath = "products"
        return try {
            requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim()
            val validCat = ProductCategory.fromRaw(category).label
            require(cleanTitle.length in 2..100) { "Title must be 2 to 100 characters" }
            require(cleanDesc.length <= 1000) { "Description must be under 1000 characters" }
            require(priceInr in 1L..10_000_000L) { "Price must be at least ₹1" }
            val safeMrp = mrpInr.coerceAtLeast(priceInr).coerceAtMost(10_000_000L)
            val safeStock = stock.coerceIn(0L, 100_000L)
            val cleanPreset = imagePreset.trim().ifBlank { "electronics" }.take(40)
            val cleanUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

            if (productId.isNullOrBlank()) {
                val docRef = db.collection("products").document()
                docPath = docRef.path
                val payload = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "priceInr" to priceInr,
                    "mrpInr" to safeMrp,
                    "category" to validCat,
                    "imagePreset" to cleanPreset,
                    "stock" to safeStock,
                    "featured" to featured,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                cleanUrl?.let { payload["imageUrl"] = it }
                docRef.set(payload).await()
            } else {
                val docRef = db.collection("products").document(productId)
                docPath = docRef.path
                val updates = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "priceInr" to priceInr,
                    "mrpInr" to safeMrp,
                    "category" to validCat,
                    "imagePreset" to cleanPreset,
                    "stock" to safeStock,
                    "featured" to featured,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanUrl != null) {
                    updates["imageUrl"] = cleanUrl
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val docPath = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Invalid product ID" }
            db.collection("products").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterProducts(): Result<Unit> {
        return try {
            requireUserId()
            val batch = db.batch()
            StarterCatalog.defaultProducts.forEach { prod ->
                val docRef = db.collection("products").document(prod.id)
                val payload = mapOf(
                    "title" to prod.title,
                    "description" to prod.description,
                    "priceInr" to prod.priceInr,
                    "mrpInr" to prod.mrpInr,
                    "category" to prod.category,
                    "imagePreset" to prod.imagePreset,
                    "stock" to prod.stock,
                    "featured" to prod.featured,
                    "active" to prod.active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "products")
            Result.failure(e)
        }
    }

    suspend fun saveStoreSettings(settings: StoreSettings): Result<Unit> {
        val docPath = "store_settings/config"
        return try {
            requireUserId()
            require(settings.storeName.trim().length in 2..60) { "Store name must be 2-60 chars" }
            require(settings.contactPhone.trim().length in 8..20) { "Contact phone must be 8-20 chars" }
            require(settings.contactEmail.trim().length in 5..100) { "Contact email must be 5-100 chars" }
            require(settings.merchantUpiId.trim().length in 3..80) { "Merchant UPI ID must be 3-80 chars" }

            val payload = mapOf(
                "storeName" to settings.storeName.trim().take(60),
                "tagline" to settings.tagline.trim().take(120),
                "contactPhone" to settings.contactPhone.trim().take(20),
                "contactEmail" to settings.contactEmail.trim().take(100),
                "merchantUpiId" to settings.merchantUpiId.trim().take(80),
                "announcementText" to settings.announcementText.trim().take(200),
                "storeOpen" to settings.storeOpen,
                "upiEnabled" to settings.upiEnabled,
                "cardGatewayEnabled" to settings.cardGatewayEnabled,
                "codEnabled" to settings.codEnabled,
                "promoEnabled" to settings.promoEnabled,
                "promoCode" to settings.promoCode.trim().uppercase().take(24),
                "promoDiscountPercent" to settings.promoDiscountPercent.coerceIn(0L, 80L),
                "deliveryFeeInr" to settings.deliveryFeeInr.coerceIn(0L, 2000L),
                "freeDeliveryAboveInr" to settings.freeDeliveryAboveInr.coerceIn(0L, 100000L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("store_settings").document("config").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addAdmin(uid: String, email: String): Result<Unit> {
        val cleanUid = uid.trim()
        val docPath = "admins/$cleanUid"
        return try {
            val currentUid = requireUserId()
            require(cleanUid.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) { "Invalid UID format" }
            require(email.trim().length in 3..120) { "Valid email required" }
            val payload = mapOf(
                "uid" to cleanUid,
                "email" to email.trim().take(120),
                "addedBy" to currentUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("admins").document(cleanUid).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
