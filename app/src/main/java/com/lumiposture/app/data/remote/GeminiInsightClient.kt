package com.lumiposture.app.data.remote

import com.lumiposture.app.data.remote.dto.GeminiGenerateContentRequest
import com.lumiposture.app.data.remote.dto.GeminiGenerateContentResponse
import retrofit2.HttpException
import javax.inject.Inject

class GeminiInsightClient @Inject constructor(private val api: GeminiApiService) {
    suspend fun generate(apiKey: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
        try {
            return api.generateErgonomicInsight(apiKey, request, PRIMARY_MODEL)
        } catch (error: HttpException) {
            // A listed model can still reject generation for a project or be temporarily unavailable.
            if (error.code() != 404 && error.code() != 503) throw error
        }
        return api.generateErgonomicInsight(apiKey, request, BACKUP_MODEL)
    }

    companion object {
        const val PRIMARY_MODEL = "gemini-3.1-flash-lite"
        const val BACKUP_MODEL = "gemini-3.8-flash"
    }
}
