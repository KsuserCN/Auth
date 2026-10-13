package cn.ksuser.api.service;

import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.dto.QrChallengeInitResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** A mobile confirmation for the original official OIDC request; never a Passport session transfer. */
@Service
public class OfficialQrAuthorizationService {
    private static final String PREFIX = "official:qr:";
    private static final Duration TTL = Duration.ofSeconds(QrChallengeService.DEFAULT_TTL_SECONDS);
    private final OfficialAuthorizationService official;
    private final QrChallengeService qr;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public OfficialQrAuthorizationService(OfficialAuthorizationService official, QrChallengeService qr,
                                           StringRedisTemplate redis, ObjectMapper json) {
        this.official = official; this.qr = qr; this.redis = redis; this.json = json;
    }

    public record Created(QrChallengeInitResponse challenge, OfficialAuthorizationService.AuthorizeContext application,
                          String redirectUri) {}
    public record BrowserRequest(String challengeId, String pollToken) {}
    public record Status(String status, long expiresInSeconds) {}
    public record Flow(OfficialAuthorizeRequest authorization, String pollToken, long expiresAt) {}

    public Created create(OfficialAuthorizeRequest input, String ip, String userAgent) {
        if (input == null) throw invalid();
        // The phone decides approval. Neither browser credentials nor an input approval flag are inherited.
        OfficialAuthorizeRequest authorization = decision(input, null);
        var context = official.buildAuthorizeContext(null, authorization);
        var challenge = qr.createChallenge(QrChallengeService.ChallengeType.OFFICIAL_AUTHORIZATION,
            null, null, "official", ip, userAgent);
        Flow flow = new Flow(authorization, challenge.getPollToken(), System.currentTimeMillis() + TTL.toMillis());
        store(key(challenge.getChallengeId()), flow, TTL);
        redis.opsForValue().set(key(challenge.getChallengeId()) + ":approval", "pending", TTL);
        return new Created(new QrChallengeInitResponse(challenge.getChallengeId(), challenge.getPollToken(),
            QrChallengeService.QR_TEXT_PREFIX + challenge.getApproveCode(), TTL.toSeconds()), context, authorization.redirectUri());
    }

    public OfficialAuthorizationService.AuthorizeContext preview(QrChallengeService.QrChallengePayload challenge) {
        return official.buildAuthorizeContext(null, flow(challenge).authorization());
    }

    public String redirectUri(QrChallengeService.QrChallengePayload challenge) {
        return flow(challenge).authorization().redirectUri();
    }

    public void decide(QrChallengeService.QrChallengePayload challenge, User user, boolean approved) {
        Flow flow = flow(challenge);
        // Recheck current role, enabled application, callback and scopes before claiming this scan.
        official.buildAuthorizeContext(user, flow.authorization());
        if (redis.opsForValue().getAndDelete(key(challenge.getChallengeId()) + ":approval") == null) throw invalid();
        String redirect = official.approveAuthorization(user, decision(flow.authorization(), approved)).get("redirectUrl");
        if (redirect == null) throw invalid();
        store(key(challenge.getChallengeId()) + ":result", Map.of("redirectUrl", redirect),
            Duration.ofMillis(Math.max(1, flow.expiresAt() - System.currentTimeMillis())));
        // General /auth/qr/status may expose only confirmation state, never this authorization code.
        if (approved) qr.approveChallenge(challenge.getChallengeId(), user.getId(), Map.of("verified", true));
        else qr.rejectChallenge(challenge.getChallengeId(), user.getId(), Map.of("verified", false));
    }

    public Status status(BrowserRequest input) {
        Flow flow = browserFlow(input);
        if (flow == null) return new Status("expired", 0);
        var challenge = qr.getByChallengeId(input.challengeId());
        if (challenge == null) return new Status("expired", 0);
        return new Status(challenge.getStatus(), Math.max(0, (flow.expiresAt() - System.currentTimeMillis() + 999) / 1000));
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> consume(BrowserRequest input) {
        Flow flow = browserFlow(input);
        if (flow == null) throw expired();
        var challenge = qr.getByChallengeId(input.challengeId());
        if (challenge == null || !List.of("approved", "rejected").contains(challenge.getStatus())) throw invalid();
        // The result secret is kept out of the QR and all mobile responses, and is consumed atomically.
        Map<String, String> result = decode(redis.opsForValue().getAndDelete(key(input.challengeId()) + ":result"), Map.class);
        if (result == null) throw invalid();
        delete(input.challengeId());
        return result;
    }

    public void cancel(BrowserRequest input) {
        if (browserFlow(input) != null) delete(input.challengeId());
    }

    private Flow flow(QrChallengeService.QrChallengePayload challenge) {
        if (challenge == null || !QrChallengeService.ChallengeType.OFFICIAL_AUTHORIZATION.value().equals(challenge.getType())
            || !"pending".equals(challenge.getStatus())) throw invalid();
        Flow flow = decode(redis.opsForValue().get(key(challenge.getChallengeId())), Flow.class);
        if (flow == null || flow.expiresAt() <= System.currentTimeMillis()) throw expired();
        return flow;
    }

    private Flow browserFlow(BrowserRequest input) {
        if (input == null || input.challengeId() == null || !input.challengeId().matches("[A-Za-z0-9-]{1,64}")
            || input.pollToken() == null || !input.pollToken().matches("[A-Za-z0-9_-]{32}")) throw invalid();
        Flow flow = decode(redis.opsForValue().get(key(input.challengeId())), Flow.class);
        if (flow == null || flow.expiresAt() <= System.currentTimeMillis()) return null;
        if (!MessageDigest.isEqual(flow.pollToken().getBytes(StandardCharsets.UTF_8), input.pollToken().getBytes(StandardCharsets.UTF_8))) throw invalid();
        return flow;
    }

    private void delete(String id) {
        redis.delete(List.of(key(id), key(id) + ":approval", key(id) + ":result"));
    }
    private static String key(String id) { return PREFIX + id; }
    private static OfficialAuthorizeRequest decision(OfficialAuthorizeRequest a, Boolean approved) {
        return new OfficialAuthorizeRequest(a.clientId(), a.redirectUri(), a.responseType(), a.scope(), a.state(),
            a.nonce(), a.codeChallenge(), a.codeChallengeMethod(), approved);
    }
    private void store(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, json.writeValueAsString(value), ttl); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private <T> T decode(String value, Class<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Oauth2Exception invalid() { return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.OFFICIAL_REQUEST_INVALID); }
    private static Oauth2Exception expired() { return new Oauth2Exception(HttpStatus.GONE, "invalid_request", ApiResultCode.AUTH_086); }
}
