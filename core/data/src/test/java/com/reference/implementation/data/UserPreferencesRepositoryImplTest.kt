package com.reference.implementation.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.reference.implementation.data.repositoryimpl.UserPreferencesRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryImplTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepositoryImpl

    @Before
    fun setUp() {
        // Create a real in-memory DataStore instance pointing to a temporary file
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("user_preferences.preferences_pb") }
        )

        repository = UserPreferencesRepositoryImpl(dataStore = dataStore)
    }

    @Test
    fun `getImportantMessageIds emits empty set initially`() =
        runTest(testDispatcher) {
            repository.getImportantMessageIds().test {
                assertEquals(emptySet(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markMessageAsImportant adds message ID to important set`() =
        runTest(testDispatcher) {
            repository.getImportantMessageIds().test {
                // Initial state
                assertEquals(emptySet(), awaitItem())

                // Act
                repository.markMessageAsImportant(101)

                // Assert updated emission
                assertEquals(setOf("101"), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markMessageAsNotImportant removes message ID from set`() =
        runTest(testDispatcher) {
            repository.getImportantMessageIds().test {
                assertEquals(emptySet(), awaitItem())

                // Add then remove
                repository.markMessageAsImportant(101)
                assertEquals(setOf("101"), awaitItem())

                repository.markMessageAsNotImportant(101)
                assertEquals(emptySet(), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    // #########################################################################################
    // #########################################################################################
    // #########################################################################################
    // #########################################################################################

    @Test
    fun `getBookmarkBulletinIds emits empty set initially`() =
        runTest(testDispatcher) {
            repository.getBookmarkBulletinIds().test {
                assertEquals(emptySet(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markBulletinAsBookmark adds bulletin ID to bookmark set`() =
        runTest(testDispatcher) {
            repository.getBookmarkBulletinIds().test {
                // Initial state
                assertEquals(emptySet(), awaitItem())

                // Act
                repository.markBulletinAsBookmark(101)

                // Assert updated emission
                assertEquals(setOf("101"), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markBulletinAsNotBookmark removes message ID from set`() =
        runTest(testDispatcher) {
            repository.getBookmarkBulletinIds().test {
                assertEquals(emptySet(), awaitItem())

                // Add then remove
                repository.markBulletinAsBookmark(101)
                assertEquals(setOf("101"), awaitItem())

                repository.markBulletinAsNotBookmark(101)
                assertEquals(emptySet(), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}