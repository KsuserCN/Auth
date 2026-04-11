package cn.ksuser.api.service;

import cn.ksuser.api.dto.Oauth2AppCreateRequest;
import cn.ksuser.api.dto.Oauth2AppCreateResponse;
import cn.ksuser.api.dto.Oauth2AppResponse;
import cn.ksuser.api.dto.Oauth2AppUpdateRequest;
import cn.ksuser.api.dto.Oauth2AuthorizedAppResponse;
import cn.ksuser.api.dto.Oauth2AppsOverviewResponse;
import cn.ksuser.api.dto.Oauth2AuthorizeApproveResponse;
import cn.ksuser.api.dto.Oauth2AuthorizeContextResponse;
import cn.ksuser.api.dto.Oauth2AuthorizeRequest;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauth2Authorization;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.UserOauth2AuthorizationRepository;
import cn.ksuser.api.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class Oauth2PlatformService {

    private static final String AUTH_CODE_PREFIX = "oauth2:auth-code:";
    private static final List<String> SUPPORTED_SCOPE_ORDER = List.of("profile", "email");
    private static final Set<String> SUPPORTED_SCOPES = Set.of("profile", "email");

    private final Oauth2ApplicationRepository applicationRepository;
    private final UserOauth2AuthorizationRepository authorizationRepository;
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
                                 UserOauth2AuthorizationRepository authorizationRepository,
                                 UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 StringRedisTemplate redisTemplate,
                                 Oauth2TokenService oauth2TokenService) {
        this.applicationRepository = applicationRepository;
        this.authorizationRepository = authorizationRepository;
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
        boolean verified = isOauthCreator(user);
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
        ensureOauthCreator(user);

        if (applicationRepository.countByOwnerUserId(user.getId()) >= maxAppsPerUser) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "每位认证用户最多只能创建 " + maxAppsPerUser + " 个 OAuth2 应用");
        }

        String appName = normalizeAppName(request == null ? null : request.getAppName());
        String redirectUri = normalizeAndValidateRedirectUri(request == null ? null : request.getRedirectUri());
        String contactInfo = normalizeContactInfo(request == null ? null : request.getContactInfo());
        List<String> scopes = normalizeAppScopes(request == null ? null : request.getScopes());

        String appId = generateUniqueAppId();
        String appSecret = generateOpaqueValue("ksapp_secret_", 36);

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
            saved.getLogoUrl(),
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
        return toAppResponse(applicationRepository.save(application));
    }

    public void deleteApplication(User user, String appId) {
        applicationRepository.delete(findOwnedApplication(user, appId));
    }

    public Oauth2AppResponse updateApplicationLogo(User user, String appId, String logoUrl) {
        Oauth2Application application = findOwnedApplication(user, appId);
        application.setLogoUrl(normalizeOptional(logoUrl));
        return toAppResponse(applicationRepository.save(application));
    }

    public Oauth2AuthorizeContextResponse buildAuthorizeContext(User user, String clientId, String redirectUri,
                                                                String responseType, String scope) {
        validateResponseType(responseType);
        Oauth2Application application = findActiveApplication(clientId, HttpStatus.BAD_REQUEST);
        validateRedirectUriMatches(application, redirectUri);
        List<String> requestedScopes = resolveRequestedScopes(application, scope);
        return new Oauth2AuthorizeContextResponse(
            application.getAppId(),
            application.getAppName(),
            application.getLogoUrl(),
            application.getContactInfo(),
            application.getRedirectUri(),
            requestedScopes,
            hasExistingAuthorization(user, application.getAppId(), requestedScopes)
        );
    }

    public Oauth2AuthorizeApproveResponse approveAuthorization(User user, Oauth2AuthorizeRequest request) {
        Oauth2AuthorizeContextResponse context = buildAuthorizeContext(
            user,
            request == null ? null : request.getClientId(),
            request == null ? null : request.getRedirectUri(),
            request == null ? null : request.getResponseType(),
            request == null ? null : request.getScope()
        );

        Oauth2Application application = findActiveApplication(context.getClientId(), HttpStatus.BAD_REQUEST);
        recordAuthorization(user, application, context.getRequestedScopes(), context.getRedirectUri());
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

    public List<Oauth2AuthorizedAppResponse> listAuthorizations(User user) {
        return authorizationRepository.findAllByUserIdOrderByLastAuthorizedAtDesc(user.getId()).stream()
            .map(this::toAuthorizedAppResponse)
            .toList();
    }

    @Transactional
    public void revokeAuthorization(User user, String appId) {
        String normalizedAppId = normalizeRequired(appId, "AppID 不能为空");
        authorizationRepository.deleteByUserIdAndAppId(user.getId(), normalizedAppId);
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
        response.put("openid", parsed.openid() == null || parsed.openid().isBlank()
            ? buildOpenId(application.getAppId(), user.getUuid()) : parsed.openid());
        response.put("unionid", parsed.unionid() == null || parsed.unionid().isBlank()
            ? buildUnionId(parsed.ownerUserId(), user.getUuid()) : parsed.unionid());

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

    public boolean isOauthCreator(User user) {
        String verificationType = user == null ? "none" : user.getVerificationType();
        return "personal".equalsIgnoreCase(verificationType)
            || "enterprise".equalsIgnoreCase(verificationType)
            || "admin".equalsIgnoreCase(verificationType);
    }

    private void ensureOauthCreator(User user) {
        if (!isOauthCreator(user)) {
            throw new Oauth2Exception(HttpStatus.FORBIDDEN, "unauthorized_client",
                "仅个人认证、企业认证或管理员账号可以创建 OAuth2 应用");
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
        for (String scope : Oauth2ScopeUtil.parseScopeList(scopeValue)) {
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
            application.getLogoUrl(),
            application.getRedirectUri(),
            application.getContactInfo(),
            parseScopes(application.getScopes()),
            application.getCreatedAt(),
            application.getUpdatedAt()
        );
    }

    private Oauth2AuthorizedAppResponse toAuthorizedAppResponse(UserOauth2Authorization authorization) {
        Oauth2Application application = applicationRepository.findByAppId(authorization.getAppId()).orElse(null);
        String appName = application != null ? application.getAppName() : authorization.getAppName();
        String logoUrl = application != null ? application.getLogoUrl() : authorization.getLogoUrl();
        String contactInfo = application != null ? application.getContactInfo() : authorization.getContactInfo();
        String redirectUri = application != null ? application.getRedirectUri() : authorization.getRedirectUri();
        return new Oauth2AuthorizedAppResponse(
            authorization.getAppId(),
            appName,
            logoUrl,
            contactInfo,
            redirectUri,
            parseScopes(authorization.getScopes()),
            authorization.getAuthorizedAt(),
            authorization.getLastAuthorizedAt()
        );
    }

    private boolean hasExistingAuthorization(User user, String appId, List<String> requestedScopes) {
        if (user == null) {
            return false;
        }
        return authorizationRepository.findByUserIdAndAppId(user.getId(), appId)
            .map(record -> new LinkedHashSet<>(parseScopes(record.getScopes())))
            .map(grantedScopes -> grantedScopes.containsAll(requestedScopes))
            .orElse(false);
    }

    private void recordAuthorization(User user, Oauth2Application application, List<String> requestedScopes,
                                     String redirectUri) {
        UserOauth2Authorization record = authorizationRepository.findByUserIdAndAppId(user.getId(), application.getAppId())
            .orElseGet(UserOauth2Authorization::new);
        LocalDateTime now = LocalDateTime.now();
        if (record.getId() == null) {
            record.setUserId(user.getId());
            record.setAppId(application.getAppId());
            record.setAuthorizedAt(now);
        }
        LinkedHashSet<String> grantedScopes = new LinkedHashSet<>(parseScopes(record.getScopes()));
        grantedScopes.addAll(requestedScopes);
        record.setAppName(application.getAppName());
        record.setLogoUrl(application.getLogoUrl());
        record.setContactInfo(application.getContactInfo());
        record.setRedirectUri(redirectUri);
        record.setScopes(joinScopes(orderScopes(grantedScopes)));
        record.setLastAuthorizedAt(now);
        authorizationRepository.save(record);
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

    private record AuthorizationCodePayload(Long userId,
                                            Long ownerUserId,
                                            String clientId,
                                            String redirectUri,
                                            String scope) {
    }
}
