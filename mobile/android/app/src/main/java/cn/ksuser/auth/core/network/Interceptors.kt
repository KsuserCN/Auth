package cn.ksuser.auth.core.network

import cn.ksuser.auth.core.app.AppIdentityProvider
import cn.ksuser.auth.core.session.SessionRepository
import cn.ksuser.auth.core.session.SessionTokenStore
import cn.ksuser.auth.core.env.EnvironmentProvider
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

private const val REFRESH_RETRY_HEADER = "X-KSUSER-REFRESH-RETRIED"

data class SessionRequestGeneration(val value: Long)
class SessionChangedException(message: String) : IOException(message)

class SessionGenerationInterceptor(private val sessions: SessionRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request().newBuilder()
        .tag(SessionRequestGeneration::class.java, SessionRequestGeneration(sessions.currentGeneration())).build())
}

/** Network interceptors run for every redirect, before BridgeInterceptor consumes Set-Cookie. */
class CredentialBoundaryInterceptor(private val origin: okhttp3.HttpUrl, private val sessions: SessionRepository? = null) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val epoch = request.tag(SessionRequestGeneration::class.java)?.value
        fun isStale() = epoch != null && sessions != null && epoch != sessions.currentGeneration()
        if (isStale()) throw SessionChangedException("The account changed before the request was sent")
        val outbound = if (sameOrigin(request.url, origin)) request else request.newBuilder()
            .removeHeader("Authorization").removeHeader("Cookie").removeHeader("X-XSRF-TOKEN").build()
        val response = chain.proceed(outbound)
        if (isStale() || (epoch != null && sessions != null &&
                !sessions.acceptResponseCookies(epoch, response.request.url, okhttp3.Cookie.parseAll(response.request.url, response.headers)))) {
            response.close()
            throw SessionChangedException("The account changed while the request was running")
        }
        return response
    }
}

class AccessTokenInterceptor(
    private val sessionStorage: SessionTokenStore,
    private val apiOrigin: okhttp3.HttpUrl = EnvironmentProvider.current.apiBaseUrl.toHttpUrl(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!sameOrigin(original.url, apiOrigin)) {
            return chain.proceed(original.newBuilder().removeHeader("Authorization")
                .removeHeader("Cookie").removeHeader("X-XSRF-TOKEN").build())
        }
        val builder = original.newBuilder()
        if (original.header("User-Agent").isNullOrBlank()) {
            builder.header("User-Agent", AppIdentityProvider.userAgent())
        }

        val token = sessionStorage.getAccessToken()
        if (!token.isNullOrBlank() && original.header("Authorization") == null) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}

internal fun sameOrigin(first: okhttp3.HttpUrl, second: okhttp3.HttpUrl): Boolean =
    first.scheme == second.scheme && first.host == second.host && first.port == second.port

class CsrfInterceptor(
    private val cookieStore: CookieStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method in setOf("GET", "HEAD", "OPTIONS")) {
            return chain.proceed(request)
        }

        var csrfToken = cookieStore.findCookieValue(request.url, "XSRF-TOKEN")
        if (csrfToken.isNullOrBlank()) {
            val bootstrapRequest = request.newBuilder()
                .get()
                .url(
                    request.url.newBuilder()
                        .encodedPath("/auth/csrf-token")
                        .query(null)
                        .build(),
                )
                .removeHeader("X-XSRF-TOKEN")
                .build()
            runCatching {
                chain.proceed(bootstrapRequest).use { /* CSRF cookie will be persisted by CookieJar */ }
            }
            csrfToken = cookieStore.findCookieValue(request.url, "XSRF-TOKEN")
        }

        val enriched = if (csrfToken.isNullOrBlank() || request.header("X-XSRF-TOKEN") != null) {
            request
        } else {
            request.newBuilder()
                .header("X-XSRF-TOKEN", csrfToken)
                .build()
        }
        return chain.proceed(enriched)
    }
}

class UnauthorizedRetryInterceptor(
    private val sessionRepository: SessionRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val epoch = request.tag(SessionRequestGeneration::class.java)?.value ?: sessionRepository.currentGeneration()
        val response = chain.proceed(request)
        if (!shouldRefresh(request, response)) {
            return response
        }

        val requestToken = request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
        val refreshedToken = sessionRepository.refreshAccessTokenBlocking(requestToken, epoch) ?: return response
        response.close()

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $refreshedToken")
                .header(REFRESH_RETRY_HEADER, "1")
                .build(),
        )
    }

    private fun shouldRefresh(request: Request, response: Response): Boolean {
        if (response.code != 401) return false
        if (request.url.encodedPath.endsWith("/auth/refresh")) return false
        if (request.header("Authorization").isNullOrBlank()) return false
        if (!request.header(REFRESH_RETRY_HEADER).isNullOrBlank()) return false
        return true
    }
}
