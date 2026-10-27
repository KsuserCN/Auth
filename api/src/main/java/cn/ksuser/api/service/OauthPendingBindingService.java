package cn.ksuser.api.service;

import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Versioned, fixed-lifetime authorization from a completed OAuth provider callback. */
@Service
public class OauthPendingBindingService {
    public static final long TTL_SECONDS = 900;
    private static final String PREFIX = "oauth:pending-bind:v2:";
    private static final Set<String> PROVIDERS = Set.of("qq", "github", "google", "microsoft");
    static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) ~= 0 or redis.call('EXISTS', KEYS[2]) ~= 0 then return 0 end
        redis.call('HSET', KEYS[1], 'version', '2', 'state', 'READY',
          'provider', ARGV[1], 'providerUserId', ARGV[2], 'unionId', ARGV[3])
        redis.call('EXPIRE', KEYS[1], ARGV[4])
        redis.call('SET', KEYS[2], '0', 'EX', ARGV[4])
        return 1
        """, Long.class);
    static final DefaultRedisScript<String> RESOLVE = new DefaultRedisScript<>("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return '' end
        if redis.call('HGET', KEYS[1], 'version') ~= '2'
          or redis.call('HGET', KEYS[1], 'state') ~= 'READY'
          or redis.call('HGET', KEYS[1], 'provider') ~= ARGV[1] then return '' end
        return cjson.encode({provider=ARGV[1], providerUserId=redis.call('HGET', KEYS[1], 'providerUserId'),
          unionId=redis.call('HGET', KEYS[1], 'unionId')})
        """, String.class);
    static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return '' end
        if redis.call('HGET', KEYS[1], 'version') ~= '2'
          or redis.call('HGET', KEYS[1], 'state') ~= 'READY'
          or redis.call('HGET', KEYS[1], 'provider') ~= ARGV[1] then return '' end
        redis.call('HSET', KEYS[1], 'state', 'CLAIMED', 'owner', ARGV[2], 'userId', ARGV[3])
        return cjson.encode({provider=ARGV[1], providerUserId=redis.call('HGET', KEYS[1], 'providerUserId'),
          unionId=redis.call('HGET', KEYS[1], 'unionId')})
        """, String.class);
    static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        if redis.call('PTTL', KEYS[1]) <= 0 then return 0 end
        if redis.call('HGET', KEYS[1], 'version') ~= '2'
          or redis.call('HGET', KEYS[1], 'state') ~= 'CLAIMED'
          or redis.call('HGET', KEYS[1], 'owner') ~= ARGV[1]
          or redis.call('HGET', KEYS[1], 'userId') ~= ARGV[2]
          or redis.call('HGET', KEYS[1], 'provider') ~= ARGV[3]
          or redis.call('HGET', KEYS[1], 'providerUserId') ~= ARGV[4]
          or redis.call('HGET', KEYS[1], 'unionId') ~= ARGV[5] then return 0 end
        redis.call('HSET', KEYS[1], 'state', 'CONSUMED')
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public OauthPendingBindingService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(String provider, String providerUserId, String unionId) {
        Identity identity = identity(provider, providerUserId, unionId);
        String token = randomToken();
        try {
            Long created = redis.execute(CREATE, List.of(pendingKey(token), quotaKey(token)), identity.provider(),
                identity.providerUserId(), nullToEmpty(identity.unionId()), Long.toString(TTL_SECONDS));
            if (!Long.valueOf(1).equals(created)) throw unavailable();
            return token;
        } catch (DataAccessException ex) { throw unavailable(); }
    }

    public Identity resolve(String provider, String token) {
        String normalized = provider(provider);
        validateToken(token);
        try {
            return parse(redis.execute(RESOLVE, List.of(pendingKey(token)), normalized));
        } catch (DataAccessException ex) { throw unavailable(); }
    }

    /** The quota is initialized with the same deadline and can only be incremented, never renewed. */
    public String quotaKey(String token) {
        validateToken(token);
        return PREFIX + "password-attempts:" + digest(token);
    }

    public Claim claim(String provider, String token, Long userId) {
        String normalized = provider(provider);
        validateToken(token);
        if (userId == null || userId <= 0) throw invalid();
        String owner = randomToken();
        try {
            Identity identity = parse(redis.execute(CLAIM, List.of(pendingKey(token)), normalized, owner, userId.toString()));
            return new Claim(token, owner, userId, identity);
        } catch (DataAccessException ex) { throw unavailable(); }
    }

    /** Consumption precedes the database transaction: failed storage requires a new provider callback. */
    public void consumeClaim(Claim claim) {
        if (claim == null || claim.identity() == null || claim.userId() == null || claim.owner() == null
            || !claim.owner().matches("[A-Za-z0-9_-]{32}")) throw invalid();
        validateToken(claim.token());
        Identity identity = identity(claim.identity().provider(), claim.identity().providerUserId(), claim.identity().unionId());
        try {
            Long consumed = redis.execute(CONSUME, List.of(pendingKey(claim.token())), claim.owner(), claim.userId().toString(),
                identity.provider(), identity.providerUserId(), nullToEmpty(identity.unionId()));
            if (consumed == null) throw unavailable();
            if (consumed != 1) throw invalid();
        } catch (DataAccessException ex) { throw unavailable(); }
    }

    private Identity parse(String raw) {
        if (raw == null) throw unavailable();
        if (raw.isBlank()) throw invalid();
        try {
            JsonNode node = mapper.readTree(raw);
            return identity(node.path("provider").asText(null), node.path("providerUserId").asText(null),
                node.path("unionId").asText(null));
        } catch (BindingFailure ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private static Identity identity(String provider, String providerUserId, String unionId) {
        String normalized = provider(provider);
        if (providerUserId == null || providerUserId.isBlank() || providerUserId.length() > 128
            || unionId != null && unionId.length() > 128) throw invalid();
        String union = unionId == null || unionId.isBlank() ? null : unionId;
        if ("qq".equals(normalized) && union == null)
            throw new BindingFailure(HttpStatus.BAD_REQUEST, ApiResultCode.OAUTH_090);
        return new Identity(normalized, providerUserId, union);
    }

    private static String provider(String provider) {
        String value = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(value)) throw new BindingFailure(HttpStatus.BAD_REQUEST, ApiResultCode.OAUTH_086);
        return value;
    }
    private String randomToken() { byte[] bytes = new byte[24]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String pendingKey(String token) { return PREFIX + "identity:" + digest(token); }
    private static void validateToken(String token) { if (token == null || !token.matches("[A-Za-z0-9_-]{32}")) throw invalid(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static BindingFailure invalid() { return new BindingFailure(HttpStatus.BAD_REQUEST, ApiResultCode.OAUTH_087); }
    private static BindingFailure unavailable() { return new BindingFailure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR); }

    public record Identity(String provider, String providerUserId, String unionId) {}
    public record Claim(String token, String owner, Long userId, Identity identity) {}
    public static class BindingFailure extends RuntimeException {
        private final HttpStatus status;
        private final ApiResult result;
        public BindingFailure(HttpStatus status, ApiResultCode resultCode) {
            this(status, ApiResult.of(resultCode));
        }
        public BindingFailure(HttpStatus status, ApiResult result) {
            super(result.errorCode().name()); this.status = status; this.result = result;
        }
        public HttpStatus status() { return status; }
        public ApiResultCode resultCode() { return result.errorCode(); }
        public ApiResult result() { return result; }
    }
}
