package cn.ksuser.auth.sdk

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

internal object IdTokenVerifier {
    fun subject(token: String, jwks: String, issuer: String, clientId: String, nonce: String, nowSeconds: Long): String {
        try {
            require(token.length <= 64 * 1024)
            val parts = token.split('.')
            require(parts.size == 3)
            val header = JsonParser.parseString(String(decodeBase64Url(parts[0]), Charsets.UTF_8)).asJsonObject
            val claims = JsonParser.parseString(String(decodeBase64Url(parts[1]), Charsets.UTF_8)).asJsonObject
            require(header.string("alg") == "RS256" && !header.has("crit"))
            val kid = header.string("kid")?.takeIf { it.isNotEmpty() } ?: invalidToken()
            val keys = JsonParser.parseString(jwks).asJsonObject.getAsJsonArray("keys")
                .map { it.asJsonObject }.filter {
                    it.string("kid") == kid && it.string("kty") == "RSA" &&
                        (!it.has("alg") || it.string("alg") == "RS256") &&
                        (!it.has("use") || it.string("use") == "sig")
                }
            require(keys.size == 1)
            val n = decodeBase64Url(keys.single().string("n") ?: invalidToken())
            val e = decodeBase64Url(keys.single().string("e") ?: invalidToken())
            require(n.size in 256..1024 && e.size in 1..8)
            val modulus = BigInteger(1, n)
            val exponent = BigInteger(1, e)
            require(modulus.bitLength() >= 2048 && exponent >= BigInteger.valueOf(3) && exponent.testBit(0))
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initVerify(publicKey)
                update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            }
            require(signature.verify(decodeBase64Url(parts[2])))
            require(claims.string("iss") == issuer && claims.string("nonce") == nonce)
            val subject = claims.string("sub")?.takeIf { it.isNotBlank() } ?: invalidToken()
            val expiration = claims.number("exp") ?: invalidToken()
            val issued = claims.number("iat") ?: invalidToken()
            require(expiration > nowSeconds && issued <= nowSeconds + 60 && issued < expiration)
            if (claims.has("nbf")) require((claims.number("nbf") ?: invalidToken()) <= nowSeconds + 60)
            val audience = claims.get("aud") ?: invalidToken()
            val audiences = if (audience.isJsonArray) audience.asJsonArray.map { it.strictString() ?: invalidToken() }
                else listOf(audience.strictString() ?: invalidToken())
            require(clientId in audiences)
            if (audiences.size > 1 || claims.has("azp")) require(claims.string("azp") == clientId)
            return subject
        } catch (error: KsuserAuthException) {
            throw error
        } catch (_: Exception) { invalidToken() }
    }

    private fun JsonElement.strictString(): String? = takeIf { isJsonPrimitive && asJsonPrimitive.isString }?.asString
    private fun JsonObject.string(key: String): String? = get(key)?.strictString()
    private fun JsonObject.number(key: String): Double? = get(key)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isNumber
    }?.asDouble?.takeIf { it.isFinite() }
}
