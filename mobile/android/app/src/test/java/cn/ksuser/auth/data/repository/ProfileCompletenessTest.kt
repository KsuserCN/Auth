package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.data.model.UserSettings
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ProfileCompletenessTest {
    @Test fun profileAndEmailMutationsFetchCompleteAccountAfterPartialResponse() = runTest {
        val calls = mutableListOf<String>()
        val complete = UserProfile("account", "new-name", "mail@example.invalid", bio = "keep this",
            settings = UserSettings(mfaEnabled = true), hasPassword = false)
        val partial = UserProfile("account", "new-name", "mail@example.invalid")
        val api = Proxy.newProxyInstance(KsuserApiService::class.java.classLoader, arrayOf(KsuserApiService::class.java)) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "updateProfile", "changeEmail" -> Response.success(ApiEnvelope(200, data = partial))
                "getUserInfo" -> Response.success(ApiEnvelope(200, data = complete))
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KsuserApiService
        val updated = ProfileRepository(api, Gson()).updateField("username", "new-name")
        assertEquals(complete, updated)
        assertEquals(listOf("updateProfile", "getUserInfo"), calls)
        calls.clear()
        val email = SecurityRepository(api, Gson()).changeEmail("mail@example.invalid", "123456")
        assertEquals(complete, email)
        assertEquals(listOf("changeEmail", "getUserInfo"), calls)
        assertEquals(false, email.hasPassword)
        assertEquals(true, email.settings?.mfaEnabled)
    }
    @Test fun mutationCannotSucceedLocallyWhenCompleteProfileFetchFails() = runTest {
        val api = Proxy.newProxyInstance(KsuserApiService::class.java.classLoader, arrayOf(KsuserApiService::class.java)) { _, method, _ ->
            if (method.name == "updateProfile") Response.success(ApiEnvelope(200, data = UserProfile("account", "new-name", "")))
            else throw IllegalStateException("temporarily offline")
        } as KsuserApiService
        try { ProfileRepository(api, Gson()).updateField("username", "new-name"); fail("Expected failure") }
        catch (expected: IllegalStateException) { assertEquals("temporarily offline", expected.message) }
    }
}
