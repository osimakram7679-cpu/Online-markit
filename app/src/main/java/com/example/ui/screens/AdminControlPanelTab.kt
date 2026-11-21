package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.StoreSettings
import com.example.payment.PaymentGatewayManager

@Composable
fun AdminControlPanelTab(
    storeSettings: StoreSettings,
    isBusy: Boolean,
    onSaveSettings: (StoreSettings) -> Unit,
    onAddAdmin: (uid: String, email: String) -> Unit
) {
    var storeName by remember { mutableStateOf(storeSettings.storeName) }
    var tagline by remember { mutableStateOf(storeSettings.tagline) }
    var contactPhone by remember { mutableStateOf(storeSettings.contactPhone) }
    var contactEmail by remember { mutableStateOf(storeSettings.contactEmail) }
    var merchantUpiId by remember { mutableStateOf(storeSettings.merchantUpiId) }
    var announcementText by remember { mutableStateOf(storeSettings.announcementText) }

    var storeOpen by remember { mutableStateOf(storeSettings.storeOpen) }
    var upiEnabled by remember { mutableStateOf(storeSettings.upiEnabled) }
    var cardGatewayEnabled by remember { mutableStateOf(storeSettings.cardGatewayEnabled) }
    var codEnabled by remember { mutableStateOf(storeSettings.codEnabled) }
    var promoEnabled by remember { mutableStateOf(storeSettings.promoEnabled) }

    var promoCode by remember { mutableStateOf(storeSettings.promoCode) }
    var promoDiscount by remember { mutableStateOf(storeSettings.promoDiscountPercent.toString()) }
    var deliveryFee by remember { mutableStateOf(storeSettings.deliveryFeeInr.toString()) }
    var freeDeliveryAbove by remember { mutableStateOf(storeSettings.freeDeliveryAboveInr.toString()) }

    var newAdminUid by remember { mutableStateOf("") }
    var newAdminEmail by remember { mutableStateOf("") }

    LaunchedEffect(storeSettings) {
        storeName = storeSettings.storeName
        tagline = storeSettings.tagline
        contactPhone = storeSettings.contactPhone
        contactEmail = storeSettings.contactEmail
        merchantUpiId = storeSettings.merchantUpiId
        announcementText = storeSettings.announcementText
        storeOpen = storeSettings.storeOpen
        upiEnabled = storeSettings.upiEnabled
        cardGatewayEnabled = storeSettings.cardGatewayEnabled
        codEnabled = storeSettings.codEnabled
        promoEnabled = storeSettings.promoEnabled
        promoCode = storeSettings.promoCode
        promoDiscount = storeSettings.promoDiscountPercent.toString()
        deliveryFee = storeSettings.deliveryFeeInr.toString()
        freeDeliveryAbove = storeSettings.freeDeliveryAboveInr.toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Store Operations & Identity
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Storefront Control Panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    ControlSwitchRow(
                        title = "Store Open for Orders",
                        subtitle = if (storeOpen) "Customers can browse and place orders" else "Maintenance mode active",
                        checked = storeOpen,
                        onCheckedChange = { storeOpen = it },
                        testTag = "control_store_open_switch"
                    )

                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Store Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Store Tagline") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = announcementText,
                        onValueChange = { announcementText = it },
                        label = { Text("Top Announcement Strip") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
                        label = { Text("Seller Contact Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        label = { Text("Support Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Payment Gateway Controls
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Payment Gateway & Pricing Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Gateway Key Status: ${PaymentGatewayManager.maskedGatewayKey()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = merchantUpiId,
                        onValueChange = { merchantUpiId = it },
                        label = { Text("Merchant UPI ID (pa)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ControlSwitchRow(
                        title = "UPI Payment Gateway",
                        subtitle = "GPay, PhonePe, Paytm, BHIM & VPA Checkout",
                        checked = upiEnabled,
                        onCheckedChange = { upiEnabled = it },
                        testTag = "control_upi_switch"
                    )
                    ControlSwitchRow(
                        title = "Card / NetBanking Gateway",
                        subtitle = "Debit, Credit Cards & NetBanking",
                        checked = cardGatewayEnabled,
                        onCheckedChange = { cardGatewayEnabled = it },
                        testTag = "control_card_switch"
                    )
                    ControlSwitchRow(
                        title = "Cash on Delivery (COD)",
                        subtitle = "Pay on doorstep delivery",
                        checked = codEnabled,
                        onCheckedChange = { codEnabled = it },
                        testTag = "control_cod_switch"
                    )

                    ControlSwitchRow(
                        title = "Active Promo Code",
                        subtitle = "Allow discount code at checkout",
                        checked = promoEnabled,
                        onCheckedChange = { promoEnabled = it },
                        testTag = "control_promo_switch"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = promoCode,
                            onValueChange = { promoCode = it.uppercase() },
                            label = { Text("Promo Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = promoDiscount,
                            onValueChange = { promoDiscount = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Discount %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = deliveryFee,
                            onValueChange = { deliveryFee = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("Delivery Fee (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = freeDeliveryAbove,
                            onValueChange = { freeDeliveryAbove = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Free Above (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            onSaveSettings(
                                StoreSettings(
                                    storeName = storeName,
                                    tagline = tagline,
                                    contactPhone = contactPhone,
                                    contactEmail = contactEmail,
                                    merchantUpiId = merchantUpiId,
                                    announcementText = announcementText,
                                    storeOpen = storeOpen,
                                    upiEnabled = upiEnabled,
                                    cardGatewayEnabled = cardGatewayEnabled,
                                    codEnabled = codEnabled,
                                    promoEnabled = promoEnabled,
                                    promoCode = promoCode,
                                    promoDiscountPercent = (promoDiscount.toLongOrNull() ?: 0L).coerceIn(0L, 80L),
                                    deliveryFeeInr = (deliveryFee.toLongOrNull() ?: 0L).coerceIn(0L, 2000L),
                                    freeDeliveryAboveInr = (freeDeliveryAbove.toLongOrNull() ?: 999L).coerceIn(0L, 100000L)
                                )
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_control_panel_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Control Panel Settings")
                    }
                }
            }
        }

        // Co-Admin Access Card
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Grant Co-Admin Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = newAdminUid,
                        onValueChange = { newAdminUid = it },
                        label = { Text("User Firebase UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAdminEmail,
                        onValueChange = { newAdminEmail = it },
                        label = { Text("User Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            if (newAdminUid.isNotBlank() && newAdminEmail.isNotBlank()) {
                                onAddAdmin(newAdminUid, newAdminEmail)
                                newAdminUid = ""
                                newAdminEmail = ""
                            }
                        },
                        enabled = !isBusy && newAdminUid.isNotBlank() && newAdminEmail.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Add Admin UID")
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
