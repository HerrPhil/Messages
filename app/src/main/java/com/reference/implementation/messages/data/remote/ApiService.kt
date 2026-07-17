package com.reference.implementation.messages.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // Response contains access token to be used in header of the following api declarations
    // Retrofit is configured to do this in the app container

    @POST("auth/login")
    suspend fun login(@Body loginRequestDto: LoginRequestDto): Response<LoginDto>

    @POST("auth/refresh")
    suspend fun refreshAccessToken(
        @Body refreshToken: RefreshTokenRequestDto,
        @Header("x-refresh-scenario") scenario: String?  = null // Dynamic test hook!!!!
    ): Response<RefreshTokenDto>

    @GET("messages")
    suspend fun getMessages(): Response<List<MessageDto>>

    // Get message by unique ID, expect one JSON object, empty when not found, otherwise populated
    @GET("messages/{id}")
    suspend fun getMessage(@Path("id") id: Int): Response<MessageDto>

    // Get message(s) by user ID, expect one JSON list (array), empty when not found, otherwise populated
    @GET("messages/userId/{userId}")
    suspend fun getMessages(@Path("userId") userId: Int): Response<List<MessageDto>>

    @POST("messages")
    suspend fun addMessage(@Body messageRequestDto: MessageRequestDto): Response<MessageDto>

    @PUT("messages/{id}")
    suspend fun updateMessage(@Path("id") id: Int, @Body messageDto: MessageDto): Response<MessageDto>

    @PATCH("messages/{id}")
    suspend fun partialUpdateMessage(@Path("id") id: Int, @Body patchMessageRequestDto: PartialMessageRequestDto): Response<MessageDto>

    @PATCH("messages/{id}")
    suspend fun markMessageAsRead(@Path("id") id: Int, @Body markMessageAsReadDto: MarkMessageAsReadDto): Response<MessageDto>

    @PATCH("messages/{id}")
    suspend fun markMessageAsUnread(@Path("id") id: Int, @Body markMessageAsUnreadDto: MarkMessageAsUnreadDto): Response<MessageDto>

    @DELETE("messages/{id}")
    suspend fun removeMessage(@Path("id") id: Int): Response<Unit>

    // Get role(s) by target user ID
    // Expect one or more JSON objects in a list when found
    // empty list when not found
    @GET("roles/targetUserId/{targetUserId}")
    suspend fun getRoles(@Path("targetUserId") targetUserId: Int): Response<List<RoleDto>>

    @GET("permissions")
    suspend fun getPermissions(@Query("id") permissionIds: List<Int>): Response<List<PermissionDto>>

    @GET("bulletins")
    suspend fun getBulletins(): Response<List<BulletinDto>>

    // Get bulletin by unique ID, expect one JSON object, empty when not found, otherwise populated
    @GET("bulletins/{id}")
    suspend fun getBulletin(@Path("id") id: Int): Response<BulletinDto>

}