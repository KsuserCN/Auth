package cn.ksuser.auth.sdk

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal interface AuthorizationHttp {
    suspend fun create(scopes: List<String>, state: String, nonce: String?, verifier: String): NativeCreated
    suspend fun cancel(transaction: PendingTransaction)
    suspend fun exchange(transaction: PendingTransaction): KsuserTokenSet
    suspend fun verifyOfficial(tokens: KsuserTokenSet, nonce: String): String
}

internal class SdkHttp(
    private val configuration: KsuserAuthConfiguration,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build(),
) : AuthorizationHttp {
    private val gson = Gson()

    override suspend fun create(scopes: List<String>, state: String, nonce: String?, verifier: String): NativeCreated {
        val authorization = JsonObject().apply {
            addProperty("clientId", configuration.clientId)
            addProperty("redirectUri", configuration.redirectUri)
            addProperty("responseType", "code")
            addProperty("scope", scopes.joinToString(" "))
            addProperty("state", state)
            addProperty("codeChallenge", Pkce.challenge(verifier))
            addProperty("codeChallengeMethod", "S256")
            if (nonce != null) addProperty("nonce", nonce)
        }
        val body = JsonObject().apply {
            addProperty("mode", configuration.mode.name.lowercase())
            addProperty("platform", "android")
            add("authorization", authorization)
        }
        val data = envelope(request("auth/native-authorization/create", json(body)))
        val created = parse { gson.fromJson(data, NativeCreated::class.java) } ?: invalidResponse()
        CreatedValidator.validate(created, configuration)
        return created
    }

    override suspend fun cancel(transaction: PendingTransaction) {
        val body = JsonObject().apply {
            addProperty("ticket", transaction.created.ticket)
            addProperty("cancelSecret", transaction.created.cancelSecret)
        }
        envelope(request("auth/native-authorization/cancel", json(body)))
    }

    override suspend fun exchange(transaction: PendingTransaction): KsuserTokenSet {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", configuration.clientId)
            .add("redirect_uri", configuration.redirectUri)
            .add("code_verifier", transaction.verifier)
            .add("code", transaction.code ?: throw KsuserAuthException(KsuserAuthErrorCode.INVALID_CALLBACK))
            .build()
        val path = if (configuration.mode == KsuserAuthorizationMode.OFFICIAL) "official/token" else "oauth2/token"
        val raw = request(path, body)
        val tokens = parse { gson.fromJson(raw, KsuserTokenSet::class.java) } ?: invalidResponse()
        if (tokens.accessToken.isNullOrBlank() || !tokens.tokenType.equals("Bearer", ignoreCase = true) || tokens.expiresIn <= 0) invalidResponse()
        return tokens
    }

    override suspend fun verifyOfficial(tokens: KsuserTokenSet, nonce: String): String {
        val token = tokens.idToken ?: invalidToken()
        val keys = request("official/jwks")
        return IdTokenVerifier.subject(token, keys, configuration.officialIssuer, configuration.clientId, nonce, System.currentTimeMillis() / 1000)
    }

    private suspend fun request(path: String, body: RequestBody? = null): String = suspendCancellableCoroutine { continuation ->
            val url = configuration.apiBaseUrl.trimEnd('/') + "/" + path
            // Never add passport Authorization, Cookie, or the calling application's unrelated credentials.
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .apply { if (body != null) post(body) }.build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(KsuserAuthException(KsuserAuthErrorCode.NETWORK)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use { response ->
                val responseBody = response.body ?: invalidResponse()
                if (responseBody.contentLength() > MAX_BODY) invalidResponse()
                val source = responseBody.source()
                source.request(MAX_BODY + 1)
                if (source.buffer.size > MAX_BODY) invalidResponse()
                val value = source.readUtf8()
                if (!response.isSuccessful) {
                    val error = runCatching {
                        val objectValue = JsonParser.parseString(value).asJsonObject
                        (objectValue.get("errorCode") ?: objectValue.get("error"))?.asString
                    }.getOrNull()?.takeIf { Regex("[A-Za-z0-9_.-]{1,128}").matches(it) }
                    throw KsuserAuthException(KsuserAuthErrorCode.SERVER, response.code, error)
                }
                value
                    } }.recoverCatching { error ->
                        if (error is KsuserAuthException) throw error
                        if (error is IOException) throw KsuserAuthException(KsuserAuthErrorCode.NETWORK)
                        invalidResponse()
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
    }

    private fun envelope(raw: String): com.google.gson.JsonElement {
        val root = parse { JsonParser.parseString(raw).asJsonObject }
        if (root?.get("code")?.runCatching { asInt }?.getOrNull() != 200) {
            val code = root?.get("errorCode")?.runCatching { asString }?.getOrNull()
                ?.takeIf { Regex("[A-Za-z0-9_.-]{1,128}").matches(it) }
            throw KsuserAuthException(KsuserAuthErrorCode.SERVER, serverErrorCode = code)
        }
        return root.get("data")?.takeUnless { it.isJsonNull } ?: invalidResponse()
    }

    private fun json(value: JsonObject): RequestBody = gson.toJson(value).toRequestBody("application/json".toMediaType())
    private fun <T> parse(block: () -> T): T = try { block() } catch (_: Exception) { invalidResponse() }

    companion object { private const val MAX_BODY = 1024L * 1024L }
}
