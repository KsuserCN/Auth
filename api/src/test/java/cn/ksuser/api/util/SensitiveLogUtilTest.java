package cn.ksuser.api.util;

import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.service.SensitiveLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensitiveLogUtilTest {
    @Test void recordsTheVerificationFactorWithoutPersistingUnknownRequestValues() {
        var service = mock(SensitiveLogService.class);
        var util = new SensitiveLogUtil(); ReflectionTestUtils.setField(util, "sensitiveLogService", service);
        var request = new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.1");
        util.logSensitiveVerify(request, 1L, "email-code", true, null, null);
        util.logSensitiveVerify(request, 1L, "RECOVERY_CODE", true, null, null);
        util.logSensitiveVerify(request, 1L, "unexpected".repeat(50), false, "invalid_verification_method", null);
        var logs = ArgumentCaptor.forClass(UserSensitiveLog.class);
        verify(service, times(3)).logAsync(logs.capture());
        assertEquals("EMAIL_CODE", logs.getAllValues().get(0).getLoginMethod());
        assertEquals("RECOVERY_CODE", logs.getAllValues().get(1).getLoginMethod());
        assertNull(logs.getAllValues().get(2).getLoginMethod());
    }
}
