package com.reference.implementation.messages.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    // TODO LOGIN REQUEST/RESPONSE; response contains access token to be used in header of
    //      the following api declarations, via retrofit

    @POST("login")
    suspend fun login(@Body loginRequestDto: LoginRequestDto): Response<LoginDto>

    @GET("messages")
    suspend fun getMessages(): Response<List<MessageDto>>

    // Get message by unique ID, expect one JSON object, empty when not found, otherwise populated
    @GET("messages/{id}")
    suspend fun getMessage(@Path("id") id: Int): Response<MessageDto>

    // Get message(s) by user ID, expect one JSON list (array), empty when not found, otherwise populated
    @GET("messages/userId/{userId}")
    suspend fun getMessages(@Path("userId") userId: Int): Response<MessageDto>

    @POST("messages")
    suspend fun addMessage(@Body messageRequestDto: MessageRequestDto): Response<MessageDto>

    @PUT("messages/{id}")
    suspend fun updateMessage(@Path("id") id: Int, @Body messageDto: MessageDto): Response<MessageDto>

    @PATCH("messages/{id}")
    suspend fun partialUpdateMessage(@Path("id") id: Int, @Body patchMessageRequestDto: PartialMessageRequestDto): Response<MessageDto>

    @DELETE("messages/{id}")
    suspend fun removeMessage(@Path("id") id: Int): Response<Unit>

    // Get role by target user ID, expect one JSON object, empty when not found, otherwise populated
    @GET("roles/{targetUserId}")
    suspend fun getRole(@Path("targetUserId") targetUserId: Int): Response<RoleDto>
}
