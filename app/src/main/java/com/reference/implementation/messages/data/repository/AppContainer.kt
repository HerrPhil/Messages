package com.reference.implementation.messages.data.repository

import android.content.Context
import com.reference.implementation.messages.BuildConfig
import com.reference.implementation.messages.data.audit.SecurityAuditInterceptor
import com.reference.implementation.messages.data.manager.AccessTokenManager
import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.RefreshTokenManager
import com.reference.implementation.messages.data.manager.RoleManager
import com.reference.implementation.messages.data.manager.SessionManager
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.domain.repository.LoginRepository
import com.reference.implementation.messages.domain.repository.LogoutRepository
import com.reference.implementation.messages.domain.repository.MessageCacheRepository
import com.reference.implementation.messages.domain.repository.MessageRepository
import com.reference.implementation.messages.domain.repository.PermissionRepository
import com.reference.implementation.messages.domain.repository.RefreshTokenRepository
import com.reference.implementation.messages.domain.repository.RoleRepository
import com.reference.implementation.messages.domain.repository.UserRepository
import com.reference.implementation.messages.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.messages.domain.use_case.ForceLogoutUseCase
import com.reference.implementation.messages.domain.use_case.GetActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.GetMessageUiEventsUseCase
import com.reference.implementation.messages.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.messages.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.messages.domain.use_case.LoginUseCase
import com.reference.implementation.messages.domain.use_case.LogoutUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.messages.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.messages.domain.use_case.RefreshTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

interface AppContainer {
    val loginUseCase: LoginUseCase
    val logoutUseCase: LogoutUseCase
    val forceLogoutUseCase: ForceLogoutUseCase
    val getUserDashboardUseCase: GetUserDashboardUseCase
    val refreshTokenUseCase: RefreshTokenUseCase
    val loadActiveMessagesUseCase: LoadActiveMessagesUseCase
    val getActiveMessagesUseCase: GetActiveMessagesUseCase
    val markMessageAsReadUseCase: MarkMessageAsReadUseCase
    val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase
    val deleteMessageUseCase: DeleteMessageUseCase
    val getMessageUiEventsUseCase: GetMessageUiEventsUseCase
    val authSessionManager: AuthSessionManager
    val roleManager: RoleManager
}

/**
 * [AppContainer] implementation that provides instances(s) of my use cases
 * and provides the needed repository value(s) to each use case.
 */

class AppMessageContainer(context: Context) : AppContainer {

    private val accessTokenManager = AccessTokenManager(
        context,
        "access_token_manager_key",
        "encrypted_access_token",
        "access_token_iv")

    private val refreshTokenManager = RefreshTokenManager(
        context,
        "refresh_token_manager_key",
        "encrypted_refresh_token",
        "refresh_token_iv")


    /**
     * This is related to login/logout.
     * It is the Global State Source (Application Layer) whether the user is authenticated.
     */
    override val authSessionManager = AuthSessionManager()

    /**
     * This is related to login.
     * It is the Global State Source (Application Layer) of the authenticated user's role.
     * Think "Regular User" or "Administrator".
     */
    override val roleManager = RoleManager()

    // To be used in conjunction with work that MUST finish.
    // For example, on logout, if viewModelScope dies, there must be an application scope that is
    // parented by the Application lifecycle to handle critical work.
    // The cancellation signal from the ViewModel cannot reach it.
    // Then just pass this to a repository, like we pass tokenManager to a repository.
    // SupervisorJob ensures a failure in one background task won't kill the scope.
    private val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json {
        ignoreUnknownKeys = true // API adds a field? No crash.
        isLenient = true // Accepts malformed JSON if possible
        coerceInputValues = true // Enables coercing incorrect JSON values. See documentation
        encodeDefaults = true // Includes default values in requests
    }

    /**
     * The view models should not "see" the data/remote layer.
     * Encapsulate it here!
     */
    private val apiService: ApiService by lazy {

        // 1. Create the logging interceptor
        val logging = HttpLoggingInterceptor().apply {
            // BODY gives you headers + status + body.
            // Use HEADERS if you only want the metadata.
            // After I am comfortable with the code working, switch to NONE or BASIC for the
            // level, for security reasons - keep the token out of logcat.

            //            level = HttpLoggingInterceptor.Level.BODY

            level =
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            // Extra credit: redact the Header
            redactHeader("Authorization")
        }

        // 2. Create the TokenAuthenticator and
        // Defer assigning the use cases with the provider pattern.
        //
        // This transforms a strict data dependency (I need this object right now)
        // into a behavioural dependency (I need a method to fetch this object later).
        //
        // This gives the compiler a clear boundary to stop execution.
        // It compiles the apiService perfectly,
        // leaves the contents of that lambda un-evaluated in memory,
        // and moves on.
        //
        // By passing a provider lambda, the core networking infrastructure does not
        // tightly couple itself to the concrete lifecycle of the data layer use cases.
        val tokenAuthenticator = TokenAuthenticator(
            refreshTokenUseCaseProvider = { refreshTokenUseCase },
            forceLogoutUseCaseProvider = { forceLogoutUseCase }
        )

        // 2. Create the OkHttpClient and add the interceptor
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(SecurityAuditInterceptor())
            .addInterceptor(AuthInterceptor(accessTokenManager))
            .authenticator(tokenAuthenticator)
            .build()

        val contentType = "application/json".toMediaType()

        Retrofit.Builder()
//            .baseUrl("http://192.168.215.165:4000/")
            .baseUrl("http://10.0.2.2:4000/")
            .client(client) // This is incorporating the logging of the HTTP client
            // This is the magic line for Kotlinx Serialization
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }

    private val sessionManager by lazy {
        SessionManager(accessTokenManager, refreshTokenManager)
    }

    /**
     * The view models should not "see" the data/repository layer.
     * Encapsulate it here!
     */
    private val loginRepository: LoginRepository by lazy {
        // The container provides ("injects") the api service to the re=pository.
        LoginRepositoryImpl(
            apiService,
            accessTokenManager,
            refreshTokenManager,
            authSessionManager,
            roleManager,
            sessionManager
        )
    }

    private val logoutRepository: LogoutRepository by lazy {
        LogoutRepositoryImpl(
            sessionManager,
            authSessionManager,
            roleManager,
            externalScope = applicationScope
        )
    }

    private val userRepository: UserRepository by lazy {
        UserRepositoryImpl(sessionManager)
    }

    private val messageRepository: MessageRepository by lazy {
        MessageRepositoryImpl(apiService, sessionManager)
    }

    private val roleRepository: RoleRepository by lazy {
        RoleRepositoryImpl(sessionManager)
    }

    private val permissionRepository: PermissionRepository by lazy {
        PermissionRepositoryImpl(apiService, sessionManager)
    }

    private val refreshTokenRepository: RefreshTokenRepository by lazy {
        RefreshTokenRepositoryImpl(apiService, accessTokenManager, refreshTokenManager)
    }

    private val messageCacheRepository: MessageCacheRepository by lazy {
        MessageCacheRepositoryImpl(apiService, sessionManager)
    }

    /**
     * On the journey of building up the app, the first point of contact is login.
     * Here is the implementation for the login use case.
     */
    override val loginUseCase: LoginUseCase by lazy {
        // The container provides ("injects") the repository to the use case.
        LoginUseCase(loginRepository)
    }

    override val logoutUseCase: LogoutUseCase by lazy {
        LogoutUseCase(logoutRepository)
    }

    override val forceLogoutUseCase: ForceLogoutUseCase by lazy {
        ForceLogoutUseCase(logoutRepository)
    }

    override val getUserDashboardUseCase: GetUserDashboardUseCase by lazy {
        GetUserDashboardUseCase(
            userRepository,
            messageRepository,
            roleRepository,
            permissionRepository
        )
    }

    override val refreshTokenUseCase: RefreshTokenUseCase by lazy {
        RefreshTokenUseCase(refreshTokenRepository)
    }

    override val loadActiveMessagesUseCase: LoadActiveMessagesUseCase by lazy {
        LoadActiveMessagesUseCase(messageCacheRepository)
    }

    override val getActiveMessagesUseCase: GetActiveMessagesUseCase by lazy {
        GetActiveMessagesUseCase(messageCacheRepository)
    }

    override val markMessageAsReadUseCase: MarkMessageAsReadUseCase by lazy {
        MarkMessageAsReadUseCase(messageCacheRepository)
    }

    override val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase by lazy {
        MarkMessageAsUnreadUseCase(messageCacheRepository)
    }

    override val deleteMessageUseCase: DeleteMessageUseCase by lazy {
        DeleteMessageUseCase(messageCacheRepository)
    }

    override val getMessageUiEventsUseCase: GetMessageUiEventsUseCase by lazy {
        GetMessageUiEventsUseCase(messageCacheRepository)
    }

}