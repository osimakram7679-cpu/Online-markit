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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.PaymentStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.StoreSettings
import com.example.model.UiState
import com.example.ui.components.ProductArtwork
import com.example.ui.components.StatusPill
import com.example.ui.components.formatInr
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    productsState: UiState<List<Product>>,
    adminOrdersState: UiState<List<Order>>,
    storeSettings: StoreSettings,
    isBusy: Boolean,
    onBack: () -> Unit,
    onUpsertProduct: (
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
    ) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onSeedStarterProducts: () -> Unit,
    onUpdateOrder: (orderId: String, orderStatus: OrderStatus, paymentStatus: PaymentStatus, txnId: String) -> Unit,
    onSaveStoreSettings: (StoreSettings) -> Unit,
    onAddAdmin: (uid: String, email: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Products", "Orders", "Control")

    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showProductSheet by remember { mutableStateOf(false) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }

    val products = when (productsState) {
        is UiState.Success -> productsState.data
        else -> emptyList()
    }
    val orders = when (adminOrdersState) {
        is UiState.Success -> adminOrdersState.data
        else -> emptyList()
    }

    if (showProductSheet) {
        ProductEditorSheet(
            initial = editingProduct,
            isBusy = isBusy,
            onDismiss = { showProductSheet = false },
            onSave = { id, title, desc, price, mrp, cat, preset, url, stock, featured, active ->
                onUpsertProduct(id, title, desc, price, mrp, cat, preset, url, stock, featured, active) {
                    showProductSheet = false
                }
            }
        )
    }

    if (deletingProduct != null) {
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Delete Product?") },
            text = { Text("Remove \"${deletingProduct!!.title}\" from Firestore catalog?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(deletingProduct!!.id)
                        deletingProduct = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = {
                        editingProduct = null
                        showProductSheet = true
                    },
                    modifier = Modifier.testTag("admin_add_product_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Product")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("admin_tab_${title.lowercase()}")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> AdminOverviewTab(
                        products = products,
                        orders = orders,
                        storeSettings = storeSettings,
                        isBusy = isBusy,
                        onSeedStarterProducts = onSeedStarterProducts,
                        onGoToProducts = { selectedTab = 1 },
                        onGoToOrders = { selectedTab = 2 },
                        onGoToControl = { selectedTab = 3 }
                    )

                    1 -> AdminProductsTab(
                        products = products,
                        isBusy = isBusy,
                        onEdit = {
                            editingProduct = it
                            showProductSheet = true
                        },
                        onDelete = { deletingProduct = it },
                        onSeedStarterProducts = onSeedStarterProducts
                    )

                    2 -> AdminOrdersTab(
                        ordersState = adminOrdersState,
                        isBusy = isBusy,
                        onUpdateOrder = onUpdateOrder
                    )

                    3 -> AdminControlPanelTab(
                        storeSettings = storeSettings,
                        isBusy = isBusy,
                        onSaveSettings = onSaveStoreSettings,
                        onAddAdmin = onAddAdmin
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(
    products: List<Product>,
    orders: List<Order>,
    storeSettings: StoreSettings,
    isBusy: Boolean,
    onSeedStarterProducts: () -> Unit,
    onGoToProducts: () -> Unit,
    onGoToOrders: () -> Unit,
    onGoToControl: () -> Unit
) {
    val totalRevenue = orders.filter { it.orderStatus != OrderStatus.CANCELLED.wireValue }.sumOf { it.totalInr }
    val pendingOrders = orders.count {
        it.orderStatus in listOf(OrderStatus.PLACED.wireValue, OrderStatus.CONFIRMED.wireValue, OrderStatus.PACKED.wireValue)
    }
    val lowStock = products.count { it.stock <= 5L }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Total Revenue",
                    value = formatInr(totalRevenue),
                    icon = Icons.Filled.Payments,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Total Orders",
                    value = orders.size.toString(),
                    icon = Icons.Filled.ShoppingBag,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Pending Dispatch",
                    value = pendingOrders.toString(),
                    icon = Icons.Filled.LocalShipping,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Products ($lowStock Low)",
                    value = products.size.toString(),
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f)
                )
            }
        }

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
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Store Status: ${if (storeSettings.storeOpen) "OPEN" else "PAUSED"} • Support: ${storeSettings.contactPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onGoToProducts, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Manage Products")
                        }
                        OutlinedButton(onClick = onGoToOrders, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Manage Orders")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGoToControl, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Control Panel")
                        }
                        OutlinedButton(
                            onClick = onSeedStarterProducts,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Catalog")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminProductsTab(
    products: List<Product>,
    isBusy: Boolean,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit,
    onSeedStarterProducts: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedButton(
                onClick = onSeedStarterProducts,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("seed_starter_catalog_button")
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Publish / Reset 6 Starter Products to Cloud")
            }
        }

        items(products, key = { it.id }) { product ->
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
                        product = product,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatInr(product.priceInr)} • Stock: ${product.stock} • ${product.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusPill(
                            text = if (product.active) "ACTIVE" else "HIDDEN",
                            containerColor = if (product.active) EmeraldSoft else CrimsonSoft,
                            contentColor = if (product.active) EmeraldSuccess else CrimsonError
                        )
                    }
                    IconButton(onClick = { onEdit(product) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit product")
                    }
                    IconButton(onClick = { onDelete(product) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete product", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOrdersTab(
    ordersState: UiState<List<Order>>,
    isBusy: Boolean,
    onUpdateOrder: (orderId: String, orderStatus: OrderStatus, paymentStatus: PaymentStatus, txnId: String) -> Unit
) {
    var statusFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("ALL", "PLACED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { f ->
                    FilterChip(
                        selected = statusFilter == f,
                        onClick = { statusFilter = f },
                        label = { Text(f) }
                    )
                }
            }
        }

        when (ordersState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(ordersState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                val filtered = if (statusFilter == "ALL") ordersState.data else ordersState.data.filter { it.orderStatus == statusFilter }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No orders matching \"$statusFilter\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { order ->
                        AdminOrderCard(
                            order = order,
                            isBusy = isBusy,
                            onUpdateOrder = onUpdateOrder
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOrderCard(
    order: Order,
    isBusy: Boolean,
    onUpdateOrder: (orderId: String, orderStatus: OrderStatus, paymentStatus: PaymentStatus, txnId: String) -> Unit
) {
    val currentStatus = OrderStatus.fromRaw(order.orderStatus)
    val currentPayment = PaymentStatus.fromRaw(order.paymentStatus)
    val nextStatus = when (currentStatus) {
        OrderStatus.PLACED -> OrderStatus.CONFIRMED
        OrderStatus.CONFIRMED -> OrderStatus.PACKED
        OrderStatus.PACKED -> OrderStatus.SHIPPED
        OrderStatus.SHIPPED -> OrderStatus.DELIVERED
        else -> null
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${order.id.take(8).uppercase()} • ${order.customerName}", fontWeight = FontWeight.Bold)
                StatusPill(
                    text = currentStatus.label,
                    containerColor = when (currentStatus) {
                        OrderStatus.DELIVERED -> EmeraldSoft
                        OrderStatus.CANCELLED -> CrimsonSoft
                        else -> AmberSoft
                    },
                    contentColor = when (currentStatus) {
                        OrderStatus.DELIVERED -> EmeraldSuccess
                        OrderStatus.CANCELLED -> CrimsonError
                        else -> AmberWarning
                    }
                )
            }
            Text("${order.customerPhone} • ${order.customerEmail}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${order.shippingAddress}, ${order.city} - ${order.pincode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${order.paymentMethod} (${currentPayment.wireValue})", style = MaterialTheme.typography.labelMedium)
                Text(formatInr(order.totalInr), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (nextStatus != null) {
                    Button(
                        onClick = { onUpdateOrder(order.id, nextStatus, currentPayment, order.transactionId) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Mark ${nextStatus.label}")
                    }
                }
                if (currentPayment != PaymentStatus.PAID && currentStatus != OrderStatus.CANCELLED) {
                    OutlinedButton(
                        onClick = { onUpdateOrder(order.id, currentStatus, PaymentStatus.PAID, order.transactionId) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Mark Paid")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorSheet(
    initial: Product?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
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
    ) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var price by remember { mutableStateOf(initial?.priceInr?.toString() ?: "") }
    var mrp by remember { mutableStateOf(initial?.mrpInr?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "10") }
    var category by remember { mutableStateOf(initial?.category ?: ProductCategory.ELECTRONICS.label) }
    var preset by remember { mutableStateOf(initial?.imagePreset ?: "electronics") }
    var imageUrl by remember { mutableStateOf(initial?.imageUrl.orEmpty()) }
    var featured by remember { mutableStateOf(initial?.featured ?: false) }
    var active by remember { mutableStateOf(initial?.active ?: true) }

    val presets = listOf("electronics", "fashion", "home", "banner")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (initial == null) "Add New Product" else "Edit Product",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Product Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("product_title_input")
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("Sale Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("product_price_input")
                )
                OutlinedTextField(
                    value = mrp,
                    onValueChange = { mrp = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("MRP (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Text("Category", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProductCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat.label,
                        onClick = { category = cat.label },
                        label = { Text(cat.label) }
                    )
                }
            }

            Text("Image Artwork Preset", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { p ->
                    FilterChip(
                        selected = preset == p,
                        onClick = { preset = p },
                        label = { Text(p.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Optional Image URL (https://...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Featured on Storefront")
                Switch(checked = featured, onCheckedChange = { featured = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active / Visible to Customers")
                Switch(checked = active, onCheckedChange = { active = it })
            }

            Button(
                onClick = {
                    val p = price.toLongOrNull() ?: 0L
                    val m = (mrp.toLongOrNull() ?: p).coerceAtLeast(p)
                    val s = stock.toLongOrNull() ?: 0L
                    onSave(initial?.id, title, description, p, m, category, preset, imageUrl.ifBlank { null }, s, featured, active)
                },
                enabled = !isBusy && title.trim().length >= 2 && (price.toLongOrNull() ?: 0L) >= 1L,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_product_button")
            ) {
                Text(if (initial == null) "Create Product" else "Save Changes")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
