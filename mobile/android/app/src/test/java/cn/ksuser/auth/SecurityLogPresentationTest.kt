package cn.ksuser.auth

import cn.ksuser.auth.core.security.SecurityLogPresentation
import cn.ksuser.auth.data.model.SensitiveLogItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityLogPresentationTest {
    private fun log(operation: String = "LOGIN", method: String = "PASSWORD_MFA", action: String = "ALLOW") =
        SensitiveLogItem(id = 1, operationType = operation, loginMethod = method, ipAddress = "192.0.2.1", result = "SUCCESS", actionTaken = action, createdAt = "2026-10-14T10:00:00")
    @Test fun compositeMethodsUseReadableChineseAndSupportLegacyFormats() {
        assertEquals(listOf("密码", "二步验证"), SecurityLogPresentation.methods(log()))
        assertEquals(listOf("EMAIL_CODE", "MFA", "WECHAT"), SecurityLogPresentation.parseMethods("[\"email_mfa\", weixin, MFA, null]"))
        assertEquals(listOf("APPLE"), SecurityLogPresentation.parseMethods("PASSWORD", listOf("APPLE")))
        assertEquals("密码不正确", SecurityLogPresentation.reason("Invalid password"))
    }
    @Test fun policyEventsShowTheActionWithoutRequestSourceMethods() {
        val event = log("ADAPTIVE_POLICY", "adaptive-auth-status", "FREEZE")
        assertEquals("会话已冻结", SecurityLogPresentation.title(event))
        assertEquals(emptyList<String>(), SecurityLogPresentation.methods(event))
        assertEquals("修改邮箱", SecurityLogPresentation.title(log("CHANGE_EMAIL")))
        assertEquals(listOf("Apple"), SecurityLogPresentation.methods(log("BIND_OAUTH", "apple")))
    }
}
