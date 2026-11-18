package com.aura.family.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    val gson: Gson by lazy {
        GsonBuilder()
            .serializeNulls()
            .create()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val telemetryApiService: TelemetryApiService by lazy {
        retrofit.create(TelemetryApiService::class.java)
    }

    fun normalizeBaseUrl(rawUrl: String): String {
        return rawUrl.trim().trimEnd('/')
    }

    fun isValidWebUrl(rawUrl: String): Boolean {
        val trimmed = normalizeBaseUrl(rawUrl)
        if (trimmed.isBlank()) return false
        val parsed = trimmed.toHttpUrlOrNull() ?: return false
        return (parsed.scheme == "http" || parsed.scheme == "https") && parsed.host.isNotBlank()
    }

    fun buildTelemetryEndpoint(baseUrl: String): String? {
        val normalized = normalizeBaseUrl(baseUrl)
        if (!isValidWebUrl(normalized)) return null
        return "$normalized/appg/telemetry/api/"
    }
}
