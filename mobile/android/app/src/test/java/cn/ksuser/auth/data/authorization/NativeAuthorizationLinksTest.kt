package cn.ksuser.auth.data.authorization

import org.junit.Assert.*
import org.junit.Test

class NativeAuthorizationLinksTest {
    private val web = "https://auth.ksuser.cn"
    private val ticket = "a".repeat(32)
    private val redirect = "https://login.example.app/ksuser/callback"
    private val state = "s".repeat(32)

    @Test fun nativeEntryIsExactHttpsAndSeparateFromBrowserAndSchemeRoutes() {
        val link = "$web/app/sdk-authorize?ticket=$ticket"
        assertEquals(ticket, NativeAuthorizationLinks.ticket(link, web))
        assertEquals(MobileAuthorizationLinks.Request.Native(ticket), MobileAuthorizationLinks.deepLink(link, web))
        for (invalid in listOf(link.replace("https:", "http:"), "$link&ticket=$ticket", "$link&name=untrusted",
            "$link#fragment", link.replace("/sdk-authorize", "/sdk-authorize/web"),
            link.replace("auth.ksuser.cn", "auth.ksuser.cn.evil.invalid"), link.replace("auth.ksuser.cn", "user@auth.ksuser.cn"),
            link.replace("auth.ksuser.cn", "auth.ksuser.cn:444"), link.replace("sdk-authorize", "%73dk-authorize"),
            "ksuserauth://sdk-authorize?ticket=$ticket")) {
            assertNull(invalid, NativeAuthorizationLinks.ticket(invalid, web))
            assertNull(invalid, MobileAuthorizationLinks.deepLink(invalid, web))
        }
    }

    @Test fun nativeReturnRequiresExactRegisteredAddressAndOneResultWithState() {
        val approved = "$redirect?code=one-time&state=$state"
        val denied = "$redirect?error=access_denied&state=$state"
        assertEquals(approved, NativeAuthorizationLinks.returnUrl(approved, redirect, true))
        assertEquals(denied, NativeAuthorizationLinks.returnUrl(denied, redirect, false))
        for (invalid in listOf(approved.replace("https:", "http:"), approved.replace("login.example.app", "attacker.invalid"),
            approved.replace("login.example.app", "user@login.example.app"), approved.replace("/callback", "/other"),
            approved.replace("/callback", "/%63allback"), approved.replace("login.example.app", "login.example.app:443"),
            "$approved#fragment", "$approved&code=another", "$approved&%73tate=$state", "$approved&extra=x",
            "$redirect?code=one-time", "$redirect?code=&state=$state", "$redirect?code=x&state=short",
            "$redirect?code=x&error=access_denied&state=$state")) {
            assertNull(invalid, NativeAuthorizationLinks.returnUrl(invalid, redirect, true))
        }
        assertNull(NativeAuthorizationLinks.returnUrl(denied, redirect, true))
        assertNull(NativeAuthorizationLinks.returnUrl(denied.replace("access_denied", "unknown"), redirect, false))
        assertNull(MobileAuthorizationLinks.applicationReturn(approved, web))
        assertNull(MobileAuthorizationLinks.bridgeReturn(approved, web))
    }
}
