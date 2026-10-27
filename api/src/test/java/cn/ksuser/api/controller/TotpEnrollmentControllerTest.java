package cn.ksuser.api.controller;

import cn.ksuser.api.dto.TotpRegistrationOptionsResponse;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TotpEnrollmentControllerTest {
    private final TotpEnrollmentService enrollments = mock(TotpEnrollmentService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TotpController(mock(TotpService.class), mock(UserService.class),
            mock(JwtUtil.class), mock(EncryptionUtil.class), mock(SensitiveOperationService.class),
            mock(RateLimitService.class), mock(SensitiveLogUtil.class), enrollments)).build();
    }

    @Test void legacyOptionsWithoutDedicatedProofAreRejectedBeforeSecretGeneration() throws Exception {
        mvc.perform(post("/auth/totp/registration-options").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SENSITIVE_VERIFICATION_REQUIRED"));
        verifyNoInteractions(enrollments);
    }

    @Test void legacyCodeAndClientRecoveryCodesCannotConfirmWithoutEnrollmentId() throws Exception {
        mvc.perform(post("/auth/totp/registration-verify").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"123456\",\"recoveryCodes\":[\"ATTACKER\"]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(enrollments);
    }

    @Test void responseReturnsOnlyTheServiceAuthorizedEnrollmentContext() throws Exception {
        var options = new TotpRegistrationOptionsResponse("fixture-secret", "otpauth://totp/fixture", new String[]{"SERVERAA"});
        options.setEnrollmentId("server-id"); options.setExpiresInSeconds(600);
        when(enrollments.start(any(), any(), eq("proof"))).thenReturn(options);
        mvc.perform(post("/auth/totp/registration-options").contentType(MediaType.APPLICATION_JSON).content("{\"proofId\":\"proof\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.enrollmentId").value("server-id"))
            .andExpect(jsonPath("$.data.expiresInSeconds").value(600));
    }

    @Test void revokedOrMismatchedProofPreservesTheFailureStatus() throws Exception {
        when(enrollments.start(any(), any(), any())).thenThrow(new TotpEnrollmentProofService.EnrollmentFailure(
            HttpStatus.FORBIDDEN, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED));
        mvc.perform(post("/auth/totp/registration-options").contentType(MediaType.APPLICATION_JSON).content("{\"proofId\":\"other-session-proof\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void confirmationPassesOnlyServerEnrollmentIdAndCodeToTheTransaction() throws Exception {
        when(enrollments.confirm(any(), any(), eq("server-id"), eq("123456"))).thenReturn(true);
        mvc.perform(post("/auth/totp/registration-verify").contentType(MediaType.APPLICATION_JSON)
            .content("{\"enrollmentId\":\"server-id\",\"code\":\"123456\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.errorCode").value("TOTP_007"));
        verify(enrollments).confirm(any(), any(), eq("server-id"), eq("123456"));
    }
}
