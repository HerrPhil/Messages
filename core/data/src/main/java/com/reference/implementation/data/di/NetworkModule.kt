package com.reference.implementation.data.di

import android.content.Context
import com.reference.implementation.data.BuildConfig
import com.reference.implementation.data.manager.AccessTokenManager
import com.reference.implementation.data.manager.RefreshTokenManager
import com.reference.implementation.data.sources.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

// 1. Define Qualifiers to distinguish your two network pipelines
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthNetwork

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BareNetwork

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://10.0.2.2:4000"

    // 2. Shared Infrastructure Providers

    // The shared AuthSessionManager uses @Singleton @Inject constructor binding
    // The shared RoleManager uses @Singleton @Inject constructor binding
    // The shared SessionManager uses @Singleton @Inject constructor binding

    // BODY gives you headers + status + body.
    // Use HEADERS if you only want the metadata.
    // After I am comfortable with the code working, switch to NONE or BASIC for the
    // level, for security reasons - keep the token out of logcat.
    // Extra credit: redact the Header
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            redactHeader("Authorization")
        }
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // API adds a field? No crash.
        isLenient = true // Accepts malformed JSON if possible
        coerceInputValues = true // Enables coercing incorrect JSON values. See documentation
        encodeDefaults = true // Includes default values in requests
    }

    @Provides
    @Singleton
    fun provideConverterFactory(json: Json): Converter.Factory {
        val contentType = "application/json".toMediaType()
        return json.asConverterFactory(contentType)
    }

    @Provides
    @Singleton
    fun provideAccessTokenManager(
        @ApplicationContext context: Context // Hilt hooks into the app lifecycle to supply this automatically
    ): AccessTokenManager =
        AccessTokenManager(
            context,
            "access_token_manager_key",
            "encrypted_access_token",
            "access_token_iv"
        )

    @Provides
    @Singleton
    fun provideRefreshTokenManager(
        @ApplicationContext context: Context // Hilt hooks into the app lifecycle to supply this automatically
    ): RefreshTokenManager =
        RefreshTokenManager(
            context,
            "refresh_token_manager_key",
            "encrypted_refresh_token",
            "refresh_token_iv"
        )


    // 3. Base/Bare Network Setup
    @Provides
    @Singleton
    @BareNetwork
    fun provideBaseOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(SecurityAuditInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @BareNetwork
    fun provideAuthApiService(
        @BareNetwork okHttpClient: OkHttpClient,
        converterFactory: Converter.Factory
    ): ApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
            .create(ApiService::class.java)
    }

    // 4. Authenticated Network Setup
    @Provides
    @Singleton
    @AuthNetwork
    fun provideAuthenticatedOkHttpClient(
        @BareNetwork baseOkHttpClient: OkHttpClient,
        accessTokenManager: AccessTokenManager,
        // Just tell Hilt to pass your fully-managed authenticator here!
        tokenAuthenticator: HiltTokenAuthenticator
    ): OkHttpClient {
        return baseOkHttpClient.newBuilder()
            .addInterceptor(AuthInterceptor(accessTokenManager)) // add bearer token
            .authenticator(tokenAuthenticator) // handle 401 errors of expired tokens
            .build()
    }

    @Provides
    @Singleton
    @AuthNetwork
    fun provideApiService(
        @AuthNetwork okHttpClient: OkHttpClient,
        converterFactory: Converter.Factory
    ): ApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()
            .create(ApiService::class.java)
    }
}