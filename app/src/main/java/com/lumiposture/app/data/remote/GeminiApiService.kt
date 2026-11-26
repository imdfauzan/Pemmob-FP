package com.lumiposture.app.data.remote

import com.lumiposture.app.data.remote.dto.GeminiGenerateContentRequest
import com.lumiposture.app.data.remote.dto.GeminiGenerateContentResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateErgonomicInsight(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateContentRequest,
        @Path("model") model: String,
    ): GeminiGenerateContentResponse
}
