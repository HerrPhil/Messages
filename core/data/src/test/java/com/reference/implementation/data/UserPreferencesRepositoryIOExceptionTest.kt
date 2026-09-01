package com.reference.implementation.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.reference.implementation.data.repositoryimpl.UserPreferencesRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserPreferencesRepositoryImplIOExceptionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockDataStore: DataStore<Preferences> = mockk()
    private val repository = UserPreferencesRepositoryImpl(dataStore = mockDataStore)

    @Test
    fun `getImportantMessageIds catches IOException and emits empty set fallback`() =
        runTest(testDispatcher) {

            // Arrange: Stub DataStore flow to throw an IOException
            every { mockDataStore.data } returns flow {
                throw IOException("Disk corrupted or permission denied")
            }

            // Act & Assert using Turbine
            repository.getImportantMessageIds().test {
                // The catch block intercepts IOException and emits emptyPreferences(),
                // mapping to emptySet()
                assertEquals(emptySet(), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `getBookmarkBulletinIds catches IOException and emits empty set fallback`() =
        runTest(testDispatcher) {

            // Arrange: Stub DataStore flow to throw an IOException
            every { mockDataStore.data } returns flow {
                throw IOException("Disk read failure")
            }

            // Act & Assert using Turbine
            repository.getBookmarkBulletinIds().test {
                assertEquals(emptySet(), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `getImportantMessageIds rethrows non-IOException exceptions`() =
        runTest(testDispatcher) {

            // Arrange: Stub DataStore flow to throw a non-IO exception (e.g. IllegalStateException)
            every { mockDataStore.data } returns flow {
                throw IllegalStateException("Fatal state exception")
            }

            // Act & Assert using Turbine's awaitError()
            repository.getImportantMessageIds().test {
                val exception = awaitError()
                assertTrue(exception is IllegalStateException)
                assertEquals("Fatal state exception", exception.message)
            }
        }

    @Test
    fun `getBookmarkBulletinIds rethrows non-IOException exceptions`() =
        runTest(testDispatcher) {

            // Arrange: Stub DataStore flow to throw a non-IO exception (e.g. IllegalStateException)
            every { mockDataStore.data } returns flow {
                throw IllegalStateException("Fatal state exception")
            }

            // Act & Assert using Turbine's awaitError()
            repository.getImportantMessageIds().test {
                val exception = awaitError()
                assertTrue(exception is IllegalStateException)
                assertEquals("Fatal state exception", exception.message)
            }
        }
}