package cn.ksuser.api.service;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

@Service
public class Oauth2PlatformService {

    private static final String AUTH_CODE_PREFIX = "oauth2:auth-code:";
    private static final List<String> SUPPORTED_SCOPE_ORDER = List.of("profile", "email");
    private static final Set<String> SUPPORTED_SCOPES = Set.of("profile", "email");

    private final Oauth2ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Oauth2TokenService oauth2TokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.oauth2.max-apps-per-user:5}")
    private int maxAppsPerUser;

    @Value("${app.oauth2.authorization-code-expiration-seconds:300}")
    private long authorizationCodeExpirationSeconds;

    public Oauth2PlatformService(Oauth2ApplicationRepository applicationRepository,
                                 UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 StringRedisTemplate redisTemplate,
                                 Oauth2TokenService oauth2TokenService) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.oauth2TokenService = oauth2TokenService;
    }

    public Oauth2AppsOverviewResponse listApplications(User user) {
        List<Oauth2AppResponse> apps = applicationRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(this::toAppResponse)
            .toList();
        boolean verified = isVerifiedUser(user);
        int currentCount = apps.size();
        return new Oauth2AppsOverviewResponse(
            user.getVerificationType(),
            verified,
            maxAppsPerUser,
            currentCount,
            verified && currentCount < maxAppsPerUser,
            apps
        );
    }

    public Oauth2AppCreateResponse createApplication(User user, Oauth2AppCreateRequest request) {
        ensureVerifiedUser(user);

        if (applicationRepository.countByOwnerUserId(user.getId()) >= maxAppsPerUser) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "每位认证用户最多只能创建 " + maxAppsPerUser + " 个 OAuth2 应用");
        }

        String appName = normalizeAppName(request == null ? null : request.getAppName());
        String redirectUri = normalizeAndValidateRedirectUri(request == null ? null : request.getRedirectUri());
        String contactInfo = normalizeContactInfo(request == null ? null : request.getContactInfo());
        List<String> scopes = normalizeAppScopes(request == null ? null : request.getScopes());

        String appId = generateUniqueAppId();
        String appSecret = generateOpaqueValue("kssecret_", 36);

        Oauth2Application application = new Oauth2Application();
        application.setOwnerUserId(user.getId());
        application.setAppId(appId);
        application.setAppSecretHash(passwordEncoder.encode(appSecret));
        application.setAppName(appName);
        application.setRedirectUri(redirectUri);
        application.setContactInfo(contactInfo);
        application.setScopes(joinScopes(scopes));
        Oauth2Application saved = applicationRepository.save(application);

        return new Oauth2AppCreateResponse(
            saved.getAppId(),
            saved.getAppName(),
            saved.getRedirectUri(),
            saved.getContactInfo(),
            scopes,
            saved.getCreatedAt(),
            saved.getUpdatedAt(),
            appSecret
        );
    }

    public Oauth2AppResponse updateApplication(User user, String appId, Oauth2AppUpdateRequest request) {
        Oauth2Application application = findOwnedApplication(user, appId);

        application.setAppName(normalizeAppName(request == null ? null : request.getAppName()));
        application.setRedirectUri(normalizeAndValidateRedirectUri(request == null ? null : request.getRedirectUri()));
        application.setContactInfo(normalizeContactInfo(request == null ? null : request.getContactInfo()));

        Oauth2Application updated = applicationRepository.save(application);
        return toAppResponse(updated);
    }

    public void deleteApplication(User user, String appId) {
        Oauth2Application application = findOwnedApplication(user, appId);
        applicationRepository.delete(application);
    }

    public Oauth2AuthorizeContextResponse buildAuthorizeContext(String clientId, String redirectUri,
                                                                String responseType, String scope) {
        validateResponseType(responseType);
        Oauth2Application application = findActiveApplication(clientId, HttpStatus.BAD_REQUEST);
        validateRedirectUriMatches(application, redirectUri);
        List<String> requestedScopes = resolveRequestedScopes(application, scope);
        return new Oauth2AuthorizeContextResponse(
            application.getAppId(),
            application.getAppName(),
            application.getContactInfo(),
            application.getRedirectUri(),
            requestedScopes
        );
    }

    public Oauth2AuthorizeApproveResponse approveAuthorization(User user, Oauth2AuthorizeRequest request) {
        Oauth2AuthorizeContextResponse context = buildAuthorizeContext(
            request == null ? null : request.getClientId(),
            request == null ? null : request.getRedirectUri(),
            request == null ? null : request.getResponseType(),
            request == null ? null : request.getScope()
        );

        Oauth2Application application = findActiveApplication(context.getClientId(), HttpStatus.BAD_REQUEST);
        String code = generateOpaqueValue("kscode_", 32);
        String normalizedState = request == null ? null : normalizeOptional(request.getState());
        AuthorizationCodePayload payload = new AuthorizationCodePayload(
            user.getId(),
            application.getOwnerUserId(),
            application.getAppId(),
            context.getRedirectUri(),
            joinScopes(context.getRequestedScopes())
        );
        storeAuthorizationCode(code, payload);

        String redirectUrl = UriComponentsBuilder.fromUriString(context.getRedirectUri())
            .queryParam("code", code)
            .queryParamIfPresent("state", Optional.ofNullable(normalizedState))
            .build(true)
            .toUriString();

        return new Oauth2AuthorizeApproveResponse(redirectUrl);
    }

    public Map<String, Object> exchangeAuthorizationCode(String grantType, String code, String clientId,
                                                         String clientSecret, String redirectUri) {
        if (!"authorization_code".equals(normalizeOptional(grantType))) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                "当前仅支持 authorization_code");
        }

        Oauth2Application application = findActiveApplication(clientId, HttpStatus.UNAUTHORIZED);
        if (clientSecret == null || clientSecret.isBlank()
            || !passwordEncoder.matches(clientSecret, application.getAppSecretHash())) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_client", "AppSecret 不正确");
        }

        validateRedirectUriMatches(application, redirectUri);

        AuthorizationCodePayload payload = consumeAuthorizationCode(code);
        if (payload == null) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "授权码无效、已过期或已使用");
        }

        if (!application.getAppId().equals(payload.clientId())
            || !application.getRedirectUri().equals(payload.redirectUri())) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "授权码与当前应用或回调地址不匹配");
        }

        User user = userRepository.findById(payload.userId())
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "授权用户不存在"));

        String openid = buildOpenId(application.getAppId(), user.getUuid());
        String unionid = buildUnionId(payload.ownerUserId(), user.getUuid());
        String scope = payload.scope() == null ? "" : payload.scope();
        String accessToken = oauth2TokenService.generateAccessToken(
            application.getAppId(),
            payload.ownerUserId(),
            user.getId(),
            user.getUuid(),
            scope,
            openid,
            unionid
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", oauth2TokenService.getAccessTokenExpiresInSeconds());
        response.put("scope", scope);
        response.put("openid", openid);
        response.put("unionid", unionid);
        return response;
    }

    public Map<String, Object> buildUserInfo(String accessToken) {
        Oauth2TokenService.ParsedOauth2AccessToken parsed = oauth2TokenService.parse(accessToken);
        if (parsed == null || parsed.clientId() == null || parsed.userId() == null || parsed.ownerUserId() == null) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "Access Token 无效或已过期");
        }

        Oauth2Application application = findActiveApplication(parsed.clientId(), HttpStatus.UNAUTHORIZED);
        User user = userRepository.findById(parsed.userId())
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "Access Token 对应用户不存在"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("openid", nonBlankOrDefault(parsed.openid(), buildOpenId(application.getAppId(), user.getUuid())));
        response.put("unionid", nonBlankOrDefault(parsed.unionid(), buildUnionId(parsed.ownerUserId(), user.getUuid())));

        Set<String> scopes = new LinkedHashSet<>(parseScopes(parsed.scope()));
        if (scopes.contains("profile")) {
            response.put("nickname", user.getUsername());
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
                response.put("avatar_url", user.getAvatarUrl());
            }
        }
        if (scopes.contains("email") && user.getEmail() != null && !user.getEmail().isBlank()) {
            response.put("email", user.getEmail());
        }

        return response;
    }

    public boolean isVerifiedUser(User user) {
        String verificationType = user == null ? "none" : user.getVerificationType();
        return "personal".equalsIgnoreCase(verificationType) || "enterprise".equalsIgnoreCase(verificationType);
    }

    private void ensureVerifiedUser(User user) {
        if (!isVerifiedUser(user)) {
            throw new Oauth2Exception(HttpStatus.FORBIDDEN, "unauthorized_client",
                "仅个人认证或企业认证用户可以创建 OAuth2 应用");
        }
    }

    private Oauth2Application findOwnedApplication(User user, String appId) {
        String normalizedAppId = normalizeRequired(appId, "AppID 不能为空");
        Oauth2Application application = applicationRepository.findByAppId(normalizedAppId)
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.NOT_FOUND, "invalid_client", "应用不存在"));
        if (!Objects.equals(application.getOwnerUserId(), user.getId())) {
            throw new Oauth2Exception(HttpStatus.NOT_FOUND, "invalid_client", "应用不存在");
        }
        return application;
    }

    private Oauth2Application findActiveApplication(String clientId, HttpStatus status) {
        String normalizedClientId = normalizeRequired(clientId, "AppID 不能为空");
        return applicationRepository.findByAppId(normalizedClientId)
            .filter(app -> Boolean.TRUE.equals(app.getIsActive()))
            .orElseThrow(() -> new Oauth2Exception(status, "invalid_client", "AppID 不存在或已停用"));
    }

    private void validateResponseType(String responseType) {
        if (!"code".equals(normalizeOptional(responseType))) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "unsupported_response_type",
                "仅支持 response_type=code");
        }
    }

    private void validateRedirectUriMatches(Oauth2Application application, String redirectUri) {
        String normalizedRedirectUri = normalizeAndValidateRedirectUri(redirectUri);
        if (!application.getRedirectUri().equals(normalizedRedirectUri)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "redirect_uri 与应用登记信息不一致");
        }
    }

    private String normalizeAppName(String appName) {
        String normalized = normalizeRequired(appName, "应用名称不能为空");
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "应用名称长度需在 2-100 个字符之间");
        }
        return normalized;
    }

    private String normalizeContactInfo(String contactInfo) {
        String normalized = normalizeRequired(contactInfo, "联系方式不能为空");
        if (normalized.length() < 3 || normalized.length() > 120) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "联系方式长度需在 3-120 个字符之间");
        }
        return normalized;
    }

    private String normalizeAndValidateRedirectUri(String redirectUri) {
        String normalized = normalizeRequired(redirectUri, "回调地址不能为空");

        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

            boolean httpsAllowed = "https".equals(scheme) && !host.isBlank();
            boolean localhostHttpAllowed = "http".equals(scheme) && "localhost".equals(host);
            if (!httpsAllowed && !localhostHttpAllowed) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                    "回调地址仅支持 https:// 或 http://localhost");
            }
            if (uri.getFragment() != null) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "回调地址不允许包含 URL fragment");
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException ex) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "回调地址格式不正确");
        }
    }

    private List<String> normalizeAppScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }

        StringBuilder builder = new StringBuilder();
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(scope.trim());
        }

        return orderScopes(parseScopes(builder.toString()));
    }

    private List<String> resolveRequestedScopes(Oauth2Application application, String requestedScope) {
        List<String> allowedScopes = orderScopes(parseScopes(application.getScopes()));
        if (requestedScope == null || requestedScope.isBlank()) {
            return allowedScopes;
        }

        List<String> requestedScopes = orderScopes(parseScopes(requestedScope));
        if (!allowedScopes.containsAll(requestedScopes)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_scope",
                "请求的 scope 超出了应用已登记的权限范围");
        }
        return requestedScopes;
    }

    private List<String> parseScopes(String scopeValue) {
        if (scopeValue == null || scopeValue.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        String[] rawParts = scopeValue.trim().split("[\\s,]+");
        for (String rawPart : rawParts) {
            String scope = rawPart == null ? "" : rawPart.trim().toLowerCase(Locale.ROOT);
            if (scope.isBlank()) {
                continue;
            }
            if (!SUPPORTED_SCOPES.contains(scope)) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_scope",
                    "暂仅支持 profile、email 两种 scope");
            }
            scopes.add(scope);
        }

        return orderScopes(scopes);
    }

    private List<String> orderScopes(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }

        List<String> ordered = new ArrayList<>();
        for (String supportedScope : SUPPORTED_SCOPE_ORDER) {
            if (scopes.contains(supportedScope)) {
                ordered.add(supportedScope);
            }
        }
        return ordered;
    }

    private String joinScopes(List<String> scopes) {
        return scopes == null || scopes.isEmpty() ? "" : String.join(" ", scopes);
    }

    private Oauth2AppResponse toAppResponse(Oauth2Application application) {
        return new Oauth2AppResponse(
            application.getAppId(),
            application.getAppName(),
            application.getRedirectUri(),
            application.getContactInfo(),
            parseScopes(application.getScopes()),
            application.getCreatedAt(),
            application.getUpdatedAt()
        );
    }

    private String generateUniqueAppId() {
        for (int i = 0; i < 10; i++) {
            String candidate = generateOpaqueValue("ksapp_", 18);
            if (!applicationRepository.existsByAppId(candidate)) {
                return candidate;
            }
        }
        throw new Oauth2Exception(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "生成 AppID 失败，请稍后重试");
    }

    private String generateOpaqueValue(String prefix, int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void storeAuthorizationCode(String code, AuthorizationCodePayload payload) {
        try {
            redisTemplate.opsForValue().set(
                AUTH_CODE_PREFIX + code,
                objectMapper.writeValueAsString(payload),
                Duration.ofSeconds(authorizationCodeExpirationSeconds)
            );
        } catch (JsonProcessingException ex) {
            throw new Oauth2Exception(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "生成授权码失败");
        }
    }

    private AuthorizationCodePayload consumeAuthorizationCode(String code) {
        String normalizedCode = normalizeOptional(code);
        if (normalizedCode == null) {
            return null;
        }

        String raw = redisTemplate.opsForValue().getAndDelete(AUTH_CODE_PREFIX + normalizedCode);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(raw, AuthorizationCodePayload.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String buildOpenId(String appId, String userUuid) {
        return buildSignedIdentifier("oid_", "openid:" + appId + ":" + userUuid);
    }

    private String buildUnionId(Long ownerUserId, String userUuid) {
        return buildSignedIdentifier("uid_", "unionid:" + ownerUserId + ":" + userUuid);
    }

    private String buildSignedIdentifier(String prefix, String source) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return prefix + encoded.substring(0, Math.min(encoded.length(), 40));
        } catch (Exception ex) {
            throw new Oauth2Exception(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "生成用户标识失败");
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record AuthorizationCodePayload(Long userId,
                                            Long ownerUserId,
                                            String clientId,
                                            String redirectUri,
                                            String scope) {
    }
}
