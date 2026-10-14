package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.OauthRegisterBindRequest;
import cn.ksuser.api.dto.RegisterResult;
import cn.ksuser.api.dto.RegisterRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauthAccount;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserPasskeyRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.repository.UserSettingsRepository;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.MfaService;
import cn.ksuser.api.service.AccountRecoveryService;
import cn.ksuser.api.service.AdaptiveRiskMetricsService;
import cn.ksuser.api.service.AdaptiveRiskOrchestrationService;
import cn.ksuser.api.service.EmailService;
import cn.ksuser.api.service.MobileBridgeService;
import cn.ksuser.api.service.PasskeyService;
import cn.ksuser.api.service.QrChallengeService;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.service.RegistrationValidationService;
import cn.ksuser.api.service.SessionTransferService;
import cn.ksuser.api.service.SensitiveOperationService;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.TotpService;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.service.VerificationCodeService;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real registration policy and verification-code consumption without external Redis or SQL. */
class OauthRegistrationSecurityTest {
    private static final String EMAIL = "verified@example.com";
    private static final String IP = "192.0.2.10";
    private static final String CHANGED_IP = "192.0.2.99";
    private static final String USER_AGENT = "registration-security-test";
    private static final String CODE = "012345";
    private static final String BIND_TOKEN = "pending-fixture";
    private static final String PENDING_KEY = "oauth:pending-bind:" + BIND_TOKEN;
    private static final String PASSWORD = "StrongPass123";

    private OauthController controller;
    private UserService users;
    private UserOauthAccountRepository accounts;
    private UserSessionService sessions;
    private RateLimitService rateLimit;
    private JwtUtil jwt;
    private SensitiveLogUtil logs;
    private VerificationCodeService codes;
    private AppProperties properties;
    private RegistrationValidationService validation;
    private Map<String, String> redisState;
    private StringRedisTemplate redis;
    private MockHttpServletRequest http;
    private MockHttpServletResponse servletResponse;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisState = new HashMap<>();
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> redisState.get(call.getArgument(0)));
        doAnswer(call -> {
            redisState.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            long count = Long.parseLong(redisState.getOrDefault(key, "0")) + 1;
            redisState.put(key, Long.toString(count));
            return count;
        });
        when(redis.hasKey(anyString())).thenAnswer(call -> redisState.containsKey(call.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(call -> redisState.remove(call.getArgument(0)) != null);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        users = mock(UserService.class);
        accounts = mock(UserOauthAccountRepository.class);
        sessions = mock(UserSessionService.class);
        rateLimit = spy(new RateLimitService(redis, new AppProperties()));
        jwt = mock(JwtUtil.class);
        logs = mock(SensitiveLogUtil.class);
        codes = new VerificationCodeService(redis);
        properties = new AppProperties();
        validation = new RegistrationValidationService(
            properties, new SecurityValidator(properties), codes);
        UserSettingsRepository settings = mock(UserSettingsRepository.class);
        when(settings.save(any())).thenAnswer(call -> call.getArgument(0));
        controller = new OauthController(properties, accounts, mock(UserRepository.class), users,
            jwt, sessions, rateLimit, settings, mock(TotpService.class), mock(MfaService.class), logs,
            mock(UserPasskeyRepository.class), mock(SensitiveOperationService.class), redis, validation);
        doReturn(IP).when(rateLimit).getClientIp(any());
        doReturn(USER_AGENT).when(rateLimit).getClientUserAgent(any());
        http = new MockHttpServletRequest();
        http.addHeader("User-Agent", USER_AGENT);
        servletResponse = new MockHttpServletResponse();
        codes.saveCode(EMAIL, CODE, IP);
    }

    private OauthRegisterBindRequest requestFor(String provider) throws Exception {
        http.setRequestURI("/oauth/" + provider + "/register-bind");
        Map<String, String> pending = new HashMap<>();
        pending.put("provider", provider);
        pending.put("providerUserId", "verified-provider-user");
        if (provider.equals("qq")) {
            pending.put("unionId", "verified-qq-union");
        }
        redisState.put(PENDING_KEY, new ObjectMapper().writeValueAsString(pending));
        OauthRegisterBindRequest body = new OauthRegisterBindRequest();
        body.setBindToken(BIND_TOKEN);
        body.setOpenid("untrusted-client-openid");
        body.setUsername("Verified_User");
        body.setEmail(EMAIL);
        body.setPassword(PASSWORD);
        body.setCode(CODE);
        return body;
    }

    private ResponseEntity<ApiResponse<Object>> register(OauthRegisterBindRequest body) {
        return controller.registerAndBind(body, http, servletResponse);
    }

    private void assertRejected(ResponseEntity<ApiResponse<Object>> response, int status, ApiResultCode result) {
        assertEquals(status, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(result, response.getBody().getErrorCode());
        verify(users, never()).register(anyString(), anyString(), anyString());
        verify(accounts, never()).save(any());
        verifyNoInteractions(sessions, jwt);
        assertTrue(redisState.containsKey(PENDING_KEY), "Rejected registration must preserve its OAuth identity ticket");
        assertNull(servletResponse.getHeader("Set-Cookie"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void missingCodeCannotRegisterForAnyProvider(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        body.setCode(null);

        assertRejected(register(body), 400, ApiResultCode.AUTH_018);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void incorrectCodeCannotRegisterForAnyProvider(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        doReturn(CHANGED_IP).when(rateLimit).getClientIp(any());
        body.setCode("999999");

        assertRejected(register(body), 400, ApiResultCode.AUTH_027);
        assertEquals(1, codes.getErrorCount(EMAIL));
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void expiredCodeCannotRegisterForAnyProvider(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        redisState.remove("verification:code:" + EMAIL);

        assertRejected(register(body), 400, ApiResultCode.AUTH_026);
        assertEquals(1, codes.getErrorCount(EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void networkChangeStillAllowsRegistrationForAnyProvider(String provider) throws Exception {
        assertSuccessfulRegistration(provider, CHANGED_IP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void lockedEmailCannotRegisterEvenWithItsCorrectCode(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        redisState.put("verification:lock:" + EMAIL, "locked");

        assertRejected(register(body), 429, ApiResultCode.AUTH_010);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void historicalSharedIpRegistrationLockDoesNotBlockAnyOAuthProvider(String provider) throws Exception {
        redisState.put("register:lock:ip:" + IP, "locked");
        redisState.put("register:success:ip:" + IP + ":2026-10-14", "99");
        String oldUaLock = "register:lock:ua:" + "a".repeat(64);
        redisState.put(oldUaLock, "locked");

        assertSuccessfulRegistration(provider, IP);

        assertEquals("locked", redisState.get("register:lock:ip:" + IP));
        assertEquals("99", redisState.get("register:success:ip:" + IP + ":2026-10-14"));
        assertEquals("locked", redisState.get(oldUaLock));
        assertNoRegistrationLimitAccess();
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void invalidUsernameIsRejectedBeforeEmailProofIsConsumed(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        body.setUsername("a!");

        assertRejected(register(body), 400, ApiResultCode.AUTH_019);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void weakPasswordIsRejectedBeforeEmailProofIsConsumed(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        body.setPassword("alllowercase");

        assertRejected(register(body), 400, ApiResultCode.PASSWORD_REQUIREMENTS_7);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void invalidPendingTicketDoesNotConsumeCorrectEmailCode(String provider) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        body.setBindToken("missing-ticket");

        assertRejected(register(body), 400, ApiResultCode.OAUTH_087);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
        assertEquals(0, codes.getErrorCount(EMAIL));
    }

    @Test
    void attackersEmailProofCannotBeSubmittedForVictimsEmailAddress() throws Exception {
        OauthRegisterBindRequest body = requestFor("github");
        codes.saveCode("attacker@example.com", CODE, IP);
        body.setEmail("victim@example.com");

        assertRejected(register(body), 400, ApiResultCode.AUTH_023);
        assertEquals(CODE, redisState.get("verification:code:attacker@example.com"));
        assertEquals(0, codes.getErrorCount("victim@example.com"));
    }

    @Test
    void alreadyLinkedOAuthIdentityDoesNotConsumeEmailProof() throws Exception {
        OauthRegisterBindRequest body = requestFor("github");
        UserOauthAccount existing = new UserOauthAccount();
        existing.setUserId(99L);
        when(accounts.findByProviderAndProviderUserId("github", "verified-provider-user"))
            .thenReturn(Optional.of(existing));

        assertRejected(register(body), 409, ApiResultCode.OAUTH_033);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
        assertEquals(0, codes.getErrorCount(EMAIL));
    }

    @Test
    void passwordLengthLimitsApplyBeforeEmailProofIsConsumed() throws Exception {
        OauthRegisterBindRequest body = requestFor("github");
        for (String password : new String[] {"Aa1", "Aa1".repeat(23)}) {
            body.setPassword(password);
            assertRejected(register(body), 400, ApiResultCode.AUTH_020);
            assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
        }
    }

    @Test
    void commonWeakPasswordsRemainRejectedWhenCharacterRequirementsAreRelaxed() throws Exception {
        properties.getPassword().setRequireUppercase(false);
        properties.getPassword().setRequireLowercase(false);
        properties.getPassword().setRequireDigits(false);
        OauthRegisterBindRequest body = requestFor("github");
        body.setPassword("password");

        assertRejected(register(body), 400, ApiResultCode.AUTH_021);
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-username", "weak-password", "missing-code"})
    void ordinaryAndOAuthRegistrationApplyTheSamePolicy(String scenario) throws Exception {
        OauthRegisterBindRequest body = requestFor("github");
        switch (scenario) {
            case "invalid-username" -> body.setUsername("a!");
            case "weak-password" -> body.setPassword("alllowercase");
            case "missing-code" -> body.setCode(null);
            default -> fail("Unexpected scenario: " + scenario);
        }
        var oauth = register(body);
        var ordinary = ordinaryController().register(new RegisterRequest(body.getUsername(), body.getEmail(),
            body.getPassword(), body.getCode()), http, new MockHttpServletResponse());

        assertEquals(ordinary.getStatusCode(), oauth.getStatusCode());
        assertEquals(ordinary.getBody().getErrorCode(), oauth.getBody().getErrorCode());
        assertRejected(oauth, ordinary.getStatusCode().value(), ordinary.getBody().getErrorCode());
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qq", "github", "microsoft", "google"})
    void successfulRegistrationConsumesEmailProofAndOAuthTicket(String provider) throws Exception {
        assertSuccessfulRegistration(provider, IP);
    }

    private AuthController ordinaryController() {
        return new AuthController(users, sessions, jwt, mock(EmailService.class),
            codes, rateLimit, mock(SensitiveOperationService.class), mock(TokenBlacklistService.class),
            new SecurityValidator(properties), properties, mock(PasskeyService.class), mock(TotpService.class),
            mock(EncryptionUtil.class), mock(UserSettingsRepository.class), mock(MfaService.class), logs,
            mock(SessionTransferService.class), mock(MobileBridgeService.class), mock(AccountRecoveryService.class),
            mock(QrChallengeService.class), mock(AdaptiveRiskOrchestrationService.class),
            mock(AdaptiveRiskMetricsService.class), accounts, validation);
    }

    private User prepareSuccessfulRegistration(String username, String currentIp) {
        User user = new User("registered-uuid", username, EMAIL, "stored-password-hash");
        user.setId(17L);
        when(users.register(username, EMAIL, PASSWORD))
            .thenReturn(new RegisterResult(RegisterResult.Status.SUCCESS, user));
        UserSession session = new UserSession();
        session.setId(42L);
        session.setSessionVersion(0);
        when(jwt.generateRefreshToken(user.getUuid())).thenReturn("refresh-fixture");
        when(sessions.createSession(user, "refresh-fixture", currentIp, USER_AGENT)).thenReturn(session);
        when(jwt.generateAccessToken(user.getUuid(), 42L, 0)).thenReturn("access-fixture");
        return user;
    }

    private void assertSuccessfulRegistration(String provider, String currentIp) throws Exception {
        OauthRegisterBindRequest body = requestFor(provider);
        doReturn(currentIp).when(rateLimit).getClientIp(any());
        User user = prepareSuccessfulRegistration(body.getUsername(), currentIp);

        var response = register(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ApiResultCode.OAUTH_003, response.getBody().getErrorCode());
        assertEquals("access-fixture", ((Map<?, ?>) response.getBody().getData()).get("accessToken"));
        ArgumentCaptor<UserOauthAccount> saved = ArgumentCaptor.forClass(UserOauthAccount.class);
        verify(accounts).save(saved.capture());
        assertEquals(17L, saved.getValue().getUserId());
        assertEquals(provider, saved.getValue().getProvider());
        assertEquals("verified-provider-user", saved.getValue().getProviderUserId());
        assertEquals(provider.equals("qq") ? "verified-qq-union" : null, saved.getValue().getUnionId());
        assertFalse(redisState.containsKey(PENDING_KEY));
        for (String field : new String[] {"code", "sent", "email", "ip", "error"}) {
            assertFalse(redisState.containsKey("verification:" + field + ":" + EMAIL));
        }
        verify(sessions).createSession(user, "refresh-fixture", currentIp, USER_AGENT);
        assertEquals(0, codes.getErrorCount(EMAIL));
        verify(logs).logRegister(eq(http), eq(17L), eq(true), isNull(), anyLong());
        assertTrue(servletResponse.getHeader("Set-Cookie").contains("refreshToken=refresh-fixture"));

        var replay = register(body);
        assertEquals(400, replay.getStatusCode().value());
        assertEquals(ApiResultCode.OAUTH_087, replay.getBody().getErrorCode());
        verify(users, times(1)).register(anyString(), anyString(), anyString());
        verify(accounts, times(1)).save(any());
        verify(sessions, times(1)).createSession(any(), anyString(), anyString(), anyString());
    }

    @Test
    void ordinaryRegistrationAlsoSucceedsAfterNetworkChange() {
        doReturn(CHANGED_IP).when(rateLimit).getClientIp(any());
        User user = prepareSuccessfulRegistration("Verified_User", CHANGED_IP);

        var response = ordinaryController().register(
            new RegisterRequest("Verified_User", EMAIL, PASSWORD, CODE), http, servletResponse);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ApiResultCode.AUTH_032, response.getBody().getErrorCode());
        verify(users).register("Verified_User", EMAIL, PASSWORD);
        verify(sessions).createSession(user, "refresh-fixture", CHANGED_IP, USER_AGENT);
        assertEquals(0, codes.getErrorCount(EMAIL));
        assertEquals(4, codes.verifyRegistrationCode(EMAIL, CODE));
    }

    @Test
    void historicalSharedIpRegistrationLockDoesNotBlockOrdinaryRegistration() {
        String lockKey = "register:lock:ip:" + IP;
        redisState.put(lockKey, "locked");
        String oldUaLock = "register:lock:ua:" + "a".repeat(64);
        redisState.put(oldUaLock, "locked");
        prepareSuccessfulRegistration("Verified_User", IP);

        var response = ordinaryController().register(
            new RegisterRequest("Verified_User", EMAIL, PASSWORD, CODE), http, servletResponse);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ApiResultCode.AUTH_032, response.getBody().getErrorCode());
        assertEquals("locked", redisState.get(lockKey));
        assertFalse(redisState.keySet().stream().anyMatch(key -> key.startsWith("register:success:")));
        assertEquals("locked", redisState.get(oldUaLock));
        assertNoRegistrationLimitAccess();
    }

    private void assertNoRegistrationLimitAccess() {
        Stream.concat(mockingDetails(redis).getInvocations().stream(),
                mockingDetails(redis.opsForValue()).getInvocations().stream())
            .flatMap(invocation -> Stream.of(invocation.getArguments()))
            .filter(String.class::isInstance).map(String.class::cast)
            .forEach(argument -> assertFalse(argument.startsWith("register:"),
                "Registration must not read or write obsolete IP/User-Agent counters or locks: " + argument));
    }

    @Test
    void otherVerificationFlowsStillRequireTheirOriginalIp() {
        assertEquals(6, codes.verifyCode(EMAIL, CODE, CHANGED_IP));
        assertEquals(1, codes.getErrorCount(EMAIL));
        assertEquals(CODE, redisState.get("verification:code:" + EMAIL));
    }

    @Test
    void repeatedInvalidCodesReachExistingEmailLockWithoutCreatingAnAccount() throws Exception {
        OauthRegisterBindRequest body = requestFor("github");
        body.setCode("999999");
        for (int attempt = 1; attempt <= 4; attempt++) {
            doReturn("192.0.2." + (20 + attempt)).when(rateLimit).getClientIp(any());
            assertRejected(register(body), 400, ApiResultCode.AUTH_027);
        }

        doReturn(CHANGED_IP).when(rateLimit).getClientIp(any());
        assertRejected(register(body), 429, ApiResultCode.AUTH_010);
        assertTrue(codes.isLocked(EMAIL));
        body.setCode(CODE);
        assertRejected(register(body), 429, ApiResultCode.AUTH_010);
    }
}
