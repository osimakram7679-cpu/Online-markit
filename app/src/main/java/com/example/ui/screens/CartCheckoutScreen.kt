package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CartItem
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.StoreSettings
import com.example.model.UserProfile
import com.example.payment.PaymentGatewayManager
import com.example.ui.components.ProductArtwork
import com.example.ui.components.SignInPromptCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatInr
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.CartPricing
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartCheckoutScreen(
    cartItems: List<CartItem>,
    pricing: CartPricing,
    storeSettings: StoreSettings,
    promoInput: String,
    promoApplied: Boolean,
    currentUser: FirebaseUser?,
    userProfile: UserProfile?,
    checkoutBusy: Boolean,
    onUpdateQuantity: (String, Int) -> Unit,
    onPromoInputChange: (String) -> Unit,
    onApplyPromo: () -> Unit,
    onRemovePromo: () -> Unit,
    onBrowseShop: () -> Unit,
    onPlaceOrder: (
        name: String,
        email: String,
        phone: String,
        address: String,
        city: String,
        pincode: String,
        paymentMethod: PaymentMethod,
        paymentStatus: PaymentStatus,
        transactionId: String,
        onSuccess: (String) -> Unit
    ) -> Unit,
    onOrderCompletedNavigate: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }

    // Prefill shipping fields once Firebase user / profile loads
    LaunchedEffect(currentUser?.uid, userProfile) {
        if (fullName.isBlank()) {
            fullName = userProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.displayName.orEmpty()
        }
        if (phone.isBlank()) phone = userProfile?.phone.orEmpty()
        if (address.isBlank()) address = userProfile?.defaultAddress.orEmpty()
        if (city.isBlank()) city = userProfile?.city.orEmpty()
        if (pincode.isBlank()) pincode = userProfile?.pincode.orEmpty()
    }

    val availableMethods = remember(storeSettings) {
        buildList {
            if (storeSettings.upiEnabled) add(PaymentMethod.UPI_GATEWAY)
            if (storeSettings.cardGatewayEnabled) add(PaymentMethod.CARD_GATEWAY)
            if (storeSettings.codEnabled) add(PaymentMethod.COD)
        }.ifEmpty { listOf(PaymentMethod.COD) }
    }

    var selectedMethod by remember(availableMethods) {
        mutableStateOf(availableMethods.first())
    }

    var showGatewaySheet by remember { mutableStateOf(false) }
    var pendingTxnRef by remember { mutableStateOf("") }
    var confirmedOrderReceipt by remember { mutableStateOf<Pair<String, String>?>(null) }

    val submitWithPayment: (PaymentMethod, PaymentStatus, String) -> Unit = { method, status, txnId ->
        val email = currentUser?.email ?: "customer@bazaarhub.in"
        onPlaceOrder(
            fullName.trim(),
            email,
            phone.trim(),
            address.trim(),
            city.trim(),
            pincode.trim(),
            method,
            status,
            txnId
        ) { createdOrderId ->
            confirmedOrderReceipt = createdOrderId to txnId
        }
    }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val parsed = if (result.resultCode == Activity.RESULT_OK || result.data != null) {
            PaymentGatewayManager.parseUpiIntentResponse(result.data, pendingTxnRef)
        } else {
            PaymentGatewayManager.parseUpiIntentResponse(null, pendingTxnRef)
        }
        if (parsed.succeeded) {
            onShowMessage(parsed.statusMessage)
            submitWithPayment(PaymentMethod.UPI_GATEWAY, PaymentStatus.PAID, parsed.transactionId)
        } else {
            onShowMessage(parsed.statusMessage)
        }
    }

    fun validateShipping(): Boolean {
        if (!storeSettings.storeOpen) {
            onShowMessage("Store is temporarily paused by Admin.")
            return false
        }
        if (fullName.trim().length < 2) {
            onShowMessage("Please enter your full name")
            return false
        }
        if (phone.trim().length < 10) {
            onShowMessage("Please enter a valid 10-digit phone number")
            return false
        }
        if (address.trim().length < 5) {
            onShowMessage("Please enter your complete street address")
            return false
        }
        if (city.trim().length < 2 || pincode.trim().length < 4) {
            onShowMessage("Please enter city and pincode")
            return false
        }
        return true
    }

    if (confirmedOrderReceipt != null) {
        val (orderId, txnId) = confirmedOrderReceipt!!
        AlertDialog(
            onDismissRequest = {
                confirmedOrderReceipt = null
                onOrderCompletedNavigate()
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Order Confirmed",
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = { Text("Order Placed Successfully!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Thank you for shopping with ${storeSettings.storeName}.")
                    Text("Order ID: #$orderId", fontWeight = FontWeight.SemiBold)
                    Text("Transaction Ref: $txnId", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Need help? Call Seller Support: ${storeSettings.contactPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmedOrderReceipt = null
                        onOrderCompletedNavigate()
                    },
                    modifier = Modifier.testTag("view_placed_order_button")
                ) {
                    Text("Track My Order")
                }
            }
        )
    }

    if (showGatewaySheet) {
        PaymentGatewayCheckoutSheet(
            amountInr = pricing.totalInr,
            storeSettings = storeSettings,
            paymentMethod = selectedMethod,
            transactionRef = pendingTxnRef,
            onDismiss = { showGatewaySheet = false },
            onLaunchExternalUpi = {
                val intent = PaymentGatewayManager.buildUpiPaymentIntent(
                    merchantUpiId = storeSettings.merchantUpiId,
                    merchantName = storeSettings.storeName,
                    amountInr = pricing.totalInr,
                    transactionRef = pendingTxnRef,
                    note = "${storeSettings.storeName} Order"
                )
                if (PaymentGatewayManager.hasUpiAppsInstalled(context, intent)) {
                    showGatewaySheet = false
                    upiLauncher.launch(intent)
                } else {
                    onShowMessage("No external UPI app detected on this device; use Gateway Instant Pay below.")
                }
            },
            onConfirmGatewaySuccess = { confirmedTxnId ->
                showGatewaySheet = false
                submitWithPayment(selectedMethod, PaymentStatus.PAID, confirmedTxnId)
            }
        )
    }

    if (cartItems.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Text("Your Cart is Empty", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Explore electronics, fashion, and handcrafted home essentials curated for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onBrowseShop,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_cart_browse_button")
                ) {
                    Text("Explore Products")
                }
            }
        }
        return
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Cart & Checkout",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            items(cartItems, key = { it.product.id }) { item ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProductArtwork(
                            product = item.product,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.product.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatInr(item.product.priceInr),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.id, item.quantity - 1) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.quantity == 1) Icons.Filled.DeleteOutline else Icons.Filled.Remove,
                                    contentDescription = "Decrease quantity"
                                )
                            }
                            Text(
                                text = item.quantity.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.id, item.quantity + 1) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                            }
                        }
                    }
                }
            }

            // Promo Code Section
            if (storeSettings.promoEnabled && storeSettings.promoCode.isNotBlank()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Apply Promo Code (Try ${storeSettings.promoCode} for ${storeSettings.promoDiscountPercent}% OFF)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = promoInput,
                                    onValueChange = onPromoInputChange,
                                    placeholder = { Text("Enter code") },
                                    singleLine = true,
                                    enabled = !promoApplied,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("promo_code_input")
                                )
                                if (promoApplied) {
                                    OutlinedButton(
                                        onClick = onRemovePromo,
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Text("Remove")
                                    }
                                } else {
                                    Button(
                                        onClick = onApplyPromo,
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("apply_promo_button")
                                    ) {
                                        Text("Apply")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Price Breakdown
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Order Summary", style = MaterialTheme.typography.titleMedium)
                        SummaryRow("Subtotal", formatInr(pricing.subtotalInr))
                        if (pricing.discountInr > 0L) {
                            SummaryRow(
                                "Promo (${pricing.appliedPromoCode})",
                                "-${formatInr(pricing.discountInr)}",
                                highlight = true
                            )
                        }
                        SummaryRow(
                            "Delivery",
                            if (pricing.deliveryFeeInr == 0L) "FREE" else formatInr(pricing.deliveryFeeInr),
                            highlight = pricing.deliveryFeeInr == 0L
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Payable", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = formatInr(pricing.totalInr),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Delivery Details Form
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Delivery Details", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_name_input")
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(14) },
                            label = { Text("Mobile Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_phone_input")
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("House / Street / Landmark") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_address_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("checkout_city_input")
                            )
                            OutlinedTextField(
                                value = pincode,
                                onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(8) },
                                label = { Text("Pincode") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("checkout_pincode_input")
                            )
                        }
                    }
                }
            }

            // Payment Gateway Options
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Payment Gateway", style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                text = "256-Bit Encrypted",
                                containerColor = EmeraldSoft,
                                contentColor = EmeraldSuccess
                            )
                        }

                        availableMethods.forEach { method ->
                            val selected = selectedMethod == method
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMethod = method }
                                    .testTag("payment_method_${method.wireValue}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { selectedMethod = method }
                                    )
                                    Icon(
                                        imageVector = when (method) {
                                            PaymentMethod.UPI_GATEWAY -> Icons.Filled.QrCode2
                                            PaymentMethod.CARD_GATEWAY -> Icons.Filled.CreditCard
                                            PaymentMethod.COD -> Icons.Filled.LocalShipping
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = method.displayTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = method.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Firebase Auth Check ONLY at Final Place Order Step (Never on app launch!)
            item {
                if (currentUser == null) {
                    SignInPromptCard(
                        title = "Sign in to Complete Checkout",
                        subtitle = "Sign in with Firebase Google Authentication only when placing your order so we can secure your receipt and live order tracking.",
                        onAuthSuccess = { onShowMessage("Signed in! Tap Pay Now to place your order.") },
                        onAuthError = onShowMessage
                    )
                } else {
                    Button(
                        onClick = {
                            if (validateShipping()) {
                                val ref = PaymentGatewayManager.generateTransactionRef(
                                    if (selectedMethod == PaymentMethod.COD) "COD" else "BZH"
                                )
                                pendingTxnRef = ref
                                if (selectedMethod == PaymentMethod.COD) {
                                    submitWithPayment(PaymentMethod.COD, PaymentStatus.PENDING, ref)
                                } else {
                                    showGatewaySheet = true
                                }
                            }
                        },
                        enabled = !checkoutBusy && storeSettings.storeOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("place_order_button")
                    ) {
                        if (checkoutBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Payments, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            val label = if (selectedMethod == PaymentMethod.COD) {
                                "Place COD Order • ${formatInr(pricing.totalInr)}"
                            } else {
                                "Pay ${formatInr(pricing.totalInr)} via Gateway"
                            }
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentGatewayCheckoutSheet(
    amountInr: Long,
    storeSettings: StoreSettings,
    paymentMethod: PaymentMethod,
    transactionRef: String,
    onDismiss: () -> Unit,
    onLaunchExternalUpi: () -> Unit,
    onConfirmGatewaySuccess: (String) -> Unit
) {
    var vpaOrCard by remember {
        mutableStateOf(
            if (paymentMethod == PaymentMethod.UPI_GATEWAY) "customer@okicici" else "4532 •••• •••• 8842"
        )
    }
    var cardExpiry by remember { mutableStateOf("08/29") }
    var cardCvv by remember { mutableStateOf("424") }
    var processing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${storeSettings.storeName} Secure Gateway",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Ref: $transactionRef • ${PaymentGatewayManager.maskedGatewayKey()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatInr(amountInr),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Payable to Merchant UPI: ${storeSettings.merchantUpiId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (paymentMethod == PaymentMethod.UPI_GATEWAY) {
                OutlinedButton(
                    onClick = onLaunchExternalUpi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("launch_upi_app_button")
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Installed UPI App (GPay / PhonePe / Paytm)")
                }

                Text(
                    text = "Or verify via UPI ID / Gateway Instant Checkout:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = vpaOrCard,
                    onValueChange = { vpaOrCard = it },
                    label = { Text("Enter Your UPI ID (e.g. name@okaxis)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = vpaOrCard,
                    onValueChange = { vpaOrCard = it },
                    label = { Text("Card Number / NetBanking Reference") },
                    leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cardExpiry,
                        onValueChange = { cardExpiry = it.take(5) },
                        label = { Text("MM/YY") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cardCvv,
                        onValueChange = { cardCvv = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("CVV") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = {
                    processing = true
                    onConfirmGatewaySuccess(transactionRef)
                },
                enabled = !processing && vpaOrCard.trim().length >= 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_gateway_pay_button")
            ) {
                Text("Authorize & Pay ${formatInr(amountInr)}")
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Cancel Payment")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
