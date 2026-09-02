package com.reference.implementation.data.repositoryimpl

import com.reference.implementation.data.audit.auditLog
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.manager.SessionResult
import com.reference.implementation.data.mappers.toDomainModel
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.model.LoginUserDomainModel
import com.reference.implementation.domain.repository.UserRepository
import com.reference.implementation.domain.util.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class UserRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) : UserRepository {

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
                name = userName,
                email = userEmail,
                id = userId
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

    override fun getUserCount(onRetry: suspend (Int) -> Unit): Flow<NetworkResult<Int>> =
        flow {

            emit(NetworkResult.Loading)

            val response = retryIO(times = 3, onRetry = onRetry) {
                val res = apiService.getUsers()
                if (res.code() >= 500 ) {
                    throw HttpException(res) // Force retryIO's catch block to trigger!
                }
                res
            }

            val body = response.body()
            if (response.isSuccessful && body != null) { // 200 response
                emit(NetworkResult.Success(body.size)) // number of bulletins
            } else { // 4xx errors
                emit(
                    NetworkResult.Error(
                        response.code(),
                        response.message()
                    )
                )
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            auditLog(e.message ?: "no messages") // 5xx errors
            emit(NetworkResult.Exception(e))
        }.onCompletion {
            withContext(NonCancellable) {
                auditLog("${auditLogTimestamp()} get messages ended")
            }
        }.flowOn(ioDispatcher)

    override fun getUsers(): Flow<NetworkResult<List<LoginUserDomainModel>>> =
        _allUsersCache.asStateFlow()

    // The following has no return value - stores result in cached Flow
    override suspend fun loadAllUsers(onRetry: suspend (Int) -> Unit) {

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

}