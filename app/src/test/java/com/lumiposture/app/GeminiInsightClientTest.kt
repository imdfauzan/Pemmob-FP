package com.lumiposture.app

import com.lumiposture.app.data.remote.GeminiApiService
import com.lumiposture.app.data.remote.GeminiInsightClient
import com.lumiposture.app.data.remote.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class GeminiInsightClientTest {
    private val request = GeminiGenerateContentRequest(
        GeminiContent(parts = listOf(GeminiPart("instruction"))),
        listOf(GeminiContent(parts = listOf(GeminiPart("{}")))),
        GeminiGenerationConfig(responseSchema = GeminiSchema("OBJECT")),
    )

    @Test fun successfulPrimaryDoesNotCallAnotherModel() = runTest {
        val expected = GeminiGenerateContentResponse()
        val api = RecordingApi { expected }
        assertSame(expected, GeminiInsightClient(api).generate("test-key", request))
        assertEquals(listOf(GeminiInsightClient.PRIMARY_MODEL), api.models)
    }

    @Test fun projectUnavailableModelUsesBackup() = runTest {
        assertBackupAfter(404)
    }

    @Test fun overloadedModelUsesBackup() = runTest {
        assertBackupAfter(503)
    }

    @Test fun authenticationAndQuotaFailuresDoNotSwitchModels() = runTest {
        for (code in listOf(400, 401, 403, 429)) {
            val api = RecordingApi { throw httpError(code) }
            try {
                GeminiInsightClient(api).generate("test-key", request)
                fail("HTTP $code should propagate to the local fallback boundary")
            } catch (error: HttpException) { assertEquals(code, error.code()) }
            assertEquals(1, api.models.size)
        }
    }

    @Test fun unavailableBackupPropagatesWithoutAnUnboundedRetryLoop() = runTest {
        val api = RecordingApi { model -> throw httpError(if (model == GeminiInsightClient.PRIMARY_MODEL) 404 else 503) }
        try {
            GeminiInsightClient(api).generate("test-key", request)
            fail("Both model failures must reach the local fallback boundary")
        } catch (error: HttpException) { assertEquals(503, error.code()) }
        assertEquals(2, api.models.size)
    }

    @Test fun cancellationIsNotConvertedIntoAnotherRequest() = runTest {
        val api = RecordingApi { throw CancellationException("test cancellation") }
        try {
            GeminiInsightClient(api).generate("test-key", request)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, api.models.size)
    }

    private suspend fun assertBackupAfter(code: Int) {
        val expected = GeminiGenerateContentResponse()
        val api = RecordingApi { model -> if (model == GeminiInsightClient.PRIMARY_MODEL) throw httpError(code) else expected }
        assertSame(expected, GeminiInsightClient(api).generate("test-key", request))
        assertEquals(listOf(GeminiInsightClient.PRIMARY_MODEL, GeminiInsightClient.BACKUP_MODEL), api.models)
    }

    private class RecordingApi(val answer: (String) -> GeminiGenerateContentResponse) : GeminiApiService {
        val models = mutableListOf<String>()
        override suspend fun generateErgonomicInsight(apiKey: String, request: GeminiGenerateContentRequest, model: String): GeminiGenerateContentResponse {
            models += model
            return answer(model)
        }
    }

    private fun httpError(code: Int) = HttpException(Response.error<GeminiGenerateContentResponse>(
        code, "{}".toResponseBody("application/json".toMediaType()),
    ))
}
