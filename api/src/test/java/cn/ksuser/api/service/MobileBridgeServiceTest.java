package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileBridgeServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SessionTransferService sessionTransferService;
    private MobileBridgeService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        sessionTransferService = mock(SessionTransferService.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AppProperties appProperties = new AppProperties();
        appProperties.getMobileBridge().setAppLinkOrigin("https://auth.ksuser.cn");
        appProperties.getMobileBridge().setAllowedReturnOrigins(List.of(
            "http://localhost:5173",
            "https://auth.ksuser.cn",
            "https://www.ksuser.cn"
        ));

        service = new MobileBridgeService(redisTemplate, appProperties, sessionTransferService);
    }

    @Test
    void shouldCreateChallengeForAllowedReturnUrl() {
        MobileBridgeService.MobileBridgePayload payload = service.createChallenge(
            "https://auth.ksuser.cn/login?redirect=%2Fhome%2Foverview",
            "nonce-1"
        );

        assertEquals(MobileBridgeService.STATUS_PENDING, payload.getStatus());
        assertEquals("https://auth.ksuser.cn", payload.getReturnOrigin());
        org.junit.jupiter.api.Assertions.assertTrue(payload.getAppLink().contains("/app/bridge-login"));
    }

    @Test
    void shouldRejectReturnUrlOutsideWhitelist() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> service.createChallenge("https://evil.example.com/login", null)
        );

        assertEquals("returnUrl 不在允许的域名白名单中", error.getMessage());
    }
}
