package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.result.ApiResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordAuthenticationServiceTest {
    private UserService users;
    private RateLimitService limits;
    private PasswordAuthenticationService service;
    private User user;

    @BeforeEach void setup() {
        users = mock(UserService.class);
        limits = mock(RateLimitService.class);
        service = new PasswordAuthenticationService(users, limits);
        user = new User("fixture-uuid", "fixture", "fixture@example.invalid", "fixture-hash"); user.setId(7L);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(limits.acquirePasswordAttempt(anyString(), anyString(), nullable(String.class)))
            .thenReturn(RateLimitService.PasswordAttemptLimit.ALLOWED);
    }

    @Test void assertionUsesOneReservationBeforeOnePasswordCheckAndNeverUsesLegacyLogin() {
        when(users.verifyPassword(" password ", "fixture-hash")).thenReturn(true);
        assertEquals(Optional.of(user), service.authenticate(" fixture@example.invalid ", " password ", "192.0.2.1"));
        var order = inOrder(users, limits);
        order.verify(users).findByEmail("fixture@example.invalid");
        order.verify(limits).acquirePasswordAttempt("user:7", "192.0.2.1", null);
        order.verify(users).verifyPassword(" password ", "fixture-hash");
        verify(users, never()).login(anyString(), anyString());
    }

    @Test void oauthAndOrdinaryAssertionsShareTheStableDatabaseIdentityDespiteEquivalentEmailSpellings() {
        service.authenticate("Fixture@Example.Invalid", "wrong", "192.0.2.1");
        service.authenticate(" fixture@example.invalid ", "wrong", "192.0.2.1", "pending-attempt-key");
        verify(limits).acquirePasswordAttempt("user:7", "192.0.2.1", null);
        verify(limits).acquirePasswordAttempt("user:7", "192.0.2.1", "pending-attempt-key");
        verify(users, times(2)).verifyPassword("wrong", "fixture-hash");
    }

    @Test void unknownAccountsUseTheSameNormalizedHashAndStillReserveAttempts() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        assertTrue(service.authenticate(" Missing@Example.Invalid ", "wrong", "192.0.2.1").isEmpty());
        assertTrue(service.authenticate("missing@example.invalid", "wrong", "192.0.2.1").isEmpty());
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(limits, times(2)).acquirePasswordAttempt(keys.capture(), eq("192.0.2.1"), isNull());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertTrue(keys.getValue().matches("unknown:[a-f0-9]{64}"));
        verify(users, never()).verifyPassword(anyString(), any());
    }

    @Test void exhaustedBudgetRejectsBeforePasswordVerification() {
        when(limits.acquirePasswordAttempt(anyString(), anyString(), nullable(String.class)))
            .thenReturn(RateLimitService.PasswordAttemptLimit.LIMITED);
        var failure = assertThrows(PasswordAuthenticationService.AuthenticationFailure.class,
            () -> service.authenticate(user.getEmail(), "password", "192.0.2.1", "pending"));
        assertEquals(429, failure.status().value());
        assertEquals(ApiResultCode.AUTH_035, failure.resultCode());
        verify(users, never()).verifyPassword(anyString(), any());
    }

    @Test void unavailableOrUnrecognizedReservationFailsClosedBeforePasswordVerification() {
        when(limits.acquirePasswordAttempt(anyString(), anyString(), nullable(String.class)))
            .thenReturn(RateLimitService.PasswordAttemptLimit.UNAVAILABLE).thenReturn(null);
        for (int i = 0; i < 2; i++) {
            var failure = assertThrows(PasswordAuthenticationService.AuthenticationFailure.class,
                () -> service.authenticate(user.getEmail(), "password", "192.0.2.1"));
            assertEquals(503, failure.status().value());
            assertEquals(ApiResultCode.INTERNAL_ERROR, failure.resultCode());
        }
        verify(users, never()).verifyPassword(anyString(), any());
    }

    @Test void passwordlessAccountDoesNotAuthenticateButStillConsumesItsReservation() {
        user.setPasswordHash(null);
        assertTrue(service.authenticate(user.getEmail(), "password", "192.0.2.1").isEmpty());
        verify(limits).acquirePasswordAttempt("user:7", "192.0.2.1", null);
        verify(users, never()).verifyPassword(anyString(), any());
    }

    @Test void wrongPasswordRetainsReservedAttemptAndDoesNotReturnUser() {
        assertTrue(service.authenticate(user.getEmail(), "wrong", "192.0.2.1").isEmpty());
        verify(limits).acquirePasswordAttempt("user:7", "192.0.2.1", null);
        verify(limits, never()).clearAllLimits(anyString(), anyString());
    }

    @Test void malformedParametersNeverReachDatabaseOrQuotaOrPasswordEncoder() {
        for (String email : new String[]{null, " ", "a".repeat(321)}) {
            assertThrows(PasswordAuthenticationService.AuthenticationFailure.class,
                () -> service.authenticate(email, "password", "192.0.2.1"));
        }
        for (String password : new String[]{null, " ", "a".repeat(257)}) {
            assertThrows(PasswordAuthenticationService.AuthenticationFailure.class,
                () -> service.authenticate("fixture@example.invalid", password, "192.0.2.1"));
        }
        verifyNoInteractions(users, limits);
    }
}
