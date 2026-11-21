package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cartDataStore by preferencesDataStore(name = "bazaarhub_cart")

class CartDataStore(private val context: Context) {

    private val cartEntriesKey = stringPreferencesKey("cart_quantities")

    val cartQuantitiesFlow: Flow<Map<String, Int>> = context.cartDataStore.data.map { prefs ->
        val raw = prefs[cartEntriesKey].orEmpty()
        if (raw.isBlank()) {
            emptyMap()
        } else {
            raw.split(";")
                .mapNotNull { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2) {
                        val qty = parts[1].toIntOrNull()
                        if (parts[0].isNotBlank() && qty != null && qty > 0) parts[0] to qty else null
                    } else null
                }
                .toMap()
        }
    }

    suspend fun saveQuantities(quantities: Map<String, Int>) {
        val serialized = quantities
            .filter { (id, qty) -> id.isNotBlank() && qty > 0 }
            .entries
            .joinToString(";") { "${it.key}=${it.value}" }
        context.cartDataStore.edit { prefs ->
            prefs[cartEntriesKey] = serialized
        }
    }

    suspend fun clear() {
        context.cartDataStore.edit { prefs ->
            prefs.remove(cartEntriesKey)
        }
    }
}
