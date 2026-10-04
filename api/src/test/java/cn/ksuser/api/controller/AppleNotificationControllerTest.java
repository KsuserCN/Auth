package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MVC and mocked service; no database or Redis connection. */
class AppleNotificationControllerTest {
    private AppleAuthService apple;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        apple=mock(AppleAuthService.class);
        var controller=new AppleAuthController(apple,mock(UserSettingsRepository.class),mock(UserPasskeyRepository.class),
            mock(UserRepository.class),mock(TotpService.class),mock(MfaService.class),mock(RateLimitService.class),
            mock(SensitiveOperationService.class),mock(UserSessionService.class),mock(JwtUtil.class),new AppProperties(),
            mock(SensitiveLogUtil.class),mock(AdaptiveRiskOrchestrationService.class));
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
    }
    private void expectFailure(RuntimeException exception,int status) throws Exception {
        doThrow(exception).when(apple).notification("signed-notification");
        mvc.perform(post("/oauth/apple/notifications").contentType(MediaType.APPLICATION_JSON)
            .content("{\"payload\":\"signed-notification\"}"))
            .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(status));
    }
    @Test void processingLeaseReturns503ForRetry() throws Exception {
        expectFailure(new IllegalStateException("processing lease"),503);
    }
    @Test void redisOrDatabaseAvailabilityFailureReturns503() throws Exception {
        expectFailure(new DataAccessResourceFailureException("storage unavailable"),503);
    }
    @Test void transactionCommitFailureReturns503() throws Exception {
        expectFailure(new TransactionSystemException("commit unavailable"),503);
    }
    @Test void invalidSignatureOrMalformedPayloadReturns400() throws Exception {
        expectFailure(new IllegalArgumentException("invalid signature or event"),400);
    }
    @Test void completedNotificationReturns200() throws Exception {
        mvc.perform(post("/oauth/apple/notifications").contentType(MediaType.APPLICATION_JSON)
            .content("{\"payload\":\"signed-notification\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(apple).notification("signed-notification");
    }
}
