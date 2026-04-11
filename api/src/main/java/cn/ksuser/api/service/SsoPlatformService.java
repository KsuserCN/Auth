package cn.ksuser.api.service;

import cn.ksuser.api.dto.SsoAuthorizeApproveResponse;
import cn.ksuser.api.dto.SsoAuthorizeContextResponse;
import cn.ksuser.api.dto.SsoAuthorizeRequest;
import cn.ksuser.api.dto.SsoAuthorizedClientResponse;
import cn.ksuser.api.dto.SsoClientCreateRequest;
import cn.ksuser.api.dto.SsoClientCreateResponse;
import cn.ksuser.api.dto.SsoClientResponse;
import cn.ksuser.api.dto.SsoClientUpdateRequest;
import cn.ksuser.api.dto.SsoClientsOverviewResponse;
import cn.ksuser.api.entity.OidcClient;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSsoAuthorization;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.OidcClientRepository;
import cn.ksuser.api.repository.UserSsoAuthorizationRepository;
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
public class SsoPlatformService {

    private static final String AUTH_CODE_PREFIX = "sso:auth-code:";
    private static final List<String> SUPPORTED_SCOPE_ORDER = List.of("openid", "profile", "email");
    private static final Set<String> SUPPORTED_SCOPES = Set.of("openid", "profile", "email");

    private final OidcClientRepository oidcClientRepository;
    private final UserSsoAuthorizationRepository authorizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final SsoTokenService ssoTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.sso.max-clients:20}")
    private int maxClients;

    @Value("${app.sso.authorization-code-expiration-seconds:300}")
    private long authorizationCodeExpirationSeconds;

    @Value("${app.sso.issuer:http://localhost:8000}")
    private String issuer;

    @Value("${app.sso.authorization-endpoint:http://localhost:5173/sso/authorize}")
    private String authorizationEndpoint;

    public SsoPlatformService(OidcClientRepository oidcClientRepository,
                              UserSsoAuthorizationRepository authorizationRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              StringRedisTemplate redisTemplate,
                              SsoTokenService ssoTokenService) {
        this.oidcClientRepository = oidcClientRepository;
        this.authorizationRepository = authorizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.ssoTokenService = ssoTokenService;
    }

    public SsoClientsOverviewResponse listClients(User user) {
        boolean admin = isAdminUser(user);
        List<SsoClientResponse> clients = admin
            ? oidcClientRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toClientResponse).toList()
            : List.of();
        return new SsoClientsOverviewResponse(
            user == null ? "none" : user.getVerificationType(),
            admin,
            maxClients,
            clients.size(),
            admin && clients.size() < maxClients,
            clients
        );
    }

    public SsoClientCreateResponse createClient(User user, SsoClientCreateRequest request) {
        ensureAdminUser(user);
        long currentCount = oidcClientRepository.count();
        if (currentCount >= maxClients) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "当前环境最多只能创建 " + maxClients + " 个 SSO 内部服务应用");
        }

        String clientName = normalizeClientName(request == null ? null : request.getClientName());
        List<String> redirectUris = normalizeAndValidateRedirectUris(request == null ? null : request.getRedirectUris());
        List<String> postLogoutRedirectUris = normalizeAndValidateOptionalRedirectUris(
            request == null ? null : request.getPostLogoutRedirectUris()
        );
        List<String> scopes = normalizeSsoScopes(request == null ? null : request.getScopes());
        List<String> audiences = normalizeAudiences(request == null ? null : request.getAudiences());

        String clientId = generateUniqueClientId();
        String clientSecret = generateOpaqueValue("ssosk_", 36);

        OidcClient client = new OidcClient();
        client.setClientId(clientId);
        client.setClientSecretHash(passwordEncoder.encode(clientSecret));
        client.setClientName(clientName);
        client.setRedirectUris(joinValues(redirectUris));
        client.setPostLogoutRedirectUris(joinValues(postLogoutRedirectUris));
        client.setScopes(joinValues(scopes));
        client.setAudiences(joinValues(audiences));
        client.setRequirePkce(request == null || request.getRequirePkce() == null || request.getRequirePkce());
        OidcClient saved = oidcClientRepository.save(client);

        return new SsoClientCreateResponse(
            saved.getClientId(),
            saved.getClientName(),
            saved.getLogoUrl(),
            redirectUris,
            postLogoutRedirectUris,
            scopes,
            audiences,
            Boolean.TRUE.equals(saved.getRequirePkce()),
            saved.getCreatedAt(),
            saved.getUpdatedAt(),
            clientSecret
        );
    }

    public SsoClientResponse updateClient(User user, String clientId, SsoClientUpdateRequest request) {
        ensureAdminUser(user);
        OidcClient client = findManagedClient(clientId);
        client.setClientName(normalizeClientName(request == null ? null : request.getClientName()));
        client.setRedirectUris(joinValues(normalizeAndValidateRedirectUris(request == null ? null : request.getRedirectUris())));
        client.setPostLogoutRedirectUris(joinValues(normalizeAndValidateOptionalRedirectUris(
            request == null ? null : request.getPostLogoutRedirectUris()
        )));
        client.setScopes(joinValues(normalizeSsoScopes(request == null ? null : request.getScopes())));
        client.setAudiences(joinValues(normalizeAudiences(request == null ? null : request.getAudiences())));
        client.setRequirePkce(request == null || request.getRequirePkce() == null || request.getRequirePkce());
        return toClientResponse(oidcClientRepository.save(client));
    }

    public void deleteClient(User user, String clientId) {
        ensureAdminUser(user);
        oidcClientRepository.delete(findManagedClient(clientId));
    }

    public SsoClientResponse updateClientLogo(User user, String clientId, String logoUrl) {
        ensureAdminUser(user);
        OidcClient client = findManagedClient(clientId);
        client.setLogoUrl(normalizeOptional(logoUrl));
        return toClientResponse(oidcClientRepository.save(client));
    }

    public SsoAuthorizeContextResponse buildAuthorizeContext(User user, String clientId, String redirectUri,
                                                             String responseType, String scope,
                                                             String nonce, String codeChallenge,
                                                             String codeChallengeMethod) {
        validateResponseType(responseType);
        validateNonce(nonce);
        OidcClient client = findActiveClient(clientId, HttpStatus.BAD_REQUEST);
        validateRedirectUriMatches(client, redirectUri);
        validatePkceParameters(client, codeChallenge, codeChallengeMethod);
        List<String> requestedScopes = resolveRequestedScopes(client, scope);
        return new SsoAuthorizeContextResponse(
            client.getClientId(),
            client.getClientName(),
            client.getLogoUrl(),
            normalizeAndValidateRedirectUri(redirectUri),
            requestedScopes,
            hasExistingAuthorization(user, client.getClientId(), requestedScopes)
        );
    }

    public SsoAuthorizeApproveResponse approveAuthorization(User user, SsoAuthorizeRequest request) {
        SsoAuthorizeContextResponse context = buildAuthorizeContext(
            user,
            request == null ? null : request.getClientId(),
            request == null ? null : request.getRedirectUri(),
            request == null ? null : request.getResponseType(),
            request == null ? null : request.getScope(),
            request == null ? null : request.getNonce(),
            request == null ? null : request.getCodeChallenge(),
            request == null ? null : request.getCodeChallengeMethod()
        );

        String code = generateOpaqueValue("ssocode_", 32);
        String normalizedState = normalizeOptional(request == null ? null : request.getState());
        OidcClient client = findActiveClient(context.getClientId(), HttpStatus.BAD_REQUEST);
        recordAuthorization(user, client, context.getRequestedScopes(), context.getRedirectUri());
        AuthorizationCodePayload payload = new AuthorizationCodePayload(
            user.getId(),
            client.getClientId(),
            context.getRedirectUri(),
            joinValues(resolveRequestedScopes(client, request == null ? null : request.getScope())),
            normalizeOptional(request == null ? null : request.getNonce()),
            normalizeOptional(request == null ? null : request.getCodeChallenge()),
            normalizeOptional(request == null ? null : request.getCodeChallengeMethod())
        );
        storeAuthorizationCode(code, payload);

        String redirectUrl = UriComponentsBuilder.fromUriString(context.getRedirectUri())
            .queryParam("code", code)
            .queryParamIfPresent("state", Optional.ofNullable(normalizedState))
            .build(true)
            .toUriString();
        return new SsoAuthorizeApproveResponse(redirectUrl);
    }

    public List<SsoAuthorizedClientResponse> listAuthorizations(User user) {
        return authorizationRepository.findAllByUserIdOrderByLastAuthorizedAtDesc(user.getId()).stream()
            .map(this::toAuthorizedClientResponse)
            .toList();
    }

    @Transactional
    public void revokeAuthorization(User user, String clientId) {
        String normalizedClientId = normalizeRequired(clientId, "ClientID 不能为空");
        authorizationRepository.deleteByUserIdAndClientId(user.getId(), normalizedClientId);
    }

    public Map<String, Object> exchangeAuthorizationCode(String grantType, String code, String clientId,
                                                         String clientSecret, String redirectUri,
                                                         String codeVerifier) {
        if (!"authorization_code".equals(normalizeOptional(grantType))) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                "当前仅支持 authorization_code");
        }

        OidcClient client = findActiveClient(clientId, HttpStatus.UNAUTHORIZED);
        if (clientSecret == null || clientSecret.isBlank()
            || client.getClientSecretHash() == null
            || !passwordEncoder.matches(clientSecret, client.getClientSecretHash())) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_client", "ClientSecret 不正确");
        }

        validateRedirectUriMatches(client, redirectUri);
        AuthorizationCodePayload payload = consumeAuthorizationCode(code);
        if (payload == null) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "授权码无效、已过期或已使用");
        }
        if (!client.getClientId().equals(payload.clientId()) || !containsValue(parseValues(client.getRedirectUris()), payload.redirectUri())) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "授权码与当前客户端或回调地址不匹配");
        }
        validateCodeVerifier(client, payload, codeVerifier);

        User user = userRepository.findById(payload.userId())
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "授权用户不存在"));

        String subject = buildSubject(client.getClientId(), user.getUuid());
        String scope = payload.scope() == null ? "openid" : payload.scope();
        String audience = firstValue(parseValues(client.getAudiences()), "ksuser-auth");
        String accessToken = ssoTokenService.generateAccessToken(client.getClientId(), user.getId(), subject, scope, audience);
        String idToken = ssoTokenService.generateIdToken(
            client.getClientId(),
            subject,
            payload.nonce(),
            scope,
            user.getUsername(),
            user.getAvatarUrl(),
            user.getEmail()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("id_token", idToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", ssoTokenService.getAccessTokenExpiresInSeconds());
        response.put("scope", scope);
        return response;
    }

    public Map<String, Object> buildUserInfo(String accessToken) {
        SsoTokenService.ParsedSsoAccessToken parsed = ssoTokenService.parseAccessToken(accessToken);
        if (parsed == null || parsed.clientId() == null || parsed.userId() == null) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "Access Token 无效或已过期");
        }
        OidcClient client = findActiveClient(parsed.clientId(), HttpStatus.UNAUTHORIZED);
        User user = userRepository.findById(parsed.userId())
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "Access Token 对应用户不存在"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sub", parsed.subject());
        Set<String> scopes = Oauth2ScopeUtil.parseScopeSet(parsed.scope());
        if (scopes.contains("profile")) {
            response.put("nickname", user.getUsername());
            response.put("preferred_username", user.getUsername());
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
                response.put("picture", user.getAvatarUrl());
            }
        }
        if (scopes.contains("email") && user.getEmail() != null && !user.getEmail().isBlank()) {
            response.put("email", user.getEmail());
            response.put("email_verified", true);
        }
        return response;
    }

    public Map<String, Object> buildOpenIdConfiguration() {
        String normalizedIssuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("issuer", normalizedIssuer);
        response.put("authorization_endpoint", authorizationEndpoint);
        response.put("token_endpoint", normalizedIssuer + "/sso/token");
        response.put("userinfo_endpoint", normalizedIssuer + "/sso/userinfo");
        response.put("response_types_supported", List.of("code"));
        response.put("grant_types_supported", List.of("authorization_code"));
        response.put("subject_types_supported", List.of("pairwise"));
        response.put("id_token_signing_alg_values_supported", List.of("HS256"));
        response.put("token_endpoint_auth_methods_supported", List.of("client_secret_post"));
        response.put("scopes_supported", List.of("openid", "profile", "email"));
        response.put("claims_supported", List.of("sub", "nickname", "preferred_username", "picture", "email", "email_verified"));
        response.put("code_challenge_methods_supported", List.of("S256"));
        response.put("response_modes_supported", List.of("query"));
        return response;
    }

    public boolean isAdminUser(User user) {
        String verificationType = user == null ? "none" : user.getVerificationType();
        return "admin".equalsIgnoreCase(verificationType);
    }

    private void ensureAdminUser(User user) {
        if (!isAdminUser(user)) {
            throw new Oauth2Exception(HttpStatus.FORBIDDEN, "unauthorized_client",
                "只有管理员可以创建或管理 SSO 内部服务应用");
        }
    }

    private OidcClient findManagedClient(String clientId) {
        String normalizedClientId = normalizeRequired(clientId, "ClientID 不能为空");
        return oidcClientRepository.findByClientId(normalizedClientId)
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.NOT_FOUND, "invalid_client", "SSO 客户端不存在"));
    }

    private OidcClient findActiveClient(String clientId, HttpStatus status) {
        String normalizedClientId = normalizeRequired(clientId, "ClientID 不能为空");
        return oidcClientRepository.findByClientId(normalizedClientId)
            .filter(client -> Boolean.TRUE.equals(client.getIsActive()))
            .orElseThrow(() -> new Oauth2Exception(status, "invalid_client", "SSO 客户端不存在或已停用"));
    }

    private void validateResponseType(String responseType) {
        if (!"code".equals(normalizeOptional(responseType))) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "unsupported_response_type", "仅支持 response_type=code");
        }
    }

    private void validateNonce(String nonce) {
        String normalizedNonce = normalizeOptional(nonce);
        if (normalizedNonce != null && normalizedNonce.length() > 255) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "nonce 长度不能超过 255 个字符");
        }
    }

    private void validatePkceParameters(OidcClient client, String codeChallenge, String codeChallengeMethod) {
        String normalizedChallenge = normalizeOptional(codeChallenge);
        String normalizedMethod = normalizeOptional(codeChallengeMethod);
        boolean requirePkce = Boolean.TRUE.equals(client.getRequirePkce());
        if (normalizedChallenge == null && normalizedMethod == null) {
            if (requirePkce) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "当前 SSO 客户端要求必须提供 PKCE 参数");
            }
            return;
        }
        if (normalizedChallenge == null || normalizedMethod == null) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "code_challenge 与 code_challenge_method 必须同时提供");
        }
        if (!"S256".equalsIgnoreCase(normalizedMethod)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "当前仅支持 code_challenge_method=S256");
        }
        if (normalizedChallenge.length() < 43 || normalizedChallenge.length() > 128) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request",
                "code_challenge 长度需在 43-128 个字符之间");
        }
    }

    private void validateCodeVerifier(OidcClient client, AuthorizationCodePayload payload, String codeVerifier) {
        if (payload.codeChallenge() == null || payload.codeChallengeMethod() == null) {
            if (Boolean.TRUE.equals(client.getRequirePkce())) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "缺少 PKCE 校验信息");
            }
            return;
        }
        String normalizedVerifier = normalizeOptional(codeVerifier);
        if (normalizedVerifier == null) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "缺少 code_verifier");
        }
        if (normalizedVerifier.length() < 43 || normalizedVerifier.length() > 128) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "code_verifier 长度无效");
        }
        String expectedChallenge = PkceUtil.toS256CodeChallenge(normalizedVerifier);
        if (!payload.codeChallenge().equals(expectedChallenge)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_grant", "PKCE 校验失败");
        }
    }

    private String normalizeClientName(String clientName) {
        String normalized = normalizeRequired(clientName, "客户端名称不能为空");
        if (normalized.length() < 2 || normalized.length() > 120) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "客户端名称长度需在 2-120 个字符之间");
        }
        return normalized;
    }

    private List<String> normalizeAndValidateRedirectUris(List<String> redirectUris) {
        List<String> values = normalizeUriList(redirectUris, true);
        if (values.isEmpty()) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "至少需要配置一个 redirect_uri");
        }
        return values;
    }

    private List<String> normalizeAndValidateOptionalRedirectUris(List<String> redirectUris) {
        return normalizeUriList(redirectUris, false);
    }

    private List<String> normalizeUriList(List<String> values, boolean requireLocalhostHttp) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String uri = normalizeAndValidateRedirectUri(value);
            if (requireLocalhostHttp || uri.startsWith("https://") || uri.startsWith("http://localhost")) {
                normalized.add(uri);
            }
        }
        return List.copyOf(normalized);
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

    private void validateRedirectUriMatches(OidcClient client, String redirectUri) {
        String normalized = normalizeAndValidateRedirectUri(redirectUri);
        if (!containsValue(parseValues(client.getRedirectUris()), normalized)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "redirect_uri 与客户端登记信息不一致");
        }
    }

    private List<String> normalizeSsoScopes(List<String> scopes) {
        Set<String> values = Oauth2ScopeUtil.parseScopeSet(joinValues(scopes));
        if (values.isEmpty()) {
            values = new LinkedHashSet<>(Set.of("openid", "profile", "email"));
        }
        if (!values.contains("openid")) {
            values.add("openid");
        }
        for (String scope : values) {
            if (!SUPPORTED_SCOPES.contains(scope)) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_scope",
                    "SSO 暂仅支持 openid、profile、email");
            }
        }
        return orderScopes(values);
    }

    private List<String> normalizeAudiences(List<String> audiences) {
        if (audiences == null || audiences.isEmpty()) {
            return List.of("ksuser-auth");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String audience : audiences) {
            String value = normalizeOptional(audience);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? List.of("ksuser-auth") : List.copyOf(normalized);
    }

    private List<String> resolveRequestedScopes(OidcClient client, String requestedScope) {
        List<String> allowedScopes = orderScopes(Oauth2ScopeUtil.parseScopeSet(client.getScopes()));
        List<String> requestedScopes = requestedScope == null || requestedScope.isBlank()
            ? allowedScopes
            : orderScopes(Oauth2ScopeUtil.parseScopeSet(requestedScope));
        for (String scope : requestedScopes) {
            if (!SUPPORTED_SCOPES.contains(scope)) {
                throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_scope",
                    "SSO 暂仅支持 openid、profile、email");
            }
        }
        if (!requestedScopes.contains("openid")) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_scope", "SSO 授权请求必须包含 openid");
        }
        if (!allowedScopes.containsAll(requestedScopes)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_scope", "请求的 scope 超出了客户端登记范围");
        }
        return requestedScopes;
    }

    private List<String> orderScopes(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of("openid");
        }
        List<String> ordered = new ArrayList<>();
        for (String supportedScope : SUPPORTED_SCOPE_ORDER) {
            if (scopes.contains(supportedScope)) {
                ordered.add(supportedScope);
            }
        }
        return ordered;
    }

    private SsoClientResponse toClientResponse(OidcClient client) {
        return new SsoClientResponse(
            client.getClientId(),
            client.getClientName(),
            client.getLogoUrl(),
            parseValues(client.getRedirectUris()),
            parseValues(client.getPostLogoutRedirectUris()),
            orderScopes(Oauth2ScopeUtil.parseScopeSet(client.getScopes())),
            parseValues(client.getAudiences()),
            Boolean.TRUE.equals(client.getRequirePkce()),
            client.getCreatedAt(),
            client.getUpdatedAt()
        );
    }

    private SsoAuthorizedClientResponse toAuthorizedClientResponse(UserSsoAuthorization authorization) {
        OidcClient client = oidcClientRepository.findByClientId(authorization.getClientId()).orElse(null);
        String clientName = client != null ? client.getClientName() : authorization.getClientName();
        String logoUrl = client != null ? client.getLogoUrl() : authorization.getLogoUrl();
        return new SsoAuthorizedClientResponse(
            authorization.getClientId(),
            clientName,
            logoUrl,
            authorization.getRedirectUri(),
            orderScopes(Oauth2ScopeUtil.parseScopeSet(authorization.getScopes())),
            authorization.getAuthorizedAt(),
            authorization.getLastAuthorizedAt()
        );
    }

    private boolean hasExistingAuthorization(User user, String clientId, List<String> requestedScopes) {
        if (user == null) {
            return false;
        }
        return authorizationRepository.findByUserIdAndClientId(user.getId(), clientId)
            .map(record -> Oauth2ScopeUtil.parseScopeSet(record.getScopes()))
            .map(grantedScopes -> grantedScopes.containsAll(requestedScopes))
            .orElse(false);
    }

    private void recordAuthorization(User user, OidcClient client, List<String> requestedScopes, String redirectUri) {
        UserSsoAuthorization record = authorizationRepository.findByUserIdAndClientId(user.getId(), client.getClientId())
            .orElseGet(UserSsoAuthorization::new);
        LocalDateTime now = LocalDateTime.now();
        if (record.getId() == null) {
            record.setUserId(user.getId());
            record.setClientId(client.getClientId());
            record.setAuthorizedAt(now);
        }
        LinkedHashSet<String> grantedScopes = new LinkedHashSet<>(Oauth2ScopeUtil.parseScopeSet(record.getScopes()));
        grantedScopes.addAll(requestedScopes);
        record.setClientName(client.getClientName());
        record.setLogoUrl(client.getLogoUrl());
        record.setRedirectUri(redirectUri);
        record.setScopes(joinValues(orderScopes(grantedScopes)));
        record.setLastAuthorizedAt(now);
        authorizationRepository.save(record);
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

    private String buildSubject(String clientId, String userUuid) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(("sso:sub:" + clientId + ":" + userUuid).getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return "sub_" + encoded.substring(0, Math.min(encoded.length(), 40));
        } catch (Exception ex) {
            throw new Oauth2Exception(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "生成 sub 失败");
        }
    }

    private String generateUniqueClientId() {
        for (int i = 0; i < 10; i++) {
            String candidate = generateOpaqueValue("ssocli_", 18);
            if (!oidcClientRepository.existsByClientId(candidate)) {
                return candidate;
            }
        }
        throw new Oauth2Exception(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "生成 ClientID 失败，请稍后重试");
    }

    private String generateOpaqueValue(String prefix, int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<String> parseValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : value.trim().split("\\s+")) {
            String normalized = normalizeOptional(part);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(" ", values);
    }

    private boolean containsValue(List<String> values, String target) {
        return values.stream().anyMatch(value -> Objects.equals(value, target));
    }

    private String firstValue(List<String> values, String fallback) {
        return values.isEmpty() ? fallback : values.get(0);
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
                                            String clientId,
                                            String redirectUri,
                                            String scope,
                                            String nonce,
                                            String codeChallenge,
                                            String codeChallengeMethod) {
    }
}
