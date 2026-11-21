package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.CartDataStore
import com.example.data.ShopRepository
import com.example.data.authStateFlow
import com.example.model.Order
import com.example.model.Product
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.screens.AccountAndControlScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CartCheckoutScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.screens.ShopScreen
import com.example.ui.theme.BazaarHubTheme
import com.example.viewmodel.AccountViewModel
import com.example.viewmodel.StoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SHOP("Shop", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    CART("Cart", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag),
    ORDERS("Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    ACCOUNT("Control", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BazaarHubTheme {
                BazaarHubApp()
            }
        }
    }
}

@Composable
fun BazaarHubApp(auth: FirebaseAuth = Firebase.auth) {
    // StoreViewModel runs public catalog + DataStore cart with zero forced login on startup
    val storeViewModel: StoreViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                StoreViewModel(
                    repository = ShopRepository(db),
                    cartDataStore = CartDataStore(app)
                )
            }
        }
    )

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val productsState by storeViewModel.productsState.collectAsStateWithLifecycle()
    val allProducts by storeViewModel.allProducts.collectAsStateWithLifecycle()
    val storeSettings by storeViewModel.storeSettings.collectAsStateWithLifecycle()
    val searchQuery by storeViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by storeViewModel.selectedCategory.collectAsStateWithLifecycle()
    val cartQuantities by storeViewModel.cartQuantities.collectAsStateWithLifecycle()
    val cartItems by storeViewModel.cartItems.collectAsStateWithLifecycle()
    val totalCartCount by storeViewModel.totalCartCount.collectAsStateWithLifecycle()
    val cartPricing by storeViewModel.cartPricing.collectAsStateWithLifecycle()
    val promoInput by storeViewModel.promoInput.collectAsStateWithLifecycle()
    val promoApplied by storeViewModel.promoApplied.collectAsStateWithLifecycle()
    val checkoutBusy by storeViewModel.checkoutBusy.collectAsStateWithLifecycle()
    val checkoutFeedback by storeViewModel.checkoutFeedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showMessage: (String) -> Unit = remember(snackbarHostState, scope) {
        { msg ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    LaunchedEffect(checkoutFeedback) {
        checkoutFeedback?.let { msg ->
            showMessage(msg)
            storeViewModel.clearFeedback()
        }
    }

    var activeTab by remember { mutableStateOf(MainTab.SHOP) }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var showAdminDashboard by remember { mutableStateOf(false) }

    // Authenticated ViewModel is strictly gated behind currentUser != null & keyed by uid
    val user = currentUser
    if (user != null) {
        key(user.uid) {
            val accountViewModel: AccountViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        AccountViewModel(
                            repository = ShopRepository(db),
                            currentUserId = user.uid,
                            firebaseUser = user
                        )
                    }
                }
            )

            val isAdmin by accountViewModel.isAdmin.collectAsStateWithLifecycle()
            val userProfile by accountViewModel.userProfile.collectAsStateWithLifecycle()
            val myOrdersState by accountViewModel.myOrdersState.collectAsStateWithLifecycle()
            val adminOrdersState by accountViewModel.adminOrdersState.collectAsStateWithLifecycle()
            val actionMessage by accountViewModel.actionMessage.collectAsStateWithLifecycle()
            val isBusy by accountViewModel.isBusy.collectAsStateWithLifecycle()

            LaunchedEffect(actionMessage) {
                actionMessage?.let { msg ->
                    showMessage(msg)
                    accountViewModel.clearMessage()
                }
            }

            if (showAdminDashboard && isAdmin) {
                BackHandler { showAdminDashboard = false }
                AdminDashboardScreen(
                    productsState = productsState,
                    adminOrdersState = adminOrdersState,
                    storeSettings = storeSettings,
                    isBusy = isBusy,
                    onBack = { showAdminDashboard = false },
                    onUpsertProduct = accountViewModel::upsertProduct,
                    onDeleteProduct = accountViewModel::deleteProduct,
                    onSeedStarterProducts = accountViewModel::seedStarterProducts,
                    onUpdateOrder = accountViewModel::adminUpdateOrder,
                    onSaveStoreSettings = accountViewModel::saveStoreSettings,
                    onAddAdmin = accountViewModel::addAdmin
                )
            } else {
                val selectedProduct = selectedProductId?.let { id -> allProducts.firstOrNull { it.id == id } }
                if (selectedProduct != null) {
                    BackHandler { selectedProductId = null }
                    ProductDetailScreen(
                        product = selectedProduct,
                        storeSettings = storeSettings,
                        onBack = { selectedProductId = null },
                        onAddToCart = { qty ->
                            storeViewModel.addToCart(selectedProduct, qty)
                            showMessage("Added ${selectedProduct.title} to cart")
                        },
                        onBuyNow = { qty ->
                            storeViewModel.addToCart(selectedProduct, qty)
                            selectedProductId = null
                            activeTab = MainTab.CART
                        }
                    )
                } else {
                    MainTabShell(
                        activeTab = activeTab,
                        onSelectTab = { activeTab = it },
                        totalCartCount = totalCartCount,
                        snackbarHostState = snackbarHostState,
                        shopContent = {
                            ShopScreen(
                                productsState = productsState,
                                storeSettings = storeSettings,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                cartQuantities = cartQuantities,
                                onSearchChange = storeViewModel::updateSearchQuery,
                                onSelectCategory = storeViewModel::selectCategory,
                                onProductClick = { selectedProductId = it.id },
                                onAddToCart = { prod ->
                                    storeViewModel.addToCart(prod, 1)
                                    showMessage("Added ${prod.title} to cart")
                                }
                            )
                        },
                        cartContent = {
                            CartCheckoutScreen(
                                cartItems = cartItems,
                                pricing = cartPricing,
                                storeSettings = storeSettings,
                                promoInput = promoInput,
                                promoApplied = promoApplied,
                                currentUser = user,
                                userProfile = userProfile,
                                checkoutBusy = checkoutBusy,
                                onUpdateQuantity = storeViewModel::updateCartQuantity,
                                onPromoInputChange = storeViewModel::updatePromoInput,
                                onApplyPromo = { storeViewModel.applyPromoCode() },
                                onRemovePromo = storeViewModel::removePromoCode,
                                onBrowseShop = { activeTab = MainTab.SHOP },
                                onPlaceOrder = { name, email, phone, addr, city, pin, method, pStatus, txnId, onDone ->
                                    storeViewModel.submitOrder(
                                        customerName = name,
                                        customerEmail = email,
                                        customerPhone = phone,
                                        shippingAddress = addr,
                                        city = city,
                                        pincode = pin,
                                        paymentMethod = method,
                                        paymentStatus = pStatus,
                                        transactionId = txnId,
                                        onOrderPlaced = onDone
                                    )
                                },
                                onOrderCompletedNavigate = { activeTab = MainTab.ORDERS },
                                onShowMessage = showMessage
                            )
                        },
                        ordersContent = {
                            OrdersScreen(
                                currentUser = user,
                                ordersState = myOrdersState,
                                storeSettings = storeSettings,
                                onCancelOrder = accountViewModel::cancelOrder,
                                onShowMessage = showMessage
                            )
                        },
                        accountContent = {
                            AccountAndControlScreen(
                                currentUser = user,
                                userProfile = userProfile,
                                isAdmin = isAdmin,
                                storeSettings = storeSettings,
                                isBusy = isBusy,
                                onSaveProfile = accountViewModel::saveProfile,
                                onOpenAdminDashboard = { showAdminDashboard = true },
                                onShowMessage = showMessage
                            )
                        }
                    )
                }
            }
        }
    } else {
        // Guest mode: Browse catalog, cart, and store contact immediately without Gmail prompt
        val selectedProduct = selectedProductId?.let { id -> allProducts.firstOrNull { it.id == id } }
        if (selectedProduct != null) {
            BackHandler { selectedProductId = null }
            ProductDetailScreen(
                product = selectedProduct,
                storeSettings = storeSettings,
                onBack = { selectedProductId = null },
                onAddToCart = { qty ->
                    storeViewModel.addToCart(selectedProduct, qty)
                    showMessage("Added ${selectedProduct.title} to cart")
                },
                onBuyNow = { qty ->
                    storeViewModel.addToCart(selectedProduct, qty)
                    selectedProductId = null
                    activeTab = MainTab.CART
                }
            )
        } else {
            MainTabShell(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                totalCartCount = totalCartCount,
                snackbarHostState = snackbarHostState,
                shopContent = {
                    ShopScreen(
                        productsState = productsState,
                        storeSettings = storeSettings,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        cartQuantities = cartQuantities,
                        onSearchChange = storeViewModel::updateSearchQuery,
                        onSelectCategory = storeViewModel::selectCategory,
                        onProductClick = { selectedProductId = it.id },
                        onAddToCart = { prod ->
                            storeViewModel.addToCart(prod, 1)
                            showMessage("Added ${prod.title} to cart")
                        }
                    )
                },
                cartContent = {
                    CartCheckoutScreen(
                        cartItems = cartItems,
                        pricing = cartPricing,
                        storeSettings = storeSettings,
                        promoInput = promoInput,
                        promoApplied = promoApplied,
                        currentUser = null,
                        userProfile = null,
                        checkoutBusy = checkoutBusy,
                        onUpdateQuantity = storeViewModel::updateCartQuantity,
                        onPromoInputChange = storeViewModel::updatePromoInput,
                        onApplyPromo = { storeViewModel.applyPromoCode() },
                        onRemovePromo = storeViewModel::removePromoCode,
                        onBrowseShop = { activeTab = MainTab.SHOP },
                        onPlaceOrder = { _, _, _, _, _, _, _, _, _, _ -> },
                        onOrderCompletedNavigate = { activeTab = MainTab.ORDERS },
                        onShowMessage = showMessage
                    )
                },
                ordersContent = {
                    OrdersScreen(
                        currentUser = null,
                        ordersState = UiState.Success(emptyList<Order>()),
                        storeSettings = storeSettings,
                        onCancelOrder = {},
                        onShowMessage = showMessage
                    )
                },
                accountContent = {
                    AccountAndControlScreen(
                        currentUser = null,
                        userProfile = null,
                        isAdmin = false,
                        storeSettings = storeSettings,
                        isBusy = false,
                        onSaveProfile = { _, _, _, _, _, _ -> },
                        onOpenAdminDashboard = {},
                        onShowMessage = showMessage
                    )
                }
            )
        }
    }
}

@Composable
private fun MainTabShell(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    totalCartCount: Int,
    snackbarHostState: SnackbarHostState,
    shopContent: @Composable () -> Unit,
    cartContent: @Composable () -> Unit,
    ordersContent: @Composable () -> Unit,
    accountContent: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                icon = {
                                    TabIcon(tab = tab, selected = selected, cartCount = totalCartCount)
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                icon = {
                                    TabIcon(tab = tab, selected = selected, cartCount = totalCartCount)
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (activeTab) {
                        MainTab.SHOP -> shopContent()
                        MainTab.CART -> cartContent()
                        MainTab.ORDERS -> ordersContent()
                        MainTab.ACCOUNT -> accountContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(
    tab: MainTab,
    selected: Boolean,
    cartCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab == MainTab.CART && cartCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(cartCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}
