package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.signOut
import com.example.model.StarterCatalog
import com.example.model.StoreSettings
import com.example.model.UserProfile
import com.example.ui.components.ContactSupportCard
import com.example.ui.components.SignInPromptCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldSuccess
import com.google.firebase.auth.FirebaseUser

@Composable
fun AccountAndControlScreen(
    currentUser: FirebaseUser?,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    storeSettings: StoreSettings,
    isBusy: Boolean,
    onSaveProfile: (name: String, email: String, phone: String?, address: String?, city: String?, pincode: String?) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Account & Control", style = MaterialTheme.typography.headlineMedium)
            }

            // Seller Contact & Support Card (Always accessible without sign-in)
            item {
                ContactSupportCard(settings = storeSettings)
            }

            if (currentUser == null) {
                item {
                    SignInPromptCard(
                        title = "Firebase Account & Admin Access",
                        subtitle = "Sign in with Google only when you want to manage saved addresses, track orders, or open the Store Control Panel & Admin Dashboard.",
                        onAuthSuccess = { onShowMessage("Welcome to ${storeSettings.storeName}!") },
                        onAuthError = onShowMessage
                    )
                }
            } else {
                // Admin Dashboard & Control Panel Entry
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAdmin) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = "Admin Control Panel",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Admin Dashboard & Control Panel",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isAdmin) {
                                            "Manage products, fulfill orders, and configure payment gateways."
                                        } else {
                                            "Admin privileges are granted to ${StarterCatalog.ADMIN_BOOTSTRAP_EMAIL} or verified Admin UIDs."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isAdmin) {
                                    StatusPill(
                                        text = "ADMIN",
                                        containerColor = EmeraldSoft,
                                        contentColor = EmeraldSuccess
                                    )
                                }
                            }

                            if (isAdmin) {
                                Button(
                                    onClick = onOpenAdminDashboard,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("open_admin_dashboard_button")
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Admin Dashboard & Control Panel")
                                }
                            } else {
                                Text(
                                    text = "Your UID: ${currentUser.uid}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Signed-in User Profile & Default Shipping Editor
                item {
                    var name by remember(currentUser.uid) {
                        mutableStateOf(userProfile?.displayName ?: currentUser.displayName.orEmpty())
                    }
                    var phone by remember(currentUser.uid) {
                        mutableStateOf(userProfile?.phone.orEmpty())
                    }
                    var address by remember(currentUser.uid) {
                        mutableStateOf(userProfile?.defaultAddress.orEmpty())
                    }
                    var city by remember(currentUser.uid) {
                        mutableStateOf(userProfile?.city.orEmpty())
                    }
                    var pincode by remember(currentUser.uid) {
                        mutableStateOf(userProfile?.pincode.orEmpty())
                    }

                    LaunchedEffect(userProfile) {
                        userProfile?.let { p ->
                            if (p.displayName.isNotBlank()) name = p.displayName
                            p.phone?.let { phone = it }
                            p.defaultAddress?.let { address = it }
                            p.city?.let { city = it }
                            p.pincode?.let { pincode = it }
                        }
                    }

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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = currentUser.displayName ?: "Signed-in Shopper",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentUser.email.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(14) },
                                label = { Text("Saved Phone Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Saved Shipping Address") },
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = city,
                                    onValueChange = { city = it },
                                    label = { Text("City") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = pincode,
                                    onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(8) },
                                    label = { Text("Pincode") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    onSaveProfile(
                                        name.ifBlank { "Shopper" },
                                        currentUser.email ?: "user@bazaarhub.in",
                                        phone,
                                        address,
                                        city,
                                        pincode
                                    )
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_profile_button")
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Profile & Address")
                            }

                            OutlinedButton(
                                onClick = {
                                    signOut(credentialManager, scope) {
                                        onShowMessage("Signed out")
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }
        }
    }
}
