package com.reference.implementation.messages.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.AuthSessionManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.manager.RoleManager
import com.reference.implementation.data.manager.SessionManager
import com.reference.implementation.data.repositoryimpl.BulletinCacheRepositoryImpl
import com.reference.implementation.data.repositoryimpl.BulletinRepositoryImpl
import com.reference.implementation.data.repositoryimpl.LoginRepositoryImpl
import com.reference.implementation.data.repositoryimpl.LogoutRepositoryImpl
import com.reference.implementation.data.repositoryimpl.MessageCacheRepositoryImpl
import com.reference.implementation.data.repositoryimpl.MessageRepositoryImpl
import com.reference.implementation.data.repositoryimpl.PermissionRepositoryImpl
import com.reference.implementation.data.repositoryimpl.RefreshTokenRepositoryImpl
import com.reference.implementation.data.repositoryimpl.RoleRepositoryImpl
import com.reference.implementation.data.repositoryimpl.UserPreferencesRepositoryImpl
import com.reference.implementation.data.repositoryimpl.UserRepositoryImpl
import com.reference.implementation.data.sources.ApiService
import com.reference.implementation.domain.repository.BulletinCacheRepository
import com.reference.implementation.domain.repository.BulletinRepository
import com.reference.implementation.domain.repository.LoginRepository
import com.reference.implementation.domain.repository.LogoutRepository
import com.reference.implementation.domain.repository.MessageCacheRepository
import com.reference.implementation.domain.repository.MessageRepository
import com.reference.implementation.domain.repository.PermissionRepository
import com.reference.implementation.domain.repository.RefreshTokenRepository
import com.reference.implementation.domain.repository.RoleRepository
import com.reference.implementation.domain.repository.UserPreferencesRepository
import com.reference.implementation.domain.repository.UserRepository
import com.reference.implementation.domain.use_case.DeleteMessageUseCase
import com.reference.implementation.domain.use_case.FetchNewUserProfileUseCase
import com.reference.implementation.domain.use_case.ForceLogoutUseCase
import com.reference.implementation.domain.use_case.GetAdminDashboardUseCase
import com.reference.implementation.domain.use_case.GetAdminUserInformationUseCase
import com.reference.implementation.domain.use_case.GetAllBulletinsUseCase
import com.reference.implementation.domain.use_case.GetBulletinUseCase
import com.reference.implementation.domain.use_case.GetCachedMessagesUseCase
import com.reference.implementation.domain.use_case.GetMessageEventsUseCase
import com.reference.implementation.domain.use_case.GetUserDashboardUseCase
import com.reference.implementation.domain.use_case.LoadActiveMessagesUseCase
import com.reference.implementation.domain.use_case.LoadAllBulletinsUseCase
import com.reference.implementation.domain.use_case.LoadAllUsersUseCase
import com.reference.implementation.domain.use_case.LoadBulletinUseCase
import com.reference.implementation.domain.use_case.LoadSelectedMessagesUseCase
import com.reference.implementation.domain.use_case.LoginUseCase
import com.reference.implementation.domain.use_case.LogoutUseCase
import com.reference.implementation.domain.use_case.MarkBulletinAsBookmarkUseCase
import com.reference.implementation.domain.use_case.MarkBulletinAsNotBookmarkUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsNotImportantUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsReadUseCase
import com.reference.implementation.domain.use_case.MarkMessageAsUnreadUseCase
import com.reference.implementation.domain.use_case.RefreshTokenUseCase
import com.reference.implementation.domain.use_case.RestoreMessageUseCase
import com.reference.implementation.messages.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.getValue

private const val USER_PREFERENCES_NAME = "user_preferences"

// Top-level extension delegate ensures a single DataStore instance per application context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_NAME
)

interface AppContainer {
    val loginUseCase: LoginUseCase
    val fetchNewUserProfileUseCase: FetchNewUserProfileUseCase
    val logoutUseCase: LogoutUseCase
    val forceLogoutUseCase: ForceLogoutUseCase
    val getUserDashboardUseCase: GetUserDashboardUseCase
    val refreshTokenUseCase: RefreshTokenUseCase
    val loadActiveMessagesUseCase: LoadActiveMessagesUseCase
    val loadSelectedMessagesUseCase: LoadSelectedMessagesUseCase
    val getCachedMessagesUseCase: GetCachedMessagesUseCase
    val markMessageAsReadUseCase: MarkMessageAsReadUseCase
    val markMessageAsUnreadUseCase: MarkMessageAsUnreadUseCase
    val deleteMessageUseCase: DeleteMessageUseCase
    val restoreMessageUseCase: RestoreMessageUseCase
    val getMessageEventsUseCase: GetMessageEventsUseCase
    val loadAllBulletinsUseCase: LoadAllBulletinsUseCase
    val getAllBulletinsUseCase: GetAllBulletinsUseCase
    val loadBulletinUseCase: LoadBulletinUseCase
    val getBulletinUseCase: GetBulletinUseCase
    val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase
    val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase
    val markBulletinAsBookmarkUseCase: MarkBulletinAsBookmarkUseCase
    val markBulletinAsNotBookmarkUseCase: MarkBulletinAsNotBookmarkUseCase
    val getAdminDashboardUseCase: GetAdminDashboardUseCase
    val loadAllUsersUseCase: LoadAllUsersUseCase
    val getAdminUserInformationUseCase: GetAdminUserInformationUseCase
    val authSessionManager: AuthSessionManager
    val roleManager: RoleManager
}

/**
 * [AppContainer] implementation that provides instances(s) of my use cases
 * and provides the needed repository value(s) to each use case.
 */

class AppMessageContainer(context: Context) : AppContainer {

    // 1. Core singletons (created once)

    // To be used in conjunction with work that MUST finish.
    // For example, on logout, if viewModelScope dies, there must be an application scope that is
    // parented by the Application lifecycle to handle critical work.
    // The cancellation signal from the ViewModel cannot reach it.
    // Then just pass this to a repository, like we pass tokenManager to a repository.
    // SupervisorJob ensures a failure in one background task won't kill the scope.
    private val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * This is related to log in & logout.
     * It is the Global State Source (Application Layer) whether the user is authenticated.
     */
    override val authSessionManager = AuthSessionManager()

    /**
     * This is related to log in event.
     * It is the Global State Source (Application Layer) of the authenticated user's role.
     * Think "Regular User" or "Administrator".
     */
    override val roleManager = RoleManager()

    private val accessTokenManager by lazy {
        AccessTokenManager(
            context,
            "access_token_manager_key",
            "encrypted_access_token",
            "access_token_iv"
        )
    }

    private val refreshTokenManager by lazy {
        RefreshTokenManager(
            context,
            "refresh_token_manager_key",
            "encrypted_refresh_token",
            "refresh_token_iv"
        )
    }

    private val sessionManager by lazy {
        SessionManager(accessTokenManager, refreshTokenManager)
    }

    // 1. Create the logging interceptor
    private val logging = HttpLoggingInterceptor().apply {
        // BODY gives you headers + status + body.
        // Use HEADERS if you only want the metadata.
        // After I am comfortable with the code working, switch to NONE or BASIC for the
        // level, for security reasons - keep the token out of logcat.

        level =
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        // Extra credit: redact the Header
        redactHeader("Authorization") // probably breaks tests - will experiment
    }

    private val baseOkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
//            .connectTimeout(15, TimeUnit.SECONDS)
//            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(SecurityAuditInterceptor())
            .addInterceptor(logging)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true // API adds a field? No crash.
        isLenient = true // Accepts malformed JSON if possible
        coerceInputValues = true // Enables coercing incorrect JSON values. See documentation
        encodeDefaults = true // Includes default values in requests
    }

    private val jsonConverterFactory by lazy {
        val contentType = "application/json".toMediaType()
        json.asConverterFactory(contentType)
    }

    // 2. Unauthenticated ApiService (BARE - NO Authenticator/AuthInterceptor)

    val baseUrl = "http://10.0.2.2:4000/"

    /**
     * The "bare" OKHttp Client, meaning no auth interceptor, and no token authenticator.
     * For use with unauthenticated endpoints whose primary job is session lifecycle management.
     */
    val authApiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(baseOkHttpClient)
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(ApiService::class.java)
    }

    // 3. (a) Refresh Token Repository & Use Cases

    private val refreshTokenRepository: RefreshTokenRepository by lazy {
        RefreshTokenRepositoryImpl(
            apiService = authApiService, // Uses bare client!!!! (VERY IMPORTANT)
            accessTokenManager = accessTokenManager,
            refreshTokenManager = refreshTokenManager
        )
    }

    override val refreshTokenUseCase: RefreshTokenUseCase by lazy {
        RefreshTokenUseCase(refreshTokenRepository)
    }

    override val forceLogoutUseCase: ForceLogoutUseCase by lazy {
        ForceLogoutUseCase(logoutRepository)
    }

    // 3. (b) Login Repository

    /**
     * The view models should not "see" the data/repository layer.
     * Encapsulate it here!
     */
    private val loginRepository: LoginRepository by lazy {
        // The container provides ("injects") the api service to the re=pository.
        LoginRepositoryImpl(
            apiService = authApiService, // Uses bare client!!!! (VERY IMPORTANT)
            accessTokenManager = accessTokenManager,
            refreshTokenManager = refreshTokenManager,
            authSessionManager = authSessionManager
        )
    }

    /**
     * The "authenticated" OKHttp Client, meaning has a auth interceptor, and has a token authenticator.
     * For use with business endpoints that require active session credentials.
     */
    // 4. Authenticated OkHttpClient
    private val authenticatedOkHttpClient by lazy {
        val tokenAuthenticator = TokenAuthenticator(
            externalScope = applicationScope,
            refreshTokenUseCaseProvider = { refreshTokenUseCase },
            forceLogoutUseCaseProvider = { forceLogoutUseCase }
        )

        // runs in thread pool of OKHttpClient (non-coroutine context)
        baseOkHttpClient.newBuilder()
            .addInterceptor(AuthInterceptor(accessTokenManager)) // add bearer token
            .authenticator(tokenAuthenticator) // handle 401 errors of expired tokens
            .build()
    }

    // 5. Authenticated ApiService (Main app endpoints)
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authenticatedOkHttpClient)
            .addConverterFactory(jsonConverterFactory)
            .build()
            .create(ApiService::class.java)
    }

    /**
     * The view models should not "see" the data/remote layer.
     * Encapsulate it here!
     */
//    private val apiService: ApiService by lazy {
//
//        // 1. Create the logging interceptor
//        val logging = HttpLoggingInterceptor().apply {
//            // BODY gives you headers + status + body.
//            // Use HEADERS if you only want the metadata.
//            // After I am comfortable with the code working, switch to NONE or BASIC for the
//            // level, for security reasons - keep the token out of logcat.
//
//            //            level = HttpLoggingInterceptor.Level.BODY
//
//            level =
//                if (BuildConfig.DEBUG) {
//                    HttpLoggingInterceptor.Level.BODY
//                } else {
//                    HttpLoggingInterceptor.Level.NONE
//                }
//            // Extra credit: redact the Header
//            redactHeader("Authorization")
//        }
//
//        // 2. Create the TokenAuthenticator and
//        // Defer assigning the use cases with the provider pattern.
//        //
//        // This transforms a strict data dependency (I need this object right now)
//        // into a behavioural dependency (I need a method to fetch this object later).
//        //
//        // This gives the compiler a clear boundary to stop execution.
//        // It compiles the apiService perfectly,
//        // leaves the contents of that lambda un-evaluated in memory,
//        // and moves on.
//        //
//        // By passing a provider lambda, the core networking infrastructure does not
//        // tightly couple itself to the concrete lifecycle of the data layer use cases.
//        val tokenAuthenticator = TokenAuthenticator(
//            externalScope = applicationScope,
//            refreshTokenUseCaseProvider = { refreshTokenUseCase },
//            forceLogoutUseCaseProvider = { forceLogoutUseCase }
//        )
//
//        // 2. Create the OkHttpClient and add the interceptor
//        val client = OkHttpClient.Builder()
//            .addInterceptor(logging)
//            .addInterceptor(SecurityAuditInterceptor())
//            .addInterceptor(AuthInterceptor(accessTokenManager))
//            .authenticator(tokenAuthenticator)
//            .build()
//
//        val contentType = "application/json".toMediaType()
//
//        Retrofit.Builder()
//            // following is computer IP address used by Wi-Fi connected device
////            .baseUrl("http://10.0.0.204:4000/")
////            .baseUrl("http://192.168.215.110:4000/")
//            // following is android studio IP address used by emulator device
//            .baseUrl("http://10.0.2.2:4000/")
//            .client(client) // This is incorporating the logging of the HTTP client
//            // This is the magic line for Kotlinx Serialization
//            .addConverterFactory(json.asConverterFactory(contentType))
//            .build()
//            .create(ApiService::class.java)
//    }

    private val logoutRepository: LogoutRepository by lazy {
        LogoutRepositoryImpl(
            externalScope = applicationScope,
            sessionManager = sessionManager,
            authSessionManager = authSessionManager,
            roleManager = roleManager
        )
    }

    private val userRepository: UserRepository by lazy {
        UserRepositoryImpl(
            apiService = apiService,
            sessionManager = sessionManager
        )
    }

    private val messageRepository: MessageRepository by lazy {
        MessageRepositoryImpl(
            apiService = apiService,
            sessionManager = sessionManager
        )
    }

    private val roleRepository: RoleRepository by lazy {
        RoleRepositoryImpl(
            apiService = apiService,
            roleManager = roleManager,
            sessionManager = sessionManager
        )
    }

    private val permissionRepository: PermissionRepository by lazy {
        PermissionRepositoryImpl(
            apiService = apiService,
            sessionManager = sessionManager
        )
    }

    private val messageCacheRepository: MessageCacheRepository by lazy {
        MessageCacheRepositoryImpl(
            apiService = apiService,
            sessionManager = sessionManager
        )
    }

    private val bulletinCacheRepository: BulletinCacheRepository by lazy {
        BulletinCacheRepositoryImpl(apiService = apiService)
    }

    private val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepositoryImpl(dataStore = context.dataStore)
    }

    private val bulletinRepository: BulletinRepository by lazy {
        BulletinRepositoryImpl(apiService = apiService)
    }

    /**
     * On the journey of building up the app, the first point of contact is login.
     * Here is the implementation for the login use case.
     */
    override val loginUseCase: LoginUseCase by lazy {
        // The container provides ("injects") the repository to the use case.
        LoginUseCase(loginRepository)
    }

    override val fetchNewUserProfileUseCase: FetchNewUserProfileUseCase by lazy {
        FetchNewUserProfileUseCase(roleRepository)
    }

    override val logoutUseCase: LogoutUseCase by lazy {
        LogoutUseCase(logoutRepository)
    }

    override val getUserDashboardUseCase: GetUserDashboardUseCase by lazy {
        GetUserDashboardUseCase(
            userRepository,
            messageRepository,
            roleRepository,
            permissionRepository
        )
    }

    override val loadActiveMessagesUseCase: LoadActiveMessagesUseCase by lazy {
        LoadActiveMessagesUseCase(messageCacheRepository)
    }

    override val getCachedMessagesUseCase: GetCachedMessagesUseCase by lazy {
        GetCachedMessagesUseCase(messageCacheRepository, userPreferencesRepository)
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

    override val restoreMessageUseCase: RestoreMessageUseCase by lazy {
        RestoreMessageUseCase(messageCacheRepository)
    }

    override val getMessageEventsUseCase: GetMessageEventsUseCase by lazy {
        GetMessageEventsUseCase(messageCacheRepository)
    }

    override val loadAllBulletinsUseCase: LoadAllBulletinsUseCase by lazy {
        LoadAllBulletinsUseCase(bulletinCacheRepository)
    }

    override val getAllBulletinsUseCase: GetAllBulletinsUseCase by lazy {
        GetAllBulletinsUseCase(bulletinCacheRepository, userPreferencesRepository)
    }

    override val loadBulletinUseCase: LoadBulletinUseCase by lazy {
        LoadBulletinUseCase(bulletinCacheRepository)
    }

    override val getBulletinUseCase: GetBulletinUseCase by lazy {
        GetBulletinUseCase(bulletinCacheRepository)
    }

    override val markMessageAsImportantUseCase: MarkMessageAsImportantUseCase by lazy {
        MarkMessageAsImportantUseCase(userPreferencesRepository)
    }

    override val markMessageAsNotImportantUseCase: MarkMessageAsNotImportantUseCase by lazy {
        MarkMessageAsNotImportantUseCase(userPreferencesRepository)
    }

    override val markBulletinAsBookmarkUseCase: MarkBulletinAsBookmarkUseCase by lazy {
        MarkBulletinAsBookmarkUseCase(userPreferencesRepository)
    }

    override val markBulletinAsNotBookmarkUseCase: MarkBulletinAsNotBookmarkUseCase by lazy {
        MarkBulletinAsNotBookmarkUseCase(userPreferencesRepository)
    }

    override val getAdminDashboardUseCase: GetAdminDashboardUseCase by lazy {
        GetAdminDashboardUseCase(userRepository, messageRepository, bulletinRepository)
    }

    override val loadAllUsersUseCase: LoadAllUsersUseCase by lazy {
        LoadAllUsersUseCase(userRepository)
    }

    override val getAdminUserInformationUseCase: GetAdminUserInformationUseCase by lazy {
        GetAdminUserInformationUseCase(userRepository)
    }

    override val loadSelectedMessagesUseCase: LoadSelectedMessagesUseCase by lazy {
        LoadSelectedMessagesUseCase(messageCacheRepository)
    }

}