package cn.ksuser.api.service;

import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.BusinessArgumentException;
import cn.ksuser.api.result.BusinessStateException;

import cn.ksuser.api.config.AppleAuthProperties;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.security.SecurityValidator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AppleAuthService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;
    private final AppleAuthProperties properties;
    private final AppleTokenClient apple;
    private final AppleCredentialCipher cipher;
    private final UserRepository users;
    private final UserOauthAccountRepository accounts;
    private final UserPasskeyRepository passkeys;
    private final UserSettingsRepository settings;
    private final SecurityValidator validator;
    private final AppleRevocationService revocations;
    private final UserSessionService sessions;
    private final ObjectMapper json = new ObjectMapper();
    public AppleAuthService(StringRedisTemplate redis, AppleAuthProperties properties, AppleTokenClient apple,
                            AppleCredentialCipher cipher, UserRepository users, UserOauthAccountRepository accounts,
                            UserPasskeyRepository passkeys, UserSettingsRepository settings, SecurityValidator validator,
                            AppleRevocationService revocations, UserSessionService sessions) {
        this.redis=redis;this.properties=properties;this.apple=apple;this.cipher=cipher;this.users=users;
        this.accounts=accounts;this.passkeys=passkeys;this.settings=settings;this.validator=validator;
        this.revocations=revocations;this.sessions=sessions;
    }
    public record Context(String uuid, Long sessionId, String ip, String userAgent) {}
    public record Challenge(String challengeId, String nonce, String state, int expiresInSeconds) {}
    public record StoredChallenge(String purpose, String clientId, String nonce, String state, Context context) {}
    public record Credential(String challengeId, String identityToken, String authorizationCode, String state,
                             String fullName, String givenName, String familyName) {}
    public record Pending(String purpose, String clientId, String sub, String email, String name, String encryptedToken, Context context) {}
    public record PendingRequest(@JsonAlias("bindToken") String oauthBindToken, Boolean acceptTerms, String username) {}
    public record Verified(Pending identity, User user) {}

    public Challenge challenge(String purpose, String clientId, Context context) {
        properties.requireConfigured(clientId);
        if (purpose==null || !Set.of("login","bind","sensitive").contains(purpose)) throw new BusinessArgumentException(ApiResultCode.APPLE_014);
        if (!"login".equals(purpose)) requireAuthenticated(context);
        String id=random(),nonce=random(),state=random();
        put("apple:challenge:"+id,new StoredChallenge(purpose,clientId,nonce,state,context));
        return new Challenge(id,nonce,state,300);
    }

    public Verified verify(Credential request, Context context, boolean sensitive) {
        StoredChallenge challenge=take("apple:challenge:",request.challengeId(),StoredChallenge.class);
        if (sensitive != "sensitive".equals(challenge.purpose())) throw new BusinessArgumentException(ApiResultCode.APPLE_015);
        if (!same(challenge.state(), request.state())) throw new BusinessArgumentException(ApiResultCode.APPLE_016);
        validateContext(challenge.context(),context,!"login".equals(challenge.purpose()));
        AppleTokenClient.Identity identity=apple.verifyIdentity(request.identityToken(),challenge.clientId(),challenge.nonce());
        AppleTokenClient.Tokens tokens=apple.exchangeCode(request.authorizationCode(),challenge.clientId(),challenge.nonce());
        if (!same(identity.sub(),tokens.identity().sub())) throw new BusinessArgumentException(ApiResultCode.APPLE_017);
        String name=request.fullName();
        if (name==null || name.isBlank()) name=String.join(" ",Objects.toString(request.givenName(),""),Objects.toString(request.familyName(),"")).trim();
        if (name.length()>50) name=name.substring(0,50);
        Pending pending=new Pending(challenge.purpose(),challenge.clientId(),identity.sub(),identity.email(),name,
            cipher.encrypt(tokens.refreshToken()),challenge.context());
        UserOauthAccount account=accounts.findByProviderAndProviderUserId("apple",identity.sub()).orElse(null);
        if (sensitive) {
            User user=requireAuthenticated(context);
            if (account==null || !account.getUserId().equals(user.getId()) || !Boolean.TRUE.equals(account.getIsEnabled()))
                throw new BusinessArgumentException(ApiResultCode.APPLE_018);
            updateCredential(account,pending);accounts.save(account);
            return new Verified(pending,user);
        }
        if ("login".equals(challenge.purpose()) && account!=null) {
            User user=users.findById(account.getUserId()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_019));
            updateCredential(account,pending);account.setIsEnabled(true);account.setLastLoginAt(LocalDateTime.now());accounts.save(account);
            return new Verified(pending,user);
        }
        return new Verified(pending,null);
    }

    public Map<String,Object> pendingData(Pending pending) {
        String token=random();put("apple:pending:"+token,pending);
        boolean conflict=pending.email()!=null && users.findByEmail(pending.email()).isPresent();
        return Map.of("needBind",true,"provider","apple","oauthBindToken",token,
            "canRegister","login".equals(pending.purpose()) && !conflict,"emailConflict",conflict);
    }

    @Transactional
    public User register(PendingRequest request,Context context) {
        if (!Boolean.TRUE.equals(request.acceptTerms())) throw new BusinessArgumentException(ApiResultCode.APPLE_020);
        Pending pending=take("apple:pending:",request.oauthBindToken(),Pending.class);
        if (!"login".equals(pending.purpose())) throw new BusinessArgumentException(ApiResultCode.APPLE_021);
        validateContext(pending.context(),context,false);
        if (accounts.findByProviderAndProviderUserId("apple",pending.sub()).isPresent()) throw new BusinessArgumentException(ApiResultCode.APPLE_022);
        if (pending.email()!=null && users.findByEmail(pending.email()).isPresent()) throw new BusinessArgumentException(ApiResultCode.APPLE_023);
        String username=request.username()==null || request.username().isBlank() ? "apple_"+random().substring(0,12) : request.username().trim();
        if (!validator.isValidUsername(username) || users.findByUsername(username).isPresent()) throw new BusinessArgumentException(ApiResultCode.APPLE_024);
        User user=new User(UUID.randomUUID().toString(),username,pending.email(),null);
        if (!pending.name().isBlank()) user.setRealName(pending.name());
        user=users.saveAndFlush(user);
        link(user,pending);
        UserSettings preference=new UserSettings();preference.setUserId(user.getId());preference.setMfaEnabled(false);
        preference.setDetectUnusualLogin(true);preference.setNotifySensitiveActionEmail(true);preference.setSubscribeNewsEmail(false);
        preference.setPreferredMfaMethod("totp");preference.setPreferredSensitiveMethod("apple");settings.save(preference);
        return user;
    }

    @Transactional
    public void bind(PendingRequest request,Context context) {
        requireAuthenticated(context);
        Pending pending=take("apple:pending:",request.oauthBindToken(),Pending.class);
        if (!"bind".equals(pending.purpose())) throw new BusinessArgumentException(ApiResultCode.APPLE_025);
        validateContext(pending.context(),context,true);
        User user=users.findByUuidForUpdate(context.uuid()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_011));
        if (accounts.findByProviderAndUserId("apple",user.getId()).isPresent() || accounts.findByProviderAndProviderUserId("apple",pending.sub()).isPresent())
            throw new BusinessArgumentException(ApiResultCode.APPLE_026);
        link(user,pending);
    }
    private void link(User user,Pending pending) {
        UserOauthAccount account=new UserOauthAccount();account.setUserId(user.getId());account.setProvider("apple");
        account.setProviderUserId(pending.sub());account.setIsEnabled(true);updateCredential(account,pending);accounts.saveAndFlush(account);
    }
    private void updateCredential(UserOauthAccount account,Pending pending) {
        account.setAppleClientId(pending.clientId());account.setAppleRefreshTokenEncrypted(pending.encryptedToken());
    }
    public Map<String,Object> status(Context context) {
        User user=requireAuthenticated(context);
        UserOauthAccount account=accounts.findByProviderAndUserId("apple",user.getId()).orElse(null);
        return Map.of("bound",account!=null,"enabled",account!=null && Boolean.TRUE.equals(account.getIsEnabled()),
            "canUnbind",account!=null && hasOtherLoginMethod(user));
    }
    public boolean hasOtherLoginMethod(User user) {
        return (user.getEmail()!=null && !user.getEmail().isBlank() && !isRelayEmail(user.getEmail()))
            || !passkeys.findByUserId(user.getId()).isEmpty()
            || accounts.findByUserId(user.getId()).stream().anyMatch(a -> !"apple".equals(a.getProvider()) && Boolean.TRUE.equals(a.getIsEnabled()));
    }
    public static boolean isRelayEmail(String email) {
        if (email == null) return false;
        String normalized = email.toLowerCase(Locale.ROOT);
        return normalized.endsWith("@privaterelay.appleid.com") || normalized.endsWith("@private.icloud.com");
    }
    @Transactional
    public void unbind(Context context) {
        requireAuthenticated(context);
        User user=users.findByUuidForUpdate(context.uuid()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_011));
        UserOauthAccount account=accounts.findByProviderAndUserId("apple",user.getId()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_027));
        if (!hasOtherLoginMethod(user)) throw new BusinessArgumentException(ApiResultCode.APPLE_028);
        revocations.enqueue(account);accounts.delete(account);
    }

    @Transactional
    public void notification(String payload) {
        Claims claims=apple.verifyNotification(payload);
        Object rawEvent=claims.get("events");
        if (!(rawEvent instanceof String eventJson) || eventJson.isBlank()) {
            throw new BusinessArgumentException(ApiResultCode.APPLE_029);
        }
        com.fasterxml.jackson.databind.JsonNode event;
        try {
            event=json.readTree(eventJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessArgumentException(ApiResultCode.APPLE_029);
        }
        if (event==null || !event.isObject()) throw new BusinessArgumentException(ApiResultCode.APPLE_029);
        String type=event.path("type").asText(),sub=event.path("sub").asText();
        if (!Set.of("consent-revoked","account-delete","account-deleted","email-disabled","email-enabled").contains(type) || sub.isBlank()) {
            throw new BusinessArgumentException(ApiResultCode.APPLE_030);
        }
        final String dedup;
        try {
            dedup=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new BusinessStateException(ApiResultCode.APPLE_031, ex);
        }
        String key="apple:notification:"+dedup;
        boolean acquired=false;
        try {
            // A duplicate in flight is retryable; never release another request's lease.
            acquired=Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,"processing",Duration.ofMinutes(2)));
            if (!acquired) {
                if ("done".equals(redis.opsForValue().get(key))) return;
                throw new BusinessStateException(ApiResultCode.APPLE_032);
            }
            UserOauthAccount account=accounts.findByProviderAndProviderUserId("apple",sub).orElse(null);
            if (account!=null) {
                if ("consent-revoked".equals(type) || "account-delete".equals(type) || "account-deleted".equals(type)) {
                    account.setIsEnabled(false);accounts.save(account);
                    users.findById(account.getUserId()).ifPresent(sessions::deleteAllSessionsByUser);
                } else {
                    account.setAppleEmailForwardingEnabled("email-enabled".equals(type));accounts.save(account);
                }
            }
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        // afterCommit propagates storage failures so the caller can return 503.
                        redis.opsForValue().set(key,"done",Duration.ofDays(30));
                    }
                    @Override public void afterCompletion(int status) {
                        if (status!=STATUS_COMMITTED) redis.delete(key);
                    }
                });
        } catch (RuntimeException ex) {
            if (acquired) {
                try { redis.delete(key); } catch (RuntimeException ignored) { /* Lease expiry permits retry. */ }
            }
            throw new BusinessStateException(ApiResultCode.APPLE_010, ex);
        }
    }

    private User requireAuthenticated(Context context) {
        if (context.uuid()==null || context.sessionId()==null) throw new BusinessArgumentException(ApiResultCode.UNAUTHENTICATED);
        return users.findByUuid(context.uuid()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_011));
    }
    void validateContext(Context original,Context current,boolean authenticated) {
        if (!Objects.equals(original.ip(),current.ip()) || !Objects.equals(original.userAgent(),current.userAgent())
            || (authenticated && (!Objects.equals(original.uuid(),current.uuid()) || !Objects.equals(original.sessionId(),current.sessionId()))))
            throw new BusinessArgumentException(ApiResultCode.APPLE_033);
    }
    private <T> T take(String prefix,String token,Class<T> type) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new BusinessArgumentException(ApiResultCode.APPLE_034);
        String value=redis.opsForValue().getAndDelete(prefix+token);
        if (value==null) throw new BusinessArgumentException(ApiResultCode.APPLE_035);
        try {return json.readValue(value,type);} catch (Exception ex) {throw new BusinessArgumentException(ApiResultCode.APPLE_034);}
    }
    private void put(String key,Object value) {
        try {redis.opsForValue().set(key,json.writeValueAsString(value),TTL);} catch (Exception ex) {throw new BusinessStateException(ApiResultCode.APPLE_036);}
    }
    private static String random() {byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static boolean same(String left,String right) {return left!=null && right!=null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),right.getBytes(StandardCharsets.UTF_8));}
}
