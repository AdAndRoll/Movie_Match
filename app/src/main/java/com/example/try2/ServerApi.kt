package com.example.try2

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class PreferencesRequest(
    val user_id: String,
    val room_id: String,
    val genres: List<String>,
    val years: List<Int>
)

data class StatusResponse(
    val status: String?,
    val error: String?
)

interface ServerApi {
    @POST("submit-preferences")
    suspend fun submitPreferences(@Body request: PreferencesRequest): Response<StatusResponse>

    @GET("check-status")
    suspend fun checkStatus(@Query("room_id") roomId: String): Response<StatusResponse>
}