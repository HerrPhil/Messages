package com.reference.implementation.messages.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.reference.implementation.messages.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private object PreferenceKeys {
        val IMPORTANT_MESSAGE_IDS = stringSetPreferencesKey("important_message_ids")
    }

    override suspend fun markMessageAsImportant(messageId: Int) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PreferenceKeys.IMPORTANT_MESSAGE_IDS] ?: emptySet()
            preferences[PreferenceKeys.IMPORTANT_MESSAGE_IDS] = currentSet + messageId.toString()
        }
    }

    override suspend fun markMessageAsNotImportant(messageId: Int) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PreferenceKeys.IMPORTANT_MESSAGE_IDS] ?: emptySet()
            preferences[PreferenceKeys.IMPORTANT_MESSAGE_IDS] = currentSet - messageId.toString()
        }
    }

    override fun getImportantMessageIds(): Flow<Set<String>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferenceKeys.IMPORTANT_MESSAGE_IDS] ?: emptySet()
            }
    }
}