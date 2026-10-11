package cn.ksuser.auth

import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.ApiException
import cn.ksuser.auth.core.network.ApiResultCodeResolver
import cn.ksuser.auth.core.network.GeneratedApiResultCodes
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.data.model.OAuthBindCallbackResponse
import cn.ksuser.auth.data.model.PasswordRequirement
import cn.ksuser.auth.data.model.SensitiveLogItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class ApiResultCodeTest {
    private val gson = Gson()

    @Test
    fun detailedCodeAndParametersResolveLocally() = runTest {
        val body = """{"code":400,"errorCode":"AUTH_020","params":{"min":8,"max":64},"msg":"服务端原始错误"}"""
        try {
            executeEnvelope<JsonObject>(gson) {
                Response.error(400, body.toResponseBody("application/json".toMediaType()))
            }
            fail("Expected API error")
        } catch (error: ApiException) {
            assertEquals(400, error.statusCode)
            assertEquals("AUTH_020", error.errorCode)
            assertEquals("密码长度必须在8-64个字符之间", error.message)
        }
    }

    @Test
    fun unknownCodeNeverUsesLegacyServerText() = runTest {
        val body = """{"code":401,"errorCode":"NEW_UNKNOWN_CODE","msg":"服务端原始错误","error_description":"协议原始错误"}"""
        try {
            executeEnvelope<JsonObject>(gson) {
                Response.error(401, body.toResponseBody("application/json".toMediaType()))
            }
            fail("Expected API error")
        } catch (error: ApiException) {
            assertEquals(ApiResultCodeResolver.message(null, statusCode = 401), error.message)
            assertFalse(error.message.contains("原始错误"))
        }
    }

    @Test
    fun flowCodesKeepTheirStateAndUseCatalogueMessage() {
        listOf(201 to "AUTH_MFA_REQUIRED", 202 to "OAUTH_BIND_REQUIRED").forEach { (code, resultCode) ->
            val envelope = ApiEnvelope<JsonObject>(code = code, errorCode = resultCode)
            requireCode(envelope, 201, 202)
            assertEquals(GeneratedApiResultCodes.messages[resultCode], envelope.resultMessage)
        }
    }

    @Test
    fun missingAndInvalidParametersUseSafeFallback() {
        assertEquals(GeneratedApiResultCodes.messages["BAD_REQUEST"], ApiResultCodeResolver.message("AUTH_020", mapOf("min" to JsonPrimitive(8)), 400))
        assertEquals(GeneratedApiResultCodes.messages["BAD_REQUEST"], ApiResultCodeResolver.message("AUTH_020", mapOf("min" to JsonObject(), "max" to JsonPrimitive(64)), 400))
    }

    @Test
    fun envelopeValidationKeepsDetailedCodesAndRejectsMissingStatus() {
        for (envelope in listOf(ApiEnvelope<JsonObject>(code = 400, errorCode = "AUTH_VERIFICATION_CODE_INVALID"), ApiEnvelope<JsonObject>(errorCode = "REQUEST_SUCCESS"))) {
            try {
                requireCode(envelope, 200)
                fail("Expected envelope rejection")
            } catch (error: ApiException) {
                val expectedCode = if (envelope.code == null) "INVALID_RESPONSE" else "AUTH_VERIFICATION_CODE_INVALID"
                assertEquals(expectedCode, error.errorCode)
                assertEquals(GeneratedApiResultCodes.messages[expectedCode], error.message)
            }
        }
    }

    @Test
    fun policiesUseCatalogueAndCallbackIgnoresServerMessage() {
        val policy = gson.fromJson("""{"minLength":8,"maxLength":64,"requireUppercase":true,"requireLowercase":true,"requireDigits":true,"requireSpecialChars":false,"requirementCode":"AUTH_020","requirementParams":{"min":8,"max":64},"requirementMessage":"服务端原始策略"}""", PasswordRequirement::class.java)
        assertEquals("密码长度必须在8-64个字符之间", policy.requirementMessage)
        val callback = gson.fromJson("""{"bound":true,"provider":"qq","message":"服务端原始结果"}""", OAuthBindCallbackResponse::class.java)
        assertEquals(null, callback.message)
    }

    @Test
    fun auditLogsTranslateOnlyCompleteKnownCodes() {
        listOf(
            "AUTH_VERIFICATION_CODE_INVALID" to GeneratedApiResultCodes.messages["AUTH_VERIFICATION_CODE_INVALID"],
            "AUTH_020" to GeneratedApiResultCodes.messages["BAD_REQUEST"],
            "历史审计：验证码错误" to "历史审计：验证码错误",
            "error AUTH_VERIFICATION_CODE_INVALID" to "error AUTH_VERIFICATION_CODE_INVALID",
        ).forEach { (stored, expected) ->
            val log = SensitiveLogItem(id = 1, operationType = "LOGIN", ipAddress = "192.0.2.1", result = "failure", failureReason = stored, createdAt = "2026-10-11T10:00:00")
            assertEquals(expected, log.displayFailureReason)
        }
    }
}
