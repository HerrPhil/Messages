package com.reference.implementation.data.repositoryimpl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.reference.implementation.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.minus
import kotlin.collections.plus

/**
 * DataStore is not a good fit for structured local cache tables, but it is the
 * perfect companion to a Room-enabled setup. To build a clean offline-first application,
 * you do not replace DataStore with Room, nor do you put Room inside DataStore.
 * Instead, you let them work side by side as specialized tools, with Room handling your complex,
 * multi-row business data (like bulletins, messages, and user lists)
 * and DataStore handling lightweight, key-value user configurations
 * (like app theme settings, auth tokens, or feature flags).
 */
// Hilt now matches the dataStore straight to the DataStoreModule provider!!!!
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences> // Hilt provides it!
) : UserPreferencesRepository {

    private object PreferenceKeys {
        val IMPORTANT_MESSAGE_IDS = stringSetPreferencesKey("important_message_ids")
        val BOOKMARK_BULLETIN_IDS = stringSetPreferencesKey("bookmark_bulletin_ids")
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

    override suspend fun markBulletinAsBookmark(bulletinId: Int) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PreferenceKeys.BOOKMARK_BULLETIN_IDS] ?: emptySet()
            preferences[PreferenceKeys.BOOKMARK_BULLETIN_IDS] = currentSet + bulletinId.toString()
        }
    }

    override suspend fun markBulletinAsNotBookmark(bulletinId: Int) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PreferenceKeys.BOOKMARK_BULLETIN_IDS] ?: emptySet()
            preferences[PreferenceKeys.BOOKMARK_BULLETIN_IDS] = currentSet - bulletinId.toString()
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

    override fun getBookmarkBulletinIds(): Flow<Set<String>> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferenceKeys.BOOKMARK_BULLETIN_IDS] ?: emptySet()
            }
    }
}