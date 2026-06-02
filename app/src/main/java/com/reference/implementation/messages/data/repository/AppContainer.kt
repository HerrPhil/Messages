package com.reference.implementation.messages.data.repository

import android.content.Context
import com.reference.implementation.messages.BuildConfig
import com.reference.implementation.messages.data.audit.SecurityAuditInterceptor
import com.reference.implementation.messages.data.manager.AuthSessionManager
import com.reference.implementation.messages.data.manager.TokenManager
import com.reference.implementation.messages.data.remote.ApiService
import com.reference.implementation.messages.domain.repository.LoginRepository
import com.reference.implementation.messages.domain.repository.LogoutRepository
import com.reference.implementation.messages.domain.use_case.LoginUseCase
import com.reference.implementation.messages.domain.use_case.LogoutUseCase
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
    val authSessionManager: AuthSessionManager
}

/**
 * [AppContainer] implementation that provides instances(s) of my use cases
 * and provides the needed repository value(s) to each use case.
 */

class AppMessageContainer(private val context: Context) : AppContainer {


    private val tokenManager = TokenManager(context)

    /**
     * This is related to login/logout.
     * It is the Global State Source (Application Layer) whether the user is authenticated.
     */
    override val authSessionManager = AuthSessionManager()

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

        // 2. Create the OkHttpClient and add the interceptor
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(SecurityAuditInterceptor())
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()

        val contentType = "application/json".toMediaType()

        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:4000/")
            .client(client) // This is incorporating the logging of the HTTP client
            // This is the magic line for Kotlinx Serialization
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }

    private val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(tokenManager)
    }

    /**
     * The view models should not "see" the data/repository layer.
     * Encapsulate it here!
     */
    private val loginRepository: LoginRepository by lazy {
        // The container provides ("injects") the api service to the re=pository.
        LoginRepositoryImpl(
            apiService,
            tokenManager,
            authSessionManager,
            sessionRepository
        )
    }

    private val logoutRepository: LogoutRepository by lazy {
        LogoutRepositoryImpl(
            sessionRepository = sessionRepository,
            externalScope = applicationScope,
            authSessionManager = authSessionManager
        )
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

}