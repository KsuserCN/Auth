package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.data.model.*
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class TotpEnrollmentRepositoryTest {
    private val gson = Gson()
    private fun repository(handler: (String, Array<out Any?>?) -> Any?): SecurityRepository {
        val api = Proxy.newProxyInstance(KsuserApiService::class.java.classLoader,
            arrayOf(KsuserApiService::class.java)) { _, method, args -> handler(method.name, args) } as KsuserApiService
        return SecurityRepository(api, gson)
    }

    @Test fun freshCredentialProofIsPassedIntoInitializationAndEnrollmentIdIntoConfirmation() = runTest {
        val calls = mutableListOf<String>()
        val repository = repository { method, args ->
            calls += method
            when (method) {
                "verifyTotpEnrollment" -> {
                    val body = args!![0] as TotpEnrollmentVerificationRequest
                    assertEquals("password", body.method)
                    assertEquals("current-password", body.password)
                    Response.success(ApiEnvelope(200, data = TotpEnrollmentProof("fresh-proof", 300)))
                }
                "getTotpRegistrationOptions" -> {
                    assertEquals("fresh-proof", (args!![0] as TotpRegistrationOptionsRequest).proofId)
                    Response.success(ApiEnvelope(200, data = TotpRegistrationOptions("secret", "otpauth://fixture",
                        listOf("ABCDEFGH"), "enrollment-one", 600)))
                }
                "verifyTotpRegistration" -> {
                    val body = gson.toJsonTree(args!![0]).asJsonObject
                    assertEquals("enrollment-one", body["enrollmentId"].asString)
                    assertEquals("123456", body["code"].asString)
                    assertFalse(body.has("recoveryCodes"))
                    Response.success(ApiEnvelope<Unit>(200))
                }
                else -> error("Unexpected generic authentication call: $method")
            }
        }
        val proofId = repository.verifyTotpEnrollment("password", password = "current-password")
        val options = repository.getTotpRegistrationOptions(proofId)
        repository.verifyTotpRegistration(options.enrollmentId, "123456")
        assertEquals(listOf("verifyTotpEnrollment", "getTotpRegistrationOptions", "verifyTotpRegistration"), calls)
    }

    @Test fun totpQrAndEmptyProofCannotReplaceExistingCredentialProof() = runTest {
        var calls = 0
        val repository = repository { _, _ -> calls++; error("No API call expected") }
        for (method in listOf("totp", "qr", "recovery_code")) {
            try { repository.verifyTotpEnrollment(method, code = "123456"); fail("Unsupported proof method accepted") }
            catch (_: IllegalArgumentException) { }
        }
        try { repository.getTotpRegistrationOptions(""); fail("Empty proof accepted") }
        catch (_: IllegalArgumentException) { }
        try { repository.verifyTotpRegistration("", "123456"); fail("Empty enrollment accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }

    @Test fun passkeyProofUsesItsOwnEndpointAndNestedCredential() = runTest {
        val repository = repository { method, args ->
            assertEquals("verifyTotpEnrollmentPasskey", method)
            val body = gson.toJsonTree(args!![0]).asJsonObject
            assertEquals("passkey-enrollment", body["passkeyChallengeId"].asString)
            assertEquals("signature", body["credential"].asJsonObject["signature"].asString)
            Response.success(ApiEnvelope(200, data = TotpEnrollmentProof("passkey-proof", 300)))
        }
        val proof = repository.verifyTotpEnrollmentPasskey("passkey-enrollment",
            PasskeyAuthenticationPayload("credential", "client", "authenticator", "signature"))
        assertEquals("passkey-proof", proof)
    }

    @Test fun legacySetupResponseWithoutEnrollmentIsRejected() = runTest {
        val repository = repository { method, _ ->
            assertEquals("getTotpRegistrationOptions", method)
            Response.success(ApiEnvelope(200, data = TotpRegistrationOptions("secret", "otpauth://fixture",
                listOf("ABCDEFGH"), "", 0)))
        }
        try { repository.getTotpRegistrationOptions("proof"); fail("Legacy setup accepted") }
        catch (_: IllegalStateException) { }
    }
}
