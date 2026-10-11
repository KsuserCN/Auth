package cn.ksuser.auth.core.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import retrofit2.Response

data class ApiEnvelope<T>(
    val code: Int? = null,
    val errorCode: String? = null,
    val params: Map<String, JsonElement>? = null,
    val data: T? = null,
) {
    val resultMessage: String
        get() = ApiResultCodeResolver.message(errorCode, params, code ?: 500)
}

class ApiException(
    val statusCode: Int,
    override val message: String,
    val errorCode: String? = null,
) : RuntimeException(message)

suspend fun <T> executeEnvelope(
    gson: Gson,
    request: suspend () -> Response<ApiEnvelope<T>>,
): ApiEnvelope<T> {
    val response = request()
    val body = response.body()
    if (response.isSuccessful && body != null) {
        return body
    }

    val parsedError = response.errorBody()?.charStream()?.use { reader ->
        runCatching { gson.fromJson(reader, ApiEnvelope::class.java) }.getOrNull()
    }
    val result = parsedError ?: body
    throw ApiException(
        response.code(),
        ApiResultCodeResolver.message(result?.errorCode, result?.params, response.code()),
        result?.errorCode,
    )
}

fun requireCode(
    envelope: ApiEnvelope<*>,
    vararg acceptedCodes: Int,
) {
    val code = envelope.code ?: throw ApiException(500, ApiResultCodeResolver.message("INVALID_RESPONSE"), "INVALID_RESPONSE")
    if (acceptedCodes.contains(code)) return
    throw ApiException(code, envelope.resultMessage, envelope.errorCode)
}
