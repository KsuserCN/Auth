package cn.ksuser.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTransferServiceTest {

    private final SessionTransferService service = new SessionTransferService(null);

    @Test
    void shouldNormalizeSupportedTargets() {
        assertEquals("web", service.normalizeTarget("web"));
        assertEquals("desktop", service.normalizeTarget("desktop"));
        assertEquals("mobile", service.normalizeTarget("mobile"));
    }

    @Test
    void shouldReturnNullForUnsupportedTarget() {
        assertNull(service.normalizeTarget("tablet"));
        assertNull(service.normalizeTarget(""));
        assertNull(service.normalizeTarget(null));
    }
}
