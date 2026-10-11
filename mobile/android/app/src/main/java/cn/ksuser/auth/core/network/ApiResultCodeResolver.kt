package cn.ksuser.auth.core.network

import com.google.gson.JsonElement

/** Resolve API results from the shared catalogue without displaying server prose. */
object ApiResultCodeResolver {
    private val placeholder = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")

    fun message(
        errorCode: String?,
        params: Map<String, JsonElement>? = null,
        statusCode: Int = 500,
    ): String {
        val template = GeneratedApiResultCodes.messages[errorCode] ?: return fallback(statusCode)
        val matches = placeholder.findAll(template).toList()
        if (matches.any { params?.get(it.groupValues[1])?.isJsonPrimitive != true }) {
            return fallback(statusCode)
        }
        return placeholder.replace(template) { params!!.getValue(it.groupValues[1]).asString }
    }

    private fun fallback(statusCode: Int): String {
        val code = when (statusCode) {
            in 200..299 -> "REQUEST_SUCCESS"
            401 -> "UNAUTHENTICATED"
            403 -> "FORBIDDEN"
            404 -> "NOT_FOUND"
            405 -> "METHOD_NOT_ALLOWED"
            408 -> "REQUEST_TIMEOUT"
            409 -> "REQUEST_CONFLICT"
            413 -> "REQUEST_UPLOAD_TOO_LARGE"
            415 -> "UNSUPPORTED_MEDIA_TYPE"
            429 -> "RATE_LIMITED"
            in 400..499 -> "BAD_REQUEST"
            in 500..Int.MAX_VALUE -> "SERVER_ERROR"
            else -> "REQUEST_FAILED"
        }
        return GeneratedApiResultCodes.messages[code] ?: "请求失败，请重试"
    }
}
