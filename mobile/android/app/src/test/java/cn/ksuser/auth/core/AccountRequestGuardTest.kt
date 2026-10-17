package cn.ksuser.auth.core

import cn.ksuser.auth.core.session.AccountRequestGuard
import org.junit.Assert.*
import org.junit.Test

class AccountRequestGuardTest {
    @Test fun onlyLatestRefreshCanReplaceTheCurrentAccountsApplicationList() {
        var account: String? = "alice"
        val guard = AccountRequestGuard { account }
        val slow = guard.begin("alice")!!
        val latest = guard.begin("alice")!!
        assertFalse(guard.isCurrent(slow))
        assertTrue(guard.isCurrent(latest))
        account = "bob"
        assertFalse(guard.isCurrent(latest))
        assertNull(guard.begin("alice"))
        val bob = guard.begin("bob")!!
        assertTrue(guard.isCurrent(bob))
        account = null
        assertFalse(guard.isCurrent(bob))
    }
    @Test fun returningToTheSameAccountCannotReviveAnEarlierRequestAfterANewLoad() {
        var account: String? = "alice"
        val guard = AccountRequestGuard { account }
        val old = guard.begin("alice")!!
        account = "bob"
        guard.begin("bob")
        account = "alice"
        val current = guard.begin("alice")!!
        assertFalse(guard.isCurrent(old))
        assertTrue(guard.isCurrent(current))
    }
}
