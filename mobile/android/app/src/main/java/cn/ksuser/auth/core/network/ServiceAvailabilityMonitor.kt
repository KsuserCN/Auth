package cn.ksuser.auth.core.network

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response

class ServiceAvailabilityMonitor {
    private val state = MutableStateFlow(false)
    val unavailable = state.asStateFlow()
    fun reportUnavailable() { state.value = true }
    fun reportAvailable() { state.value = false }
}

class ServiceAvailabilityInterceptor(private val monitor: ServiceAvailabilityMonitor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request()).also {
            if (it.code >= 500) monitor.reportUnavailable()
            else if (it.isSuccessful) monitor.reportAvailable()
        }
    } catch (error: IOException) {
        if (error !is SessionChangedException) monitor.reportUnavailable()
        throw error
    }
}
