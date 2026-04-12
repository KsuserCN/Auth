package cn.ksuser.api.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveLogLoginMethodUtilTest {

    @Test
    void shouldSplitLegacyMfaMethod() {
        assertEquals(List.of("PASSKEY", "MFA"), SensitiveLogLoginMethodUtil.parseTokens("PASSKEY_MFA"));
    }

    @Test
    void shouldNormalizeBracketTokens() {
        assertEquals(List.of("GOOGLE", "MFA"), SensitiveLogLoginMethodUtil.parseTokens("[google, mfa]"));
    }

    @Test
    void shouldNormalizeEmailAlias() {
        assertEquals(List.of("EMAIL_CODE", "MFA"), SensitiveLogLoginMethodUtil.parseTokens("[email, mfa]"));
    }

    @Test
    void shouldKeepBridgeMethod() {
        assertEquals(List.of("BRIDGE_FROM_DESKTOP"), SensitiveLogLoginMethodUtil.parseTokens("BRIDGE_FROM_DESKTOP"));
    }
}
