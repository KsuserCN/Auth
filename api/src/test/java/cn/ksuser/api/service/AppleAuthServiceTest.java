package cn.ksuser.api.service;

import cn.ksuser.api.config.*;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.security.SecurityValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppleAuthServiceTest {
    private AppleAuthService service;
    private StringRedisTemplate redis;
    private ValueOperations<String,String> values;
    private AppleTokenClient apple;
    private UserRepository users;
    private UserOauthAccountRepository accounts;
    private UserPasskeyRepository passkeys;
    private UserSettingsRepository settings;
    private AppleRevocationService revocations;
    private UserSessionService sessions;
    private final AppleAuthService.Context anonymous=new AppleAuthService.Context(null,null,"127.0.0.1","KsuserAuthiOS/1.0");
    private final AppleAuthService.Context authenticated=new AppleAuthService.Context("uuid-user",42L,"127.0.0.1","KsuserAuthiOS/1.0");
    @BeforeEach @SuppressWarnings("unchecked") void setUp() {
        redis=mock(StringRedisTemplate.class);values=mock(ValueOperations.class);
        Map<String,String> store=new ConcurrentHashMap<>();when(redis.opsForValue()).thenReturn(values);
        doAnswer(inv -> {store.put(inv.getArgument(0),inv.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0)));
        when(values.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0))!=null);
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0),inv.getArgument(1))==null);
        AppleAuthProperties properties=new AppleAuthProperties();properties.setClientIds(List.of("cn.ksuser.ios"));
        properties.setKeyId("key");properties.setTeamId("team");properties.setPrivateKeyPem("test-private-key");
        properties.setCredentialEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        apple=mock(AppleTokenClient.class);users=mock(UserRepository.class);accounts=mock(UserOauthAccountRepository.class);
        passkeys=mock(UserPasskeyRepository.class);settings=mock(UserSettingsRepository.class);revocations=mock(AppleRevocationService.class);sessions=mock(UserSessionService.class);
        User user=new User("uuid-user","user","",null);user.setId(1L);
        when(users.findByUuid("uuid-user")).thenReturn(Optional.of(user));when(users.findByUuidForUpdate("uuid-user")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any())).thenAnswer(inv -> {User saved=inv.getArgument(0);saved.setId(2L);return saved;});
        when(apple.verifyIdentity(anyString(),anyString(),anyString())).thenReturn(new AppleTokenClient.Identity("apple-user",null));
        when(apple.exchangeCode(anyString(),anyString(),anyString())).thenReturn(new AppleTokenClient.Tokens("secret-refresh-token",new AppleTokenClient.Identity("apple-user",null)));
        service=new AppleAuthService(redis,properties,apple,new AppleCredentialCipher(properties),users,accounts,passkeys,settings,new SecurityValidator(new AppProperties()),revocations,sessions);
    }
    private AppleAuthService.Verified authorize(String purpose,AppleAuthService.Context context) {
        var challenge=service.challenge(purpose,"cn.ksuser.ios",context);
        return service.verify(new AppleAuthService.Credential(challenge.challengeId(),"token","code",challenge.state(),null,null,null),context,false);
    }
    @Test void challengeRejectsUnauthenticatedBindAndUnknownClient() {
        assertThrows(IllegalArgumentException.class,() -> service.challenge("bind","cn.ksuser.ios",anonymous));
        assertThrows(IllegalStateException.class,() -> service.challenge("login","evil-app",anonymous));
        verifyNoInteractions(apple);
    }
    @Test void challengeStateAndSessionAreCheckedBeforeAppleVerificationAndCannotReplay() {
        var challenge=service.challenge("bind","cn.ksuser.ios",authenticated);
        var credential=new AppleAuthService.Credential(challenge.challengeId(),"token","code",challenge.state(),null,null,null);
        var otherSession=new AppleAuthService.Context("uuid-user",43L,"127.0.0.1","KsuserAuthiOS/1.0");
        assertThrows(IllegalArgumentException.class,() -> service.verify(credential,otherSession,false));
        assertThrows(IllegalArgumentException.class,() -> service.verify(credential,authenticated,false));
        verifyNoInteractions(apple);
    }
    @Test void wrongStateRejectsEvenValidCredentials() {
        var challenge=service.challenge("login","cn.ksuser.ios",anonymous);
        var credential=new AppleAuthService.Credential(challenge.challengeId(),"token","code","different-state",null,null,null);
        assertThrows(IllegalArgumentException.class,() -> service.verify(credential,anonymous,false));verifyNoInteractions(apple);
    }
    @Test void codeIdentityMustMatchSignedTokenIdentity() {
        when(apple.exchangeCode(anyString(),anyString(),anyString())).thenReturn(new AppleTokenClient.Tokens("refresh",new AppleTokenClient.Identity("different-user",null)));
        assertThrows(IllegalArgumentException.class,() -> authorize("login",anonymous));verify(accounts,never()).save(any());
    }
    @Test void explicitTermsCreatePasswordlessAccountWithAbsentEmailAndEncryptedCredential() {
        Map<String,Object> data=service.pendingData(authorize("login",anonymous).identity());
        String ticket=(String)data.get("oauthBindToken");
        assertThrows(IllegalArgumentException.class,() -> service.register(new AppleAuthService.PendingRequest(ticket,false,null),anonymous));
        User user=service.register(new AppleAuthService.PendingRequest(ticket,true,null),anonymous);
        assertNull(user.getPasswordHash());assertNull(user.getEmail());assertFalse(user.getHasPassword());assertTrue(user.getUsername().startsWith("apple_"));
        var captured=org.mockito.ArgumentCaptor.forClass(UserOauthAccount.class);verify(accounts).saveAndFlush(captured.capture());
        assertNotEquals("secret-refresh-token",captured.getValue().getAppleRefreshTokenEncrypted());
        assertThrows(IllegalArgumentException.class,() -> service.register(new AppleAuthService.PendingRequest(ticket,true,null),anonymous));
    }
    @Test void matchingEmailNeverAutoMergesOrRegisters() {
        when(apple.verifyIdentity(anyString(),anyString(),anyString())).thenReturn(new AppleTokenClient.Identity("apple-user","existing@example.com"));
        when(users.findByEmail("existing@example.com")).thenReturn(Optional.of(new User()));
        Map<String,Object> data=service.pendingData(authorize("login",anonymous).identity());
        assertEquals(true,data.get("emailConflict"));assertEquals(false,data.get("canRegister"));
        assertThrows(IllegalArgumentException.class,() -> service.register(new AppleAuthService.PendingRequest((String)data.get("oauthBindToken"),true,null),anonymous));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void loginTicketCannotBindAndBindTicketCannotSwitchSession() {
        String loginTicket=(String)service.pendingData(authorize("login",anonymous).identity()).get("oauthBindToken");
        assertThrows(IllegalArgumentException.class,() -> service.bind(new AppleAuthService.PendingRequest(loginTicket,false,null),authenticated));
        String bindTicket=(String)service.pendingData(authorize("bind",authenticated).identity()).get("oauthBindToken");
        var otherSession=new AppleAuthService.Context("uuid-user",43L,"127.0.0.1","KsuserAuthiOS/1.0");
        assertThrows(IllegalArgumentException.class,() -> service.bind(new AppleAuthService.PendingRequest(bindTicket,false,null),otherSession));
        verify(accounts,never()).saveAndFlush(any());
    }
    @Test void boundTicketPreservesSessionAndLinksOnlyAuthenticatedUser() {
        String ticket=(String)service.pendingData(authorize("bind",authenticated).identity()).get("oauthBindToken");
        service.bind(new AppleAuthService.PendingRequest(ticket,false,null),authenticated);
        var captured=org.mockito.ArgumentCaptor.forClass(UserOauthAccount.class);verify(accounts).saveAndFlush(captured.capture());
        assertEquals(1L,captured.getValue().getUserId());verifyNoInteractions(sessions);
    }
    @Test void appleOnlyAccountCannotUnbindLastLoginMethod() {
        UserOauthAccount account=new UserOauthAccount();account.setUserId(1L);account.setProvider("apple");
        when(accounts.findByProviderAndUserId("apple",1L)).thenReturn(Optional.of(account));
        assertThrows(IllegalArgumentException.class,() -> service.unbind(authenticated));
        verifyNoInteractions(revocations);verify(accounts,never()).delete(any());
    }
    @Test void unbindEnqueuesRevocationBeforeRemovingBinding() {
        User user=users.findByUuid("uuid-user").orElseThrow();user.setPasswordHash("hash");user.setEmail("user@example.com");
        UserOauthAccount account=new UserOauthAccount();account.setUserId(1L);account.setProvider("apple");
        when(accounts.findByProviderAndUserId("apple",1L)).thenReturn(Optional.of(account));
        service.unbind(authenticated);
        var order=inOrder(revocations,accounts);order.verify(revocations).enqueue(account);order.verify(accounts).delete(account);
    }
    @Test void notificationsDisableDeletedIdentityRevokeSessionsAndDeduplicateAfterCommit() {
        var claims=io.jsonwebtoken.Jwts.claims().add("events","{\"type\":\"account-deleted\",\"sub\":\"apple-user\",\"event_time\":1000}").build();
        when(apple.verifyNotification("signed-notification")).thenReturn(claims);
        UserOauthAccount account=new UserOauthAccount();account.setProvider("apple");account.setUserId(1L);account.setIsEnabled(true);
        when(accounts.findByProviderAndProviderUserId("apple","apple-user")).thenReturn(Optional.of(account));
        User existingUser=users.findByUuid("uuid-user").orElseThrow();
        when(users.findById(1L)).thenReturn(Optional.of(existingUser));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.notification("signed-notification");
            assertFalse(account.getIsEnabled());verify(sessions).deleteAllSessionsByUser(any());
            for(var callback:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCommit();
                callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            }
            service.notification("signed-notification");verify(sessions,times(1)).deleteAllSessionsByUser(any());
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
    private void validNotification() {
        var claims=io.jsonwebtoken.Jwts.claims().add("events","{\"type\":\"account-deleted\",\"sub\":\"apple-user\"}").build();
        when(apple.verifyNotification("signed-notification")).thenReturn(claims);
    }
    @Test void duplicateInFlightRemainsRetryableWithoutReleasingOriginalLease() {
        validNotification();
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.notification("signed-notification");
            assertThrows(IllegalStateException.class,() -> service.notification("signed-notification"));
            verify(accounts,times(1)).findByProviderAndProviderUserId("apple","apple-user");
            verify(redis,never()).delete(anyString());
            for(var callback:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCommit();
                callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            }
            service.notification("signed-notification");
            verify(accounts,times(1)).findByProviderAndProviderUserId("apple","apple-user");
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void redisLeaseFailureIsServiceFailureBeforeAnyAccountWrites() {
        validNotification();
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class)))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Redis unavailable"));
        assertThrows(IllegalStateException.class,() -> service.notification("signed-notification"));
        verifyNoInteractions(accounts,sessions);
    }
    @Test void databaseFailureReleasesOwnedLeaseSoSameNotificationCanRetry() {
        validNotification();
        when(accounts.findByProviderAndProviderUserId("apple","apple-user"))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB unavailable"))
            .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,() -> service.notification("signed-notification"));
        verify(redis).delete(anyString());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.notification("signed-notification");
            verify(accounts,times(2)).findByProviderAndProviderUserId("apple","apple-user");
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void malformedSignedEventIsBadRequestBeforeStorageAccess() {
        when(apple.verifyNotification("signed-notification"))
            .thenReturn(io.jsonwebtoken.Jwts.claims().add("events","{invalid-json").build());
        assertThrows(IllegalArgumentException.class,() -> service.notification("signed-notification"));
        verify(values,never()).setIfAbsent(anyString(),anyString(),any(Duration.class));
        verifyNoInteractions(accounts,sessions);
    }
    @Test void commitDedupStorageFailurePropagatesToCallerAsRetryable() {
        validNotification();
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.notification("signed-notification");
            doThrow(new org.springframework.dao.DataAccessResourceFailureException("Redis unavailable"))
                .when(values).set(anyString(),eq("done"),any(Duration.class));
            var callback=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().get(0);
            assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,callback::afterCommit);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void notificationsRejectUnverifiedPayloadBeforeAccessingAccounts() {
        when(apple.verifyNotification("forged")).thenThrow(new IllegalArgumentException("signature"));
        assertThrows(IllegalArgumentException.class,() -> service.notification("forged"));verifyNoInteractions(accounts,sessions);
    }
    @Test void passwordWithoutEmailIsNotAUsableFallbackLogin() {
        User user=users.findByUuid("uuid-user").orElseThrow();user.setPasswordHash("hash");
        assertFalse(service.hasOtherLoginMethod(user));
    }
    @Test void appleRelayEmailDoesNotAllowRemovingOnlyIndependentLoginMethod() {
        User user=users.findByUuid("uuid-user").orElseThrow();user.setPasswordHash("hash");
        for (String domain : List.of("privaterelay.appleid.com", "private.icloud.com")) {
            user.setEmail("hidden@"+domain);
            assertFalse(service.hasOtherLoginMethod(user));
            assertTrue(AppleAuthService.isRelayEmail("Hidden@"+domain.toUpperCase(java.util.Locale.ROOT)));
        }
        user.setEmail("verified@example.com");assertTrue(service.hasOtherLoginMethod(user));
    }
    @Test void publicUserOmitsPasswordHashAndNormalizesMissingEmail() throws Exception {
        User user=new User("uuid","name",null,"private-password-hash");user.setCreatedAt(null);user.setUpdatedAt(null);
        String encoded=new ObjectMapper().writeValueAsString(user);
        assertFalse(encoded.contains("passwordHash"));assertFalse(encoded.contains("private-password-hash"));
        assertTrue(encoded.contains("\"hasPassword\":true"));assertTrue(encoded.contains("\"email\":\"\""));
    }
}
