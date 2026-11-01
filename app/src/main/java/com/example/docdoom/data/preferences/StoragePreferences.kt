
package com.example.docdoom.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "docdoom_settings"
)

object StoragePreferences {

    private val STORAGE_CARD_EXPANDED =
        booleanPreferencesKey("storage_card_expanded")

    // The card starts expanded the first time the app is opened.
    fun storageCardExpanded(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[STORAGE_CARD_EXPANDED] ?: true
        }
    }

    // Save the user's latest expanded/collapsed choice.
    suspend fun setStorageCardExpanded(
        context: Context,
        expanded: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_CARD_EXPANDED] = expanded
        }
    }
}
