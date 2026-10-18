package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.di.ApplicationScope
import com.reference.implementation.data.di.AuthNetwork
import com.reference.implementation.data.di.IoDispatcher
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.mappers.toDomainModel
import com.reference.implementation.data.model.SyncState
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.database.dao.UserDao
import com.reference.implementation.domain.model.CachePayload
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.repository.UserRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    @AuthNetwork private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher, // Hilt provides it
    private val sessionManager: SessionManager,
    private val userDao: UserDao,
    @ApplicationScope private val externalScope: CoroutineScope
) : UserRepository {

    // THE STATUS SCRATCHPAD: Tracks only the active network sync state internally.
    // Starts as completely idle (No errors, not syncing).
    private val syncStatus = MutableStateFlow<SyncState>(SyncState.Idle)

    private val _allUsersCache =
        MutableStateFlow<NetworkResult<List<LoginUserDomainModel>>>(NetworkResult.Loading)

    /**
     * Emits:
     * - user info stored in the session manager at login
     */
    override fun getUserInfoFlow(): Flow<NetworkResult<LoginUserDomainModel>> =
        flow {
            // Emit Loading state initially
            emit(NetworkResult.Loading)

            // Step 1: Fetch the user info results from session manager
            val userName = when (val userNameSessionResult = sessionManager.getSessionUserName()) {
                is SessionResult.Authenticated -> userNameSessionResult.data
                else -> "no name"
            }
            val userEmail =
                when (val userEmailSessionResult = sessionManager.getSessionUserEmail()) {
                    is SessionResult.Authenticated -> userEmailSessionResult.data
                    else -> "no email"
                }

            // Admin Messages Centre 'selected user' feature.
            // Before: did not need to know the current session userID (logged in)
            // Now: need to know the current session userID
            // to mark up the all-user-list where one user is admin user,
            // and others are regular users.
            val userId =
                when (val userIdSessionResult = sessionManager.getSessionUserId()) {
                    is SessionResult.Authenticated -> userIdSessionResult.data
                    else -> -1
                }

            // Step 2: Contain the user info in a Domain Model
            val userDomainModel = LoginUserDomainModel(
                id = userId,
                name = userName,
                email = userEmail,
                age = 0 // don't care
            )

            // Step 3: Emit the Domain Model
            emit(NetworkResult.Success(userDomainModel))
        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no user info")
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get user info ended")
            }
        }.flowOn(ioDispatcher)

    // THE DEPTH-FIRST ENGINE: Connects use cases natively to the database pipe.
    // Your public stream now yields a unified payload containing BOTH local data and sync health!
    // Note: CachePayload is the evolution from NetworkResult.
    // Note: There is NO .onCompletion {} - the Room stream is infinite.
    // Note; There is NO .catch {} - the network health check is information in CachePayload
    override fun getUserCount(onRetry: (Int) -> Unit): Flow<CachePayload<Int>> =
        flow {

            // THE CONFLUENCE ENGINE:
            // We combine Room's infinite stream with our private background sync status flow!
            userDao.getUsersStream()
                .combine(syncStatus) { entities, status ->
                    CachePayload.ActiveStream(
                        data = entities.size,
                        isSyncing = status is SyncState.Loading,
                        syncError = (status as? SyncState.Error)?.exception // propagates 4xx/5xx errors
                    )
                }.collect { unifiedPayload ->
                    // Emit the combined model down your existing public pipeline channels!
                    emit(unifiedPayload)
                }
        }.onStart {
            // Emitters documentation says:
            // This action is called before the upstream flow is started.
            // Trigger background sync exactly once on collection entry
            externalScope.launch {
                syncUsers(onRetry)
            }
        }.flowOn(ioDispatcher)

    override fun getUsers(): Flow<NetworkResult<List<LoginUserDomainModel>>> =
        _allUsersCache.asStateFlow()

    // The following has no return value - stores result in cached Flow
    override suspend fun loadAllUsers(onRetry: (Int) -> Unit) {

        // Force the cache to show "Loading" if it is a manual retry/refresh action
        _allUsersCache.value = NetworkResult.Loading

        // force the execution onto the IO thread pool
        withContext(ioDispatcher) {
            try {
                val response = retryIO(times = 3, onRetry = onRetry) {
                    val res = apiService.getUsers()
                    if (res.code() >= 500) {
                        throw HttpException(res) // Force retryIO's catch block to trigger!
                    }
                    res
                }
                val body = response.body()
                if (response.isSuccessful && body != null) { // 200 response
                    _allUsersCache.value =
                        NetworkResult.Success(data = body.map { it.toDomainModel() })
                } else { // 4xx errors
                    // Transform unsuccessful Retrofit calls.
                    // Update the SSOT cache with the network result error!
                    _allUsersCache.value =
                        NetworkResult.Error(response.code(), response.message())
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                auditLog(e.message ?: "no message")
                // Update the SSOT cache with the network result exception!
                _allUsersCache.value = NetworkResult.Exception(e) // 5xx errors
            } finally {
                withContext(NonCancellable) {
                    auditLog("${auditLogTimestamp()} refresh messages by user ended")
                }
            }
        }
    }

    private suspend fun syncUsers(onRetry: (Int) -> Unit) {
        try {

            // Payload refactor away from NetworkResult

            // 1. Mark status scratchpad as Loading
            syncStatus.value = SyncState.Loading

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getUsers()
                if (res.code() >= 500) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()
            if (response.isSuccessful && body != null) { // 200 response

                // Map incoming cloud DTOs straight to the physical BulletinEntity rows.
                val localEntities = body.map { dto -> dto.toEntity() }

                // Room Integration: Execute the atomic update-or-insert transaction loop
                // Dump directly into Room! SQLite handles the OnConflictStrategy.REPLACE upsert
                // natively, safely refreshing values while updating your open streams instantly.
                userDao.insertUsers(localEntities)

                // Payload refactor away from NetworkResult
                // 2. Clear out any error memory states on structural network success
                syncStatus.value = SyncState.Idle
            } else { // 4xx errors
                auditLog("Sync failed with client error: ${response.code()}")
                // Payload refactor away from NetworkResult
                // 3. Capture 4xx Client Errors safely inside the state flow
                syncStatus.value = SyncState.Error(exception = HttpException(response))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            auditLog("Background network sync silently deferred: ${e.message}")
            // Payload refactor away from NetworkResult
            // 4. Capture 5xx Network Errors or IO timeouts without breaking the repository!
            syncStatus.value = SyncState.Error(exception = e)
        }
    }

}