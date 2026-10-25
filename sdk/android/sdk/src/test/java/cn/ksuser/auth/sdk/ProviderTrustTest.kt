package cn.ksuser.auth.sdk

import org.junit.Assert.*
import org.junit.Test

class ProviderTrustTest {
    private val first = "AB".repeat(32)
    private val second = "CD".repeat(32)

    @Test fun fingerprintsAreSha256NormalizedAndValidated() {
        assertEquals(first, ProviderTrust.normalizeFingerprint("ab:".repeat(31) + "ab"))
        assertEquals(first, ProviderTrust.normalizeFingerprint(first.lowercase()))
        assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", ProviderTrust.fingerprint(byteArrayOf()))
        for (invalid in listOf("", "A".repeat(63), "GG".repeat(32), "AA:".repeat(32), "A:B".repeat(32))) {
            assertEquals(KsuserAuthErrorCode.INVALID_CONFIGURATION,
                (runCatching { ProviderTrust.normalizeFingerprint(invalid) }.exceptionOrNull() as KsuserAuthException).code)
        }
    }

    @Test fun rotationLineageMayUseTrustedAncestorButConcurrentSignersMustAllMatch() {
        assertTrue(ProviderTrust.matches(setOf(first), listOf(first, second), false))
        assertFalse(ProviderTrust.matches(setOf(first), listOf(first, second), true))
        assertTrue(ProviderTrust.matches(setOf(first, second), listOf(first, second), true))
        assertFalse(ProviderTrust.matches(setOf(first), listOf(second), false))
        assertFalse(ProviderTrust.matches(emptySet(), listOf(first), false))
        assertFalse(ProviderTrust.matches(setOf(first), emptyList(), true))
    }

    @Test fun productionDefaultsAndSelfHostedTrustAreExplicitAndConfigurationBound() {
        val production = KsuserAuthConfiguration("client", KsuserAuthorizationMode.OAUTH, "https://client.example/callback")
        assertEquals(2, production.providerPins.size)
        val hosted = production.copy(webBaseUrl = "https://auth.example")
        assertTrue(hosted.providerPins.isEmpty())
        val pinned = hosted.copy(trustedProviderCertificateSha256 = listOf(first))
        assertEquals(setOf(first), pinned.providerPins)
        assertNotEquals(hosted.identity, pinned.identity)
        assertEquals(pinned.identity, hosted.copy(trustedProviderCertificateSha256 = listOf("ab:".repeat(31) + "ab")).identity)
        assertEquals(KsuserAuthErrorCode.INVALID_CONFIGURATION,
            (runCatching { hosted.copy(trustedProviderCertificateSha256 = listOf("MD5")).validate() }.exceptionOrNull() as KsuserAuthException).code)
    }
}
