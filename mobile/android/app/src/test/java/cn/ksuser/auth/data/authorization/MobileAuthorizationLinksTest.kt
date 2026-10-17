package cn.ksuser.auth.data.authorization

import org.junit.Assert.*
import org.junit.Test

class MobileAuthorizationLinksTest {
    private val origin = "https://auth.ksuser.cn"
    private val ticket = "a".repeat(32)

    @Test fun recognizesOnlyExactApplicationAndBridgeEntrypoints() {
        assertEquals(MobileAuthorizationLinks.Request.Application(ticket), MobileAuthorizationLinks.deepLink("ksuserauth://authorize?ticket=$ticket", origin))
        assertEquals(MobileAuthorizationLinks.Request.Bridge(ticket), MobileAuthorizationLinks.deepLink("https://auth.ksuser.cn/app/bridge-login?challengeId=$ticket", origin))
        for (url in listOf("https://auth.ksuser.cn/app/authorize-return?ticket=$ticket",
            "https://evil.invalid/app/authorize?ticket=$ticket", "https://auth.ksuser.cn.evil.invalid/app/authorize?ticket=$ticket",
            "https://user@auth.ksuser.cn/app/authorize?ticket=$ticket", "ksuserauth://authorize:443?ticket=$ticket",
            "ksuserauth://authorize/?ticket=$ticket", "ksuserauth://authorize?ticket=$ticket&ticket=$ticket",
            "ksuserauth://authorize?ticket=$ticket#fragment", "https://auth.ksuser.cn:444/app/authorize?ticket=$ticket",
            "https://auth.ksuser.cn/app/%61uthorize?ticket=$ticket", "ksuserauth://authorize?ticket=bad")) {
            assertNull(url, MobileAuthorizationLinks.deepLink(url, origin))
        }
    }

    @Test fun qrRejectsArbitraryUrlsAndEmbeddedPrefix() {
        assertEquals(MobileAuthorizationLinks.Request.Approval(ticket), MobileAuthorizationLinks.qr("KSUSER-AUTH-QR:$ticket", origin))
        assertEquals(MobileAuthorizationLinks.Request.Transfer(ticket), MobileAuthorizationLinks.qr("KSUSER-AUTH-XFER:v1:$ticket", origin))
        for (input in listOf("https://evil.invalid/?code=$ticket", "https://evil.invalid/KSUSER-AUTH-QR:$ticket", "prefix KSUSER-AUTH-QR:$ticket", "KSUSER-AUTH-QR:$ticket&other=value", "https://auth.ksuser.cn/login?approveCode=$ticket&transferCode=$ticket")) {
            assertNull(input, MobileAuthorizationLinks.qr(input, origin))
        }
    }

    @Test fun browserReturnRequiresTrustedOriginExactPageAndBothPrivateCredentials() {
        val result = "https://auth.ksuser.cn/app/authorize-return#ticket=$ticket&secret=${"b".repeat(32)}"
        assertEquals(result, MobileAuthorizationLinks.applicationReturn(result, origin))
        assertNotNull(MobileAuthorizationLinks.applicationReturn(result.replace("auth.ksuser.cn", "www.ksuser.cn"), origin))
        for (input in listOf(result.replace("https://", "http://"), result.replace("auth.ksuser.cn", "other.ksuser.cn"),
            result.replace("authorize-return", "authorize"), result.replace("#", "?"), "$result&ticket=$ticket",
            result.substringBefore("&secret"), result.replace("auth.ksuser.cn", "user@auth.ksuser.cn"))) {
            assertNull(input, MobileAuthorizationLinks.applicationReturn(input, origin))
        }
        assertNull(MobileAuthorizationLinks.bridgeReturn("javascript:alert(1)", origin))
        assertNull(MobileAuthorizationLinks.bridgeReturn("https://evil.invalid/", origin))
        assertNull(MobileAuthorizationLinks.bridgeReturn("javascript:alert(1)", "javascript:configured-incorrectly"))
        assertNull(MobileAuthorizationLinks.applicationReturn("custom:/app/authorize-return#ticket=$ticket&secret=$ticket", "custom:configured-incorrectly"))
    }
}
