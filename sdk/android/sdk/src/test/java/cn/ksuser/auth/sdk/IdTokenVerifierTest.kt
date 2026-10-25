package cn.ksuser.auth.sdk

import com.google.gson.Gson
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test

class IdTokenVerifierTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val gson = Gson()
    private fun encode(bytes: ByteArray) = bytes.toByteString().base64Url().trimEnd('=')
    private fun unsigned(value: BigInteger) = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    private val jwk: Map<String, String> get() {
        val key = pair.public as RSAPublicKey
        return mapOf("kty" to "RSA", "kid" to "key", "alg" to "RS256", "use" to "sig",
            "n" to encode(unsigned(key.modulus)), "e" to encode(unsigned(key.publicExponent)))
    }
    private fun claims() = mutableMapOf<String, Any>("iss" to "https://api.example/official", "aud" to "client",
        "nonce" to "nonce", "sub" to "subject", "iat" to 900, "exp" to 1300)
    private fun token(claims: Map<String, Any> = claims(), header: Map<String, Any> = mapOf("alg" to "RS256", "kid" to "key")): String {
        val input = encode(gson.toJson(header).toByteArray()) + "." + encode(gson.toJson(claims).toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(input.toByteArray()) }.sign()
        return input + "." + encode(signature)
    }
    private fun verify(jwt: String, keys: List<Map<String, String>> = listOf(jwk)) =
        IdTokenVerifier.subject(jwt, gson.toJson(mapOf("keys" to keys)), "https://api.example/official", "client", "nonce", 1000)

    @Test fun verifiesRs256AndMultipleAudienceAzp() {
        assertEquals("subject", verify(token()))
        assertEquals("subject", verify(token(claims().apply { put("aud", listOf("other", "client")); put("azp", "client") })))
    }

    @Test fun rejectsUntrustedClaimsAlgorithmsAndKeys() {
        val modifications = listOf<Pair<String, Any>>("iss" to "https://evil.example", "aud" to "other",
            "nonce" to "wrong", "exp" to 1000, "iat" to 1100, "iat" to "900", "exp" to true,
            "nbf" to 1100, "sub" to "", "azp" to "other", "aud" to listOf("client", "other"))
        for ((key, value) in modifications) {
            val error = runCatching { verify(token(claims().apply { put(key, value) })) }.exceptionOrNull()
            assertEquals("$key=$value", KsuserAuthErrorCode.INVALID_ID_TOKEN, (error as? KsuserAuthException)?.code)
        }
        assertNotNull(runCatching { verify(token(header = mapOf("alg" to "none", "kid" to "key"))) }.exceptionOrNull())
        assertNotNull(runCatching { verify(token(header = mapOf("alg" to "RS256", "kid" to "key", "crit" to emptyList<String>()))) }.exceptionOrNull())
        assertNotNull(runCatching { verify(token(), listOf(jwk, jwk)) }.exceptionOrNull())
        assertNotNull(runCatching { verify(token(), listOf(jwk + ("use" to "enc"))) }.exceptionOrNull())
        val valid = token()
        val pieces = valid.split('.')
        val tampered = encode(gson.toJson(claims().apply { put("sub", "attacker") }).toByteArray())
        assertNotNull(runCatching { verify("${pieces[0]}.$tampered.${pieces[2]}") }.exceptionOrNull())
    }
}
