package com.aura.family.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface TelemetryApiService {

    @Headers("Content-Type: application/json")
    @POST
    suspend fun postTelemetry(
        @Url fullUrl: String,
        @Body payload: TelemetryPayload
    ): Response<ResponseBody>

    @Headers("Content-Type: application/json")
    @POST
    suspend fun postRawTelemetry(
        @Url fullUrl: String,
        @Body rawJson: RequestBody
    ): Response<ResponseBody>
}
