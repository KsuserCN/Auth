package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.OauthCallbackRequest;
import cn.ksuser.api.dto.QqMobileLoginRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauthAccount;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import cn.ksuser.api.entity.UserSettings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

@RestController
@RequestMapping("/oauth")
public class OauthController {

    private static final Logger logger = LoggerFactory.getLogger(OauthController.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final AppProperties appProperties;
    private final UserOauthAccountRepository oauthRepo;
    private final UserRepository userRepository;
    private final cn.ksuser.api.service.UserService userService;
    private final JwtUtil jwtUtil;
    private final UserSessionService userSessionService;
    private final RateLimitService rateLimitService;
    private final cn.ksuser.api.repository.UserSettingsRepository userSettingsRepository;
    private final cn.ksuser.api.service.TotpService totpService;
    private final cn.ksuser.api.service.MfaService mfaService;
    private final cn.ksuser.api.util.SensitiveLogUtil sensitiveLogUtil;
    private final cn.ksuser.api.repository.UserPasskeyRepository userPasskeyRepository;
    private final cn.ksuser.api.service.SensitiveOperationService sensitiveOperationService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.qq.oauth.app-id:}")
    private String qqClientId;

    @Value("${app.qq.oauth.app-key:}")
    private String qqClientSecret;

    @Value("${app.qq.mobile.app-id:${app.qq.oauth.app-id:}}")
    private String qqMobileAppId;

    @Value("${app.qq.mobile.allowed-app-ids:}")
    private String qqMobileAllowedAppIds;

    @Value("${app.github.oauth.app-id:}")
    private String githubClientId;

    @Value("${app.github.oauth.app-key:}")
    private String githubClientSecret;

    @Value("${app.microsoft.oauth.app-id:}")
    private String microsoftClientId;

    @Value("${app.microsoft.oauth.app-key:}")
    private String microsoftClientSecret;

    @Value("${app.microsoft.oauth.tenant-id:common}")
    private String microsoftTenantId;

    @Value("${app.google.oauth.app-id:}")
    private String googleClientId;

    @Value("${app.google.oauth.app-key:}")
    private String googleClientSecret;

    public OauthController(AppProperties appProperties,
                           UserOauthAccountRepository oauthRepo,
                           UserRepository userRepository,
                           cn.ksuser.api.service.UserService userService,
                           JwtUtil jwtUtil,
                           UserSessionService userSessionService,
                           RateLimitService rateLimitService,
                           cn.ksuser.api.repository.UserSettingsRepository userSettingsRepository,
                           cn.ksuser.api.service.TotpService totpService,
                           cn.ksuser.api.service.MfaService mfaService,
                           cn.ksuser.api.util.SensitiveLogUtil sensitiveLogUtil,
                           cn.ksuser.api.repository.UserPasskeyRepository userPasskeyRepository,
                           cn.ksuser.api.service.SensitiveOperationService sensitiveOperationService,
                           StringRedisTemplate redisTemplate) {
        this.appProperties = appProperties;
        this.oauthRepo = oauthRepo;
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.userSessionService = userSessionService;
        this.rateLimitService = rateLimitService;
        this.userSettingsRepository = userSettingsRepository;
        this.totpService = totpService;
        this.mfaService = mfaService;
        this.sensitiveLogUtil = sensitiveLogUtil;
        this.userPasskeyRepository = userPasskeyRepository;
        this.sensitiveOperationService = sensitiveOperationService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 解绑 QQ（当前已登录用户）
     * 添加了更细致的安全校验，防止解绑最后可登录方式
     */
    @PostMapping("/qq/unbind")
    public ResponseEntity<ApiResponse<Object>> unbindQQ(Authentication authentication, 
                                                        jakarta.servlet.http.HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "未认证"));
        }
        String uuid = authentication.getPrincipal().toString();
        var userOpt = userService.findByUuid(uuid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "用户不存在"));
        }
        var user = userOpt.get();
        
        // 检查是否已完成敏感操作验证
        String clientIp = rateLimitService.getClientIp(request);
        if (!sensitiveOperationService.isVerified(uuid, clientIp)) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("needVerification", true);
            data.put("message", "解绑 QQ 帐号属于敏感操作，需要先完成身份验证");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "需要完成敏感操作验证", data));
        }

        var acctOpt = oauthRepo.findByProviderAndUserId("qq", user.getId());
        if (acctOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "未绑定 QQ 帐号"));
        }
        var acct = acctOpt.get();
        
        // 检查是否为最后可登录方式
        // 1. 检查是否设置了密码
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()
            && user.getEmail() != null && !user.getEmail().isBlank();
        
        // 2. 检查是否绑定了 Passkey
        boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
        
        // 3. 检查是否绑定了其他 OAuth 提供商（未来扩展）
        // 目前只有 QQ OAuth，所以不需要检查
        
        // 如果既没有密码也没有 Passkey，则不允许解绑（因为解绑后用户将无法登录）
        boolean hasApple = oauthRepo.findByProviderAndUserId("apple", user.getId()).map(a -> Boolean.TRUE.equals(a.getIsEnabled())).orElse(false);
        if (!hasPassword && !hasPasskey && !hasApple) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("canUnbind", false);
            data.put("reason", "last_login_method");
            data.put("message", "无法解绑，这是您的最后一种登录方式");
            data.put("suggestions", java.util.Arrays.asList(
                "请先设置密码或绑定 Passkey",
                "设置密码后可以使用邮箱+密码登录",
                "绑定 Passkey 后可以使用无密码登录"
            ));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "无法解绑最后登录方式", data));
        }
        
        // 删除绑定
        oauthRepo.delete(acct);
        
        // 记录敏感操作日志（使用解绑 OAuth 帐号类型）
        sensitiveLogUtil.log(request, user.getId(), "UNBIND_OAUTH", "qq", 
            cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, startTime);

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "解绑成功"));
    }

    /**
     * QQ OAuth 登录回调（无需登录态）
     */
    @PostMapping("/qq/callback/login")
    public ResponseEntity<ApiResponse<Object>> qqLoginCallback(@RequestBody OauthCallbackRequest req,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        return handleQqCallback(req, request, response, "login", null);
    }

    /**
     * QQ OAuth 绑定回调（需要登录态）
     */
    @PostMapping("/qq/callback/bind")
    public ResponseEntity<ApiResponse<Object>> qqBindCallback(@RequestBody OauthCallbackRequest req,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response,
                                                              Authentication authentication) {
        return handleQqCallback(req, request, response, "bind", authentication);
    }

    /**
     * QQ OAuth 解绑（兼容回调路径，不再要求 QQ 授权参数）
     */
    @PostMapping("/qq/callback/unbind")
    public ResponseEntity<ApiResponse<Object>> qqUnbindCallback(HttpServletRequest request,
                                                                Authentication authentication) {
        return unbindQQ(authentication, request);
    }

    /**
     * QQ 移动端 SDK 登录回调。
     */
    @PostMapping("/qq/mobile-login")
    public ResponseEntity<ApiResponse<Object>> qqMobileLogin(@RequestBody QqMobileLoginRequest req,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        String appId = req.getAppId() == null ? "" : req.getAppId().trim();
        String accessToken = req.getAccessToken() == null ? "" : req.getAccessToken().trim();
        String openid = req.getOpenid() == null ? "" : req.getOpenid().trim();
        String unionid = req.getUnionid() == null ? "" : req.getUnionid().trim();

        if (appId.isBlank() || accessToken.isBlank() || openid.isBlank() || unionid.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "参数缺失"));
        }
        java.util.Set<String> trustedMobileAppIds = new java.util.HashSet<>();
        if (qqMobileAppId != null && !qqMobileAppId.isBlank()) trustedMobileAppIds.add(qqMobileAppId.trim());
        if (qqMobileAllowedAppIds != null) Arrays.stream(qqMobileAllowedAppIds.split(",")).map(String::trim).filter(v -> !v.isBlank()).forEach(trustedMobileAppIds::add);
        if (!trustedMobileAppIds.contains(appId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "QQ 移动应用 AppId 未配置或不匹配"));
        }

        String clientIp = rateLimitService.getClientIp(request);
        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "请求过于频繁"));
        }
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        long startTime = System.currentTimeMillis();
        try {
            QqMobileIdentity identity = verifyQqMobileIdentity(appId, accessToken, openid, unionid);
            var bound = oauthRepo.findByProviderAndUnionId("qq", identity.unionid());
            if (bound.isPresent() && Boolean.TRUE.equals(bound.get().getIsEnabled())) {
                var userOpt = userRepository.findById(bound.get().getUserId());
                if (userOpt.isPresent()) {
                    UserOauthAccount acct = bound.get();
                    acct.setLastLoginAt(java.time.LocalDateTime.now());
                    oauthRepo.save(acct);
                    return issueOauthLoginResponse(
                        userOpt.get(),
                        "qq",
                        request,
                        response,
                        startTime,
                        "QQ 登录成功"
                    );
                }
            }

            sensitiveLogUtil.logLogin(request, null, "QQ", false, "Not bound", startTime);
            java.util.Map<String, Object> data = createPendingOauthBindData(
                "qq",
                identity.openid(),
                identity.unionid(),
                "login",
                "mobile"
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "未绑定，需要注册或绑定", data));
        } catch (IllegalArgumentException ex) {
            sensitiveLogUtil.logLogin(request, null, "QQ", false, ex.getMessage(), startTime);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, ex.getMessage()));
        } catch (Exception ex) {
            logger.error("QQ mobile login failed", ex);
            sensitiveLogUtil.logLogin(request, null, "QQ", false, "QQ mobile login failed", startTime);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "内部错误"));
        }
    }

    /**
     * GitHub OAuth 登录回调（无需登录态）
     */
    @PostMapping("/github/callback/login")
    public ResponseEntity<ApiResponse<Object>> githubLoginCallback(@RequestBody OauthCallbackRequest req,
                                                                   HttpServletRequest request,
                                                                   HttpServletResponse response) {
        return handleGithubCallback(req, request, response, "login", null);
    }

    /**
     * GitHub OAuth 绑定回调（需要登录态）
     */
    @PostMapping("/github/callback/bind")
    public ResponseEntity<ApiResponse<Object>> githubBindCallback(@RequestBody OauthCallbackRequest req,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                  Authentication authentication) {
        return handleGithubCallback(req, request, response, "bind", authentication);
    }

    /**
     * GitHub OAuth 解绑回调
     */
    @PostMapping("/github/callback/unbind")
    public ResponseEntity<ApiResponse<Object>> githubUnbindCallback(@RequestBody OauthCallbackRequest req,
                                                                    HttpServletRequest request,
                                                                    Authentication authentication) {
        return handleGithubUnbind(req, request, authentication);
    }

    /**
     * Microsoft OAuth 登录回调（无需登录态）
     */
    @PostMapping("/microsoft/callback/login")
    public ResponseEntity<ApiResponse<Object>> microsoftLoginCallback(@RequestBody OauthCallbackRequest req,
                                                                      HttpServletRequest request,
                                                                      HttpServletResponse response) {
        return handleMicrosoftCallback(req, request, response, "login", null);
    }

    /**
     * Microsoft OAuth 绑定回调（需要登录态）
     */
    @PostMapping("/microsoft/callback/bind")
    public ResponseEntity<ApiResponse<Object>> microsoftBindCallback(@RequestBody OauthCallbackRequest req,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response,
                                                                     Authentication authentication) {
        return handleMicrosoftCallback(req, request, response, "bind", authentication);
    }

    /**
     * Microsoft OAuth 解绑回调（需要登录态）
     */
    @PostMapping("/microsoft/callback/unbind")
    public ResponseEntity<ApiResponse<Object>> microsoftUnbindCallback(@RequestBody OauthCallbackRequest req,
                                                                       HttpServletRequest request,
                                                                       HttpServletResponse response,
                                                                       Authentication authentication) {
        return handleMicrosoftCallback(req, request, response, "unbind", authentication);
    }

    /**
     * 解绑 Microsoft（当前已登录用户）
     */
    @PostMapping("/microsoft/unbind")
    public ResponseEntity<ApiResponse<Object>> unbindMicrosoft(Authentication authentication,
                                                               HttpServletRequest request) {
        return handleMicrosoftUnbind(request, authentication);
    }

    /**
     * Google OAuth 登录回调（无需登录态）
     */
    @PostMapping("/google/callback/login")
    public ResponseEntity<ApiResponse<Object>> googleLoginCallback(@RequestBody OauthCallbackRequest req,
                                                                   HttpServletRequest request,
                                                                   HttpServletResponse response) {
        return handleGoogleCallback(req, request, response, "login", null);
    }

    /**
     * Google OAuth 绑定回调（需要登录态）
     */
    @PostMapping("/google/callback/bind")
    public ResponseEntity<ApiResponse<Object>> googleBindCallback(@RequestBody OauthCallbackRequest req,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                  Authentication authentication) {
        return handleGoogleCallback(req, request, response, "bind", authentication);
    }

    /**
     * Google OAuth 解绑回调（需要登录态）
     */
    @PostMapping("/google/callback/unbind")
    public ResponseEntity<ApiResponse<Object>> googleUnbindCallback(@RequestBody OauthCallbackRequest req,
                                                                    HttpServletRequest request,
                                                                    Authentication authentication) {
        return handleGoogleUnbind(req, request, authentication);
    }

    /**
     * 解绑 Google（当前已登录用户）
     */
    @PostMapping("/google/unbind")
    public ResponseEntity<ApiResponse<Object>> unbindGoogle(Authentication authentication,
                                                            HttpServletRequest request) {
        return handleGoogleUnbind(null, request, authentication);
    }

    /**
     * 获取当前用户的第三方登录绑定状态
     */
    @GetMapping("/accounts/status")
    public ResponseEntity<ApiResponse<Object>> getOauthAccountsStatus(Authentication authentication) {
        if (authentication == null
            || authentication.getPrincipal() == null
            || authentication instanceof AnonymousAuthenticationToken
            || "anonymousUser".equals(authentication.getPrincipal().toString())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "未认证"));
        }

        String uuid = authentication.getPrincipal().toString();
        var userOpt = userService.findByUuid(uuid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "用户不存在"));
        }

        User user = userOpt.get();
        List<UserOauthAccount> accounts = oauthRepo.findByUserId(user.getId());
        Map<String, UserOauthAccount> accountByProvider = new HashMap<>();
        for (UserOauthAccount account : accounts) {
            if (account.getProvider() == null) {
                continue;
            }
            accountByProvider.put(account.getProvider().toLowerCase(), account);
        }

        List<String> providers = Arrays.asList("wechat", "qq", "microsoft", "github", "google");
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        for (String provider : providers) {
            UserOauthAccount account = accountByProvider.get(provider);
            Map<String, Object> item = new HashMap<>();
            item.put("provider", provider);
            item.put("bound", account != null && Boolean.TRUE.equals(account.getIsEnabled()));
            item.put("lastLoginAt", account != null ? account.getLastLoginAt() : null);
            data.add(item);
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "查询成功", data));
    }

    private ResponseEntity<ApiResponse<Object>> handleQqCallback(OauthCallbackRequest req,
                                                                 HttpServletRequest request,
                                                                 HttpServletResponse response,
                                                                 String expectedOperation,
                                                                 Authentication authentication) {
        String code = req.getCode();
        String redirectUriFromRequest = req.getRedirectUri();
        String state = req.getState();

        if (code == null || code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "code 不能为空"));
        }
        if (state == null || state.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "state 不能为空"));
        }

        // 新格式: 校验参数;操作类型;prd/dev
        String[] stateParts = state.split(";", -1);
        if (stateParts.length != 3 || stateParts[0].isBlank() || stateParts[1].isBlank() || stateParts[2].isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 格式不正确，需为: 校验参数;操作类型;prd/dev"));
        }
        String stateNonce = stateParts[0].trim();
        String operationType = stateParts[1].trim().toLowerCase();
        String env = stateParts[2].trim().toLowerCase();
        if (!("login".equals(operationType) || "bind".equals(operationType) || "unbind".equals(operationType))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型不支持，仅支持 login/bind/unbind"));
        }
        if (!expectedOperation.equals(operationType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型与当前接口不匹配"));
        }
        if (!("prd".equals(env) || "dev".equals(env))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 环境标识不支持，仅支持 prd/dev"));
        }

        String redirectUri = resolveRedirectUriByEnv(env);
        if (redirectUri == null) {
            logger.error("No qq redirectUri configured for env={}", env);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "QQ redirectUri 未配置"));
        }
        if (redirectUriFromRequest != null && !redirectUriFromRequest.isBlank() && !redirectUri.equals(redirectUriFromRequest)) {
            logger.warn("Ignore redirectUri from request: {}, use configured: {}", redirectUriFromRequest, redirectUri);
        }

        User currentUser = null;
        if (!"login".equals(expectedOperation)) {
            if (authentication == null
                || authentication.getPrincipal() == null
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal().toString())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, expectedOperation + " 操作需要有效登录态"));
            }
            String uuid = authentication.getPrincipal().toString();
            var userOpt = userService.findByUuid(uuid);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "当前登录态对应用户不存在"));
            }
            currentUser = userOpt.get();
        }
        if (!"login".equals(expectedOperation) && currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, expectedOperation + " 操作需要有效登录态"));
        }

        String clientIp = rateLimitService.getClientIp(request);
        String composite = code + ":" + stateNonce + ":" + operationType + ":" + env + ":" + clientIp;
        String codeUsedKey = "oauth:qq:used-code:" + composite;
        String codeProcessingKey = "oauth:qq:processing-code:" + composite;
        String rawCodeUsedKey = "oauth:qq:raw-used-code:" + code;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(codeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 QQ 登录"));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rawCodeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 QQ 登录"));
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(codeProcessingKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "登录处理中，请勿重复提交"));
        }

        // 速率限制（按 IP）
        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "请求过于频繁"));
        }
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        try {
            // 1) 用 code 换取 access_token
            String tokenUrl = String.format(
                "https://graph.qq.com/oauth2.0/token?grant_type=authorization_code&client_id=%s&client_secret=%s&code=%s&redirect_uri=%s&fmt=json",
                URLEncoder.encode(qqClientId, StandardCharsets.UTF_8),
                URLEncoder.encode(qqClientSecret, StandardCharsets.UTF_8),
                URLEncoder.encode(code, StandardCharsets.UTF_8),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
            );

            String tokenResp = restTemplate.getForObject(tokenUrl, String.class);
            if (tokenResp == null || tokenResp.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "QQ token 接口无响应"));
            }

            JsonNode tokenJson = objectMapper.readTree(tokenResp);
            if (tokenJson.has("error")) {
                String err = tokenJson.path("error_description").asText(tokenJson.path("error").asText());
                if (err != null && err.toLowerCase().contains("reused")) {
                    redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
                    redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "QQ token 错误: " + err));
            }

            String accessToken = tokenJson.path("access_token").asText(null);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 QQ 返回 access_token"));
            }
            redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
            redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));

            // 2) 用 access_token 获取 openid
            String meUrl = String.format("https://graph.qq.com/oauth2.0/me?access_token=%s&fmt=json&unionid=1", URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
            String meResp = restTemplate.getForObject(meUrl, String.class);
            if (meResp == null || meResp.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "QQ me 接口无响应"));
            }
            JsonNode meJson = objectMapper.readTree(meResp);
            if (meJson.has("error")) {
                String err = meJson.path("error_description").asText(meJson.path("error").asText());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "QQ me 错误: " + err));
            }
            String openid = meJson.path("openid").asText(null);
            String unionid = meJson.path("unionid").asText(null);
            if (unionid != null && unionid.isBlank()) {
                unionid = null;
            }
            if (openid == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 QQ 返回 openid"));
            }
            if (unionid == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 QQ 返回 unionid，请确认已开通 getUnionId 权限"));
            }

            // bind 操作：为当前登录用户直接写入 QQ 绑定关系
            if ("bind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "bind 操作需要有效登录态"));
                }

                if (oauthRepo.findByProviderAndUserId("qq", user.getId()).isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前账号已绑定 QQ"));
                }

                java.util.Optional<UserOauthAccount> existingBinding = oauthRepo.findByProviderAndUnionId("qq", unionid);
                if (existingBinding.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "该 QQ 账号已被绑定"));
                }

                UserOauthAccount acct = new UserOauthAccount();
                acct.setProvider("qq");
                acct.setProviderUserId(openid);
                acct.setUnionId(unionid);
                acct.setUserId(user.getId());
                acct.setIsEnabled(true);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                acct.setLinkedAt(now);
                acct.setCreatedAt(now);
                oauthRepo.save(acct);

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("bound", true);
                data.put("provider", "qq");
                data.put("openid", openid);
                data.put("unionid", unionid);
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "QQ 绑定成功", data));
            }

            if ("unbind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "unbind 操作需要有效登录态"));
                }

                if (unionid.isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "未从 QQ 返回可用于解绑校验的 unionid"));
                }

                String uuid = user.getUuid();
                String clientIpReq = rateLimitService.getClientIp(request);
                if (!sensitiveOperationService.isVerified(uuid, clientIpReq)) {
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("needVerification", true);
                    data.put("message", "解绑 QQ 帐号属于敏感操作，需要先完成身份验证");
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "需要完成敏感操作验证", data));
                }

                var acctOpt = oauthRepo.findByProviderAndUserId("qq", user.getId());
                if (acctOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "未绑定 QQ 帐号"));
                }
                var acct = acctOpt.get();

                if (!unionid.equals(acct.getUnionId())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前授权的 QQ 账号与已绑定账号不一致"));
                }

                boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
                boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
                if (!hasPassword && !hasPasskey) {
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("canUnbind", false);
                    data.put("reason", "last_login_method");
                    data.put("message", "无法解绑，这是您的最后一种登录方式");
                    data.put("suggestions", java.util.Arrays.asList(
                        "请先设置密码或绑定 Passkey",
                        "设置密码后可以使用邮箱+密码登录",
                        "绑定 Passkey 后可以使用无密码登录"
                    ));
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "无法解绑最后登录方式", data));
                }

                oauthRepo.delete(acct);
                sensitiveLogUtil.log(request, user.getId(), "UNBIND_OAUTH", "qq",
                    cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, System.currentTimeMillis());
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "解绑成功"));
            }

            long startTime = System.currentTimeMillis();

            // 3) 查询是否已绑定
            var bound = oauthRepo.findByProviderAndUnionId("qq", unionid);
            if (bound.isPresent() && Boolean.TRUE.equals(bound.get().getIsEnabled())) {
                UserOauthAccount acct = bound.get();
                var userOpt = userRepository.findById(acct.getUserId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // 检查是否需要 MFA（用户设置 + TOTP 已启用）
                    User userEntity = user;
                    UserSettings settings = userSettingsRepository.findByUserId(userEntity.getId()).orElse(null);
                    boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
                    if (mfaEnabled) {
                        List<String> mfaMethods = resolveOauthMfaMethods(userEntity.getId());
                        if (mfaMethods.isEmpty()) {
                            // do nothing
                        } else {
                        String clientIpReq = rateLimitService.getClientIp(request);
                        String userAgentReq = rateLimitService.getClientUserAgent(request);
                        String challengeId = mfaService.createChallenge(userEntity.getId(), clientIpReq, userAgentReq,
                            "qq", new HashSet<>(mfaMethods));
                        java.util.Map<String,Object> resp = new java.util.HashMap<>();
                        resp.put("challengeId", challengeId);
                        resp.put("method", mfaMethods.get(0));
                        resp.put("methods", mfaMethods);
                        resp.put("operationType", operationType);
                        resp.put("env", env);
                        return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new ApiResponse<>(201, "需要 MFA 验证", resp));
                        }
                    }

                    // 签发系统 token + session（与现有登录流程一致）
                    String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
                    String userAgent = rateLimitService.getClientUserAgent(request);
                    UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
                    int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
                    String accessTokenLocal = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

                    // 设置 refreshToken Cookie
                    setRefreshTokenCookie(response, refreshToken);

                    // 更新最后登录时间
                    acct.setLastLoginAt(java.time.LocalDateTime.now());
                    oauthRepo.save(acct);

                    // 登录成功，记录敏感登录日志并返回 JSON（包含 accessToken 和 user 信息）
                    sensitiveLogUtil.logLogin(request, user.getId(), "QQ", true, null, startTime);

                    java.util.Map<String,Object> data = new java.util.HashMap<>();
                    data.put("accessToken", accessTokenLocal);
                    data.put("user", user);
                    data.put("operationType", operationType);
                    data.put("env", env);
                    return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "登录成功", data));
                }
            }

            // 未绑定：记录尝试并返回 needBind（使用 202 表示需要用户在前端继续注册或绑定）
            sensitiveLogUtil.logLogin(request, null, "QQ", false, "Not bound", startTime);
            java.util.Map<String,Object> data = createPendingOauthBindData("qq", openid, unionid, operationType, env);
            // 202 表示已接受，需前端引导用户注册或绑定
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "未绑定，需要注册或绑定", data));

        } catch (Exception e) {
            logger.error("QQ oauth callback failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "内部错误"));
        } finally {
            redisTemplate.delete(codeProcessingKey);
        }
    }

    private String resolveRedirectUriByEnv(String env) {
        List<String> configuredUris = appProperties.getQq().getOauth().getRedirectUris();
        if (configuredUris == null || configuredUris.isEmpty()) {
            return null;
        }

        if ("dev".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && (uri.contains("localhost") || uri.contains("127.0.0.1"))) {
                    return uri;
                }
            }
        }

        if ("prd".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && !uri.contains("localhost") && !uri.contains("127.0.0.1")) {
                    return uri;
                }
            }
        }

        for (String uri : configuredUris) {
            if (uri != null && !uri.isBlank()) {
                return uri;
            }
        }
        return null;
    }

    private QqMobileIdentity verifyQqMobileIdentity(String appId, String accessToken, String openid, String unionid) throws Exception {
        String meUrl = String.format(
            "https://graph.qq.com/oauth2.0/me?access_token=%s&fmt=json&unionid=1",
            URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
        );
        String meResp = restTemplate.getForObject(meUrl, String.class);
        if (meResp == null || meResp.isBlank()) {
            throw new IllegalArgumentException("QQ me 接口无响应");
        }

        JsonNode meJson = objectMapper.readTree(meResp);
        if (meJson.has("error")) {
            String err = meJson.path("error_description").asText(meJson.path("error").asText());
            throw new IllegalArgumentException("QQ me 错误: " + err);
        }

        String returnedOpenid = meJson.path("openid").asText(null);
        String returnedClientId = meJson.path("client_id").asText(null);
        String returnedUnionid = meJson.path("unionid").asText(null);
        if (returnedUnionid != null && returnedUnionid.isBlank()) {
            returnedUnionid = null;
        }
        if (returnedOpenid == null || !returnedOpenid.equals(openid)) {
            throw new IllegalArgumentException("QQ openid 校验失败");
        }
        if (returnedUnionid == null) {
            throw new IllegalArgumentException("QQ 未返回 unionid，请确认已开通 getUnionId 权限");
        }
        if (!returnedUnionid.equals(unionid)) {
            throw new IllegalArgumentException("QQ unionid 校验失败");
        }
        if (!appId.equals(returnedClientId)) {
            throw new IllegalArgumentException("QQ AppId 校验失败");
        }
        return new QqMobileIdentity(returnedOpenid, returnedUnionid);
    }

    /**
     * 绑定已有账号（前端传入 openid、email、password）
     */
    @PostMapping({"/qq/bind-existing", "/github/bind-existing", "/microsoft/bind-existing", "/google/bind-existing"})
    public ResponseEntity<ApiResponse<Object>> bindExisting(@RequestBody cn.ksuser.api.dto.OauthBindRequest req,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        String provider = resolveProviderFromRequest(request, req.getProvider());
        String email = req.getEmail();
        String password = req.getPassword();
        if (provider == null || email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "参数缺失"));
        }

        PendingOauthIdentity pending;
        try {
            pending = resolvePendingOauthIdentity(provider, req.getBindToken());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, ex.getMessage()));
        }

        // 验证用户凭据（使用 UserService.login）
        var loginOpt = userService.login(email, password);
        if (loginOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "邮箱或密码错误"));
        }
        var user = loginOpt.get();

        ResponseEntity<ApiResponse<Object>> conflict = ensureCanLinkOauth(provider, user, pending);
        if (conflict != null) {
            return conflict;
        }
        linkOauthAccount(provider, user, pending);
        consumePendingOauthIdentity(req.getBindToken());

        return issueOauthLoginResponse(user, provider, request, response, System.currentTimeMillis(), "绑定并登录成功");
    }

    /**
     * 注册并绑定（前端传入 openid, username, email, password）
     */
    @PostMapping({"/qq/register-bind", "/github/register-bind", "/microsoft/register-bind", "/google/register-bind"})
    public ResponseEntity<ApiResponse<Object>> registerAndBind(@RequestBody cn.ksuser.api.dto.OauthRegisterBindRequest req,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        String provider = resolveProviderFromRequest(request, req.getProvider());
        String username = req.getUsername();
        String email = req.getEmail();
        String password = req.getPassword();
        if (provider == null || username == null || username.isEmpty() || email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "参数缺失"));
        }

        PendingOauthIdentity pending;
        try {
            pending = resolvePendingOauthIdentity(provider, req.getBindToken());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, ex.getMessage()));
        }
        if (findExistingOauthBinding(provider, pending).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, providerDisplayName(provider) + " 账号已被绑定"));
        }

        // 使用 UserService 注册
        cn.ksuser.api.dto.RegisterResult regResult = userService.register(username, email, password);
        if (regResult.getStatus() != cn.ksuser.api.dto.RegisterResult.Status.SUCCESS) {
            switch (regResult.getStatus()) {
                case USERNAME_EXISTS:
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "用户名已存在"));
                case EMAIL_EXISTS:
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "邮箱已存在"));
                default:
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "注册失败"));
            }
        }

        User newUser = regResult.getUser();

        // 为新注册用户初始化默认 settings，确保后续设置相关接口有稳定初始数据
        getOrCreateUserSettings(newUser.getId());

        linkOauthAccount(provider, newUser, pending);
        consumePendingOauthIdentity(req.getBindToken());

        return issueOauthLoginResponse(newUser, provider, request, response, System.currentTimeMillis(), "注册并绑定并登录成功");
    }

    @PostMapping({"/qq/bind-pending", "/github/bind-pending", "/microsoft/bind-pending", "/google/bind-pending"})
    public ResponseEntity<ApiResponse<Object>> bindPendingToCurrentUser(@RequestBody cn.ksuser.api.dto.OauthBindRequest req,
                                                                        HttpServletRequest request,
                                                                        Authentication authentication) {
        String provider = resolveProviderFromRequest(request, req.getProvider());
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "参数缺失"));
        }
        if (authentication == null
            || authentication.getPrincipal() == null
            || authentication instanceof AnonymousAuthenticationToken
            || "anonymousUser".equals(authentication.getPrincipal().toString())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "未认证"));
        }

        String uuid = authentication.getPrincipal().toString();
        var userOpt = userService.findByUuid(uuid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "用户不存在"));
        }

        PendingOauthIdentity pending;
        try {
            pending = resolvePendingOauthIdentity(provider, req.getBindToken());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, ex.getMessage()));
        }

        User user = userOpt.get();
        ResponseEntity<ApiResponse<Object>> conflict = ensureCanLinkOauth(provider, user, pending);
        if (conflict != null) {
            return conflict;
        }
        linkOauthAccount(provider, user, pending);
        consumePendingOauthIdentity(req.getBindToken());

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("bound", true);
        data.put("provider", provider);
        data.put("message", providerDisplayName(provider) + " 绑定成功");
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, providerDisplayName(provider) + " 绑定成功", data));
    }

    /**
     * GitHub OAuth 回调处理（登录和绑定）
     */
    private ResponseEntity<ApiResponse<Object>> handleGithubCallback(OauthCallbackRequest req,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response,
                                                                     String expectedOperation,
                                                                     Authentication authentication) {
        String code = req.getCode();
        String state = req.getState();

        if (code == null || code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "code 不能为空"));
        }
        if (state == null || state.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "state 不能为空"));
        }

        // state 格式：verifyToken;operation;env
        String[] stateParts = state.split(";", -1);
        if (stateParts.length != 3 || stateParts[0].isBlank() || stateParts[1].isBlank() || stateParts[2].isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 格式不正确，需为: verifyToken;operation;env"));
        }
        String verifyToken = stateParts[0].trim();
        String operationType = stateParts[1].trim().toLowerCase();
        String env = stateParts[2].trim().toLowerCase();

        if (!("login".equals(operationType) || "bind".equals(operationType) || "unbind".equals(operationType))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型不支持，仅支持 login/bind/unbind"));
        }
        if (!expectedOperation.equals(operationType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型与当前接口不匹配"));
        }
        if (!("prd".equals(env) || "dev".equals(env))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 环境标识不支持，仅支持 prd/dev"));
        }

        String redirectUri = resolveGithubRedirectUriByEnv(env);
        if (redirectUri == null) {
            logger.error("No github redirectUri configured for env={}", env);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "GitHub redirectUri 未配置"));
        }
        redirectUri = redirectUri.trim();
        try {
            new java.net.URI(redirectUri).toURL();
        } catch (Exception ex) {
            logger.error("Invalid github redirectUri: {}", redirectUri, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "GitHub redirectUri 非法，请检查后端配置"));
        }
        if (githubClientId == null || githubClientId.isBlank() || githubClientSecret == null || githubClientSecret.isBlank()) {
            logger.error("GitHub OAuth client_id/client_secret not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "GitHub OAuth 配置缺失"));
        }

        User currentUser = null;
        if (!"login".equals(expectedOperation)) {
            if (authentication == null
                || authentication.getPrincipal() == null
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal().toString())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, expectedOperation + " 操作需要有效登录态"));
            }
            String uuid = authentication.getPrincipal().toString();
            var userOpt = userService.findByUuid(uuid);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "当前登录态对应用户不存在"));
            }
            currentUser = userOpt.get();
        }
        if (!"login".equals(expectedOperation) && currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, expectedOperation + " 操作需要有效登录态"));
        }

        String clientIp = rateLimitService.getClientIp(request);
        String composite = code + ":" + verifyToken + ":" + operationType + ":" + env + ":" + clientIp;
        String codeUsedKey = "oauth:github:used-code:" + composite;
        String codeProcessingKey = "oauth:github:processing-code:" + composite;
        String rawCodeUsedKey = "oauth:github:raw-used-code:" + code;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(codeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 GitHub 登录"));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rawCodeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 GitHub 登录"));
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(codeProcessingKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "登录处理中，请勿重复提交"));
        }

        // 速率限制（按 IP）
        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "请求过于频繁"));
        }
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        try {
            // 1) 用 code 换取 access_token
            String tokenUrl = "https://github.com/login/oauth/access_token";

            // GitHub token 交换使用标准表单提交，避免 redirect_uri 被服务端判定为非法 URL
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Accept", "application/json");
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            org.springframework.util.MultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
            form.add("client_id", githubClientId);
            form.add("client_secret", githubClientSecret);
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> tokenReqEntity =
                new org.springframework.http.HttpEntity<>(form, headers);
            org.springframework.http.ResponseEntity<String> tokenRespEntity = restTemplate.exchange(
                tokenUrl,
                org.springframework.http.HttpMethod.POST,
                tokenReqEntity,
                String.class
            );
            String tokenResp = tokenRespEntity.getBody();
            
            if (tokenResp == null || tokenResp.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "GitHub token 接口无响应"));
            }

            JsonNode tokenJson = objectMapper.readTree(tokenResp);
            if (tokenJson.has("error")) {
                String err = tokenJson.path("error_description").asText(tokenJson.path("error").asText());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "GitHub token 错误: " + err));
            }

            String accessToken = tokenJson.path("access_token").asText(null);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 GitHub 返回 access_token"));
            }
            redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
            redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));

            // 2) 用 access_token 获取用户信息
            String userUrl = "https://api.github.com/user";
            org.springframework.http.HttpHeaders userHeaders = new org.springframework.http.HttpHeaders();
            userHeaders.set("Authorization", "Bearer " + accessToken);
            userHeaders.set("Accept", "application/vnd.github+json");
            org.springframework.http.HttpEntity<String> userReqEntity = new org.springframework.http.HttpEntity<>(userHeaders);
            
            org.springframework.http.ResponseEntity<String> userRespEntity = restTemplate.exchange(
                userUrl,
                org.springframework.http.HttpMethod.GET,
                userReqEntity,
                String.class
            );
            String userResp = userRespEntity.getBody();
            
            if (userResp == null || userResp.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "GitHub user 接口无响应"));
            }
            
            JsonNode userJson = objectMapper.readTree(userResp);
            if (userJson.has("message")) {
                String err = userJson.path("message").asText();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "GitHub user 错误: " + err));
            }
            
            String githubId = userJson.path("id").asText(null);
            String githubLogin = userJson.path("login").asText(null);
            String githubEmail = userJson.path("email").asText(null);
            
            if (githubId == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 GitHub 返回用户 ID"));
            }
            
            long startTime = System.currentTimeMillis();

            // bind 操作：为当前登录用户直接写入 GitHub 绑定关系
            if ("bind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "bind 操作需要有效登录态"));
                }

                if (oauthRepo.findByProviderAndUserId("github", user.getId()).isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前账号已绑定 GitHub"));
                }

                java.util.Optional<UserOauthAccount> existingBinding = oauthRepo.findByProviderAndProviderUserId("github", githubId);
                if (existingBinding.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "该 GitHub 账号已被绑定"));
                }

                UserOauthAccount acct = new UserOauthAccount();
                acct.setProvider("github");
                acct.setProviderUserId(githubId);
                acct.setUnionId(null); // GitHub 没有 unionId
                acct.setUserId(user.getId());
                acct.setIsEnabled(true);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                acct.setLinkedAt(now);
                acct.setCreatedAt(now);
                oauthRepo.save(acct);

                sensitiveLogUtil.log(request, user.getId(), "BIND_OAUTH", "github", 
                    cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, startTime);

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("bound", true);
                data.put("provider", "github");
                data.put("openid", githubId);
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "GitHub 绑定成功", data));
            }

            // 3) 查询是否已绑定
            var bound = oauthRepo.findByProviderAndProviderUserId("github", githubId);
            if (bound.isPresent() && Boolean.TRUE.equals(bound.get().getIsEnabled())) {
                UserOauthAccount acct = bound.get();
                var userOpt = userRepository.findById(acct.getUserId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // 检查是否需要 MFA
                    UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
                    boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
                    if (mfaEnabled) {
                        List<String> mfaMethods = resolveOauthMfaMethods(user.getId());
                        if (mfaMethods.isEmpty()) {
                            // do nothing
                        } else {
                        String userAgentReq = rateLimitService.getClientUserAgent(request);
                        String challengeId = mfaService.createChallenge(user.getId(), clientIp, userAgentReq,
                            "github", new HashSet<>(mfaMethods));
                        java.util.Map<String,Object> resp = new java.util.HashMap<>();
                        resp.put("challengeId", challengeId);
                        resp.put("method", mfaMethods.get(0));
                        resp.put("methods", mfaMethods);
                        resp.put("operationType", operationType);
                        resp.put("env", env);
                        return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new ApiResponse<>(201, "需要 MFA 验证", resp));
                        }
                    }

                    // 签发系统 token + session
                    String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
                    String userAgent = rateLimitService.getClientUserAgent(request);
                    UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
                    int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
                    String accessTokenLocal = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

                    // 设置 refreshToken Cookie
                    setRefreshTokenCookie(response, refreshToken);

                    // 更新最后登录时间
                    acct.setLastLoginAt(java.time.LocalDateTime.now());
                    oauthRepo.save(acct);

                    // 记录登录日志
                    sensitiveLogUtil.logLogin(request, user.getId(), "GITHUB", true, null, startTime);

                    java.util.Map<String,Object> data = new java.util.HashMap<>();
                    data.put("accessToken", accessTokenLocal);
                    data.put("user", user);
                    data.put("operationType", operationType);
                    data.put("env", env);
                    return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "登录成功", data));
                }
            }

            // 未绑定
            sensitiveLogUtil.logLogin(request, null, "GITHUB", false, "Not bound", startTime);
            java.util.Map<String,Object> data = createPendingOauthBindData("github", githubId, null, operationType, env);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "未绑定，需要注册或绑定", data));

        } catch (Exception e) {
            logger.error("GitHub oauth callback failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "内部错误"));
        } finally {
            redisTemplate.delete(codeProcessingKey);
        }
    }

    /**
     * Google OAuth 回调处理（登录、绑定）
     */
    private ResponseEntity<ApiResponse<Object>> handleGoogleCallback(OauthCallbackRequest req,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response,
                                                                     String expectedOperation,
                                                                     Authentication authentication) {
        String code = req.getCode();
        String state = req.getState();

        code = decodeIfPercentEncoded(code);
        state = decodeIfPercentEncoded(state);

        if (code == null || code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "code 不能为空"));
        }
        if (state == null || state.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "state 不能为空"));
        }

        String[] stateParts = state.split(";", -1);
        if (stateParts.length != 3 || stateParts[0].isBlank() || stateParts[1].isBlank() || stateParts[2].isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 格式不正确，需为: verifyToken;operation;env"));
        }
        String verifyToken = stateParts[0].trim();
        String operationType = stateParts[1].trim().toLowerCase();
        String env = stateParts[2].trim().toLowerCase();

        if (!("login".equals(operationType) || "bind".equals(operationType))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型不支持，仅支持 login/bind"));
        }
        if (!expectedOperation.equals(operationType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型与当前接口不匹配"));
        }
        if (!("prd".equals(env) || "dev".equals(env))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 环境标识不支持，仅支持 prd/dev"));
        }

        String redirectUri = resolveGoogleRedirectUriByEnv(env);
        if (redirectUri == null) {
            logger.error("No google redirectUri configured for env={}", env);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Google redirectUri 未配置"));
        }
        if (googleClientId == null || googleClientId.isBlank() || googleClientSecret == null || googleClientSecret.isBlank()) {
            logger.error("Google OAuth client_id/client_secret not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Google OAuth 配置缺失"));
        }

        User currentUser = null;
        if (!"login".equals(expectedOperation)) {
            if (authentication == null
                || authentication.getPrincipal() == null
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal().toString())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, expectedOperation + " 操作需要有效登录态"));
            }
            String uuid = authentication.getPrincipal().toString();
            var userOpt = userService.findByUuid(uuid);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "当前登录态对应用户不存在"));
            }
            currentUser = userOpt.get();
        }

        String clientIp = rateLimitService.getClientIp(request);
        String composite = code + ":" + verifyToken + ":" + operationType + ":" + env + ":" + clientIp;
        String codeUsedKey = "oauth:google:used-code:" + composite;
        String codeProcessingKey = "oauth:google:processing-code:" + composite;
        String rawCodeUsedKey = "oauth:google:raw-used-code:" + code;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(codeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 Google 登录"));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rawCodeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 Google 登录"));
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(codeProcessingKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "登录处理中，请勿重复提交"));
        }

        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "请求过于频繁"));
        }
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        long startTime = System.currentTimeMillis();
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");

            org.springframework.util.MultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
            form.add("client_id", googleClientId);
            form.add("client_secret", googleClientSecret);
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);

            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> tokenReqEntity =
                new org.springframework.http.HttpEntity<>(form, headers);

            String tokenResp;
            try {
                org.springframework.http.ResponseEntity<String> tokenRespEntity = restTemplate.exchange(
                    tokenUrl,
                    org.springframework.http.HttpMethod.POST,
                    tokenReqEntity,
                    String.class
                );
                tokenResp = tokenRespEntity.getBody();
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                tokenResp = e.getResponseBodyAsString();
                if (tokenResp == null || tokenResp.isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, "Google token 错误: " + e.getStatusText()));
                }
            }

            if (tokenResp == null || tokenResp.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "Google token 接口无响应"));
            }

            JsonNode tokenJson = objectMapper.readTree(tokenResp);
            if (tokenJson.has("error")) {
                String errCode = tokenJson.path("error").asText();
                String err = tokenJson.path("error_description").asText(tokenJson.path("error").asText());
                if ("invalid_grant".equalsIgnoreCase(errCode)) {
                    redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
                    redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400,
                        "Google token 错误: invalid_grant（授权码已使用/过期，或 redirect_uri 与授权请求不一致）"));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "Google token 错误: " + err));
            }

            String accessToken = tokenJson.path("access_token").asText(null);
            String idToken = tokenJson.path("id_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 Google 返回 access_token"));
            }
            redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
            redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));

            String openid = extractGoogleSubFromIdToken(idToken);
            if (openid == null || openid.isBlank()) {
                openid = fetchGoogleSubFromUserInfo(accessToken);
            }
            if (openid == null || openid.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 Google 返回用户标识"));
            }

            if ("bind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "bind 操作需要有效登录态"));
                }

                if (oauthRepo.findByProviderAndUserId("google", user.getId()).isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前账号已绑定 Google"));
                }

                java.util.Optional<UserOauthAccount> existingBinding = oauthRepo.findByProviderAndProviderUserId("google", openid);
                if (existingBinding.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "该 Google 账号已被绑定"));
                }

                UserOauthAccount acct = new UserOauthAccount();
                acct.setProvider("google");
                acct.setProviderUserId(openid);
                acct.setUnionId(null);
                acct.setUserId(user.getId());
                acct.setIsEnabled(true);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                acct.setLinkedAt(now);
                acct.setCreatedAt(now);
                oauthRepo.save(acct);

                sensitiveLogUtil.log(request, user.getId(), "BIND_OAUTH", "google",
                    cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, startTime);

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("bound", true);
                data.put("provider", "google");
                data.put("openid", openid);
                data.put("message", "Google 绑定成功");
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "Google 绑定成功", data));
            }

            var bound = oauthRepo.findByProviderAndProviderUserId("google", openid);
            if (bound.isPresent() && Boolean.TRUE.equals(bound.get().getIsEnabled())) {
                UserOauthAccount acct = bound.get();
                var userOpt = userRepository.findById(acct.getUserId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();

                    UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
                    boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
                    if (mfaEnabled) {
                        List<String> mfaMethods = resolveOauthMfaMethods(user.getId());
                        if (mfaMethods.isEmpty()) {
                            // do nothing
                        } else {
                        String userAgentReq = rateLimitService.getClientUserAgent(request);
                        String challengeId = mfaService.createChallenge(user.getId(), clientIp, userAgentReq,
                            "google", new HashSet<>(mfaMethods));
                        java.util.Map<String, Object> resp = new java.util.HashMap<>();
                        resp.put("challengeId", challengeId);
                        resp.put("method", mfaMethods.get(0));
                        resp.put("methods", mfaMethods);
                        resp.put("operationType", operationType);
                        resp.put("env", env);
                        return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new ApiResponse<>(201, "需要 MFA 验证", resp));
                        }
                    }

                    String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
                    String userAgent = rateLimitService.getClientUserAgent(request);
                    UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
                    int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
                    String accessTokenLocal = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

                    setRefreshTokenCookie(response, refreshToken);

                    acct.setLastLoginAt(java.time.LocalDateTime.now());
                    oauthRepo.save(acct);

                    sensitiveLogUtil.logLogin(request, user.getId(), "GOOGLE", true, null, startTime);

                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("accessToken", accessTokenLocal);
                    data.put("user", user);
                    data.put("operationType", operationType);
                    data.put("env", env);
                    return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "登录成功", data));
                }
            }

            sensitiveLogUtil.logLogin(request, null, "GOOGLE", false, "Not bound", startTime);
            java.util.Map<String, Object> data = createPendingOauthBindData("google", openid, null, operationType, env);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "未绑定，需要注册或绑定", data));

        } catch (Exception e) {
            logger.error("Google oauth callback failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "内部错误"));
        } finally {
            redisTemplate.delete(codeProcessingKey);
        }
    }

    /**
     * Google OAuth 解绑（回调 + 独立入口）
     */
    private ResponseEntity<ApiResponse<Object>> handleGoogleUnbind(OauthCallbackRequest req,
                                                                   HttpServletRequest request,
                                                                   Authentication authentication) {
        if (req != null) {
            String state = req.getState();
            if (state == null || state.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "state 不能为空"));
            }

            String[] stateParts = state.split(";", -1);
            if (stateParts.length != 3 || stateParts[0].isBlank() || stateParts[1].isBlank() || stateParts[2].isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "state 格式不正确，需为: verifyToken;operation;env"));
            }
            String operationType = stateParts[1].trim().toLowerCase();
            String env = stateParts[2].trim().toLowerCase();
            if (!"unbind".equals(operationType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "state 操作类型与当前接口不匹配"));
            }
            if (!("prd".equals(env) || "dev".equals(env))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, "state 环境标识不支持，仅支持 prd/dev"));
            }
        }

        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = authentication.getPrincipal().toString();
        var userOpt = userService.findByUuid(userUuid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "用户不存在"));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("needVerification", true);
            data.put("message", "解绑 Google 帐号属于敏感操作，需要先完成身份验证");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "需要完成敏感操作验证", data));
        }

        var acctOpt = oauthRepo.findByProviderAndUserId("google", user.getId());
        if (acctOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "未绑定 Google 帐号"));
        }

        var acct = acctOpt.get();
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
        boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
        if (!hasPassword && !hasPasskey) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("canUnbind", false);
            data.put("reason", "last_login_method");
            data.put("message", "无法解绑，这是您的最后一种登录方式");
            data.put("suggestions", java.util.Arrays.asList(
                "请先设置密码或绑定 Passkey",
                "设置密码后可以使用邮箱+密码登录",
                "绑定 Passkey 后可以使用无密码登录"
            ));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "无法解绑最后登录方式", data));
        }

        oauthRepo.delete(acct);
        sensitiveLogUtil.log(request, user.getId(), "UNBIND_OAUTH", "google",
            cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, System.currentTimeMillis());

        if (req != null) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("canUnbind", true);
            data.put("message", "Google 解绑校验完成");
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "Google 解绑成功", data));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "Google 解绑成功"));
    }

    private String resolveGoogleRedirectUriByEnv(String env) {
        List<String> configuredUris = appProperties.getGoogle().getOauth().getRedirectUris();
        if (configuredUris == null || configuredUris.isEmpty()) {
            return null;
        }

        if ("dev".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && (uri.contains("localhost") || uri.contains("127.0.0.1"))) {
                    return uri;
                }
            }
        }

        if ("prd".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && !uri.contains("localhost") && !uri.contains("127.0.0.1")) {
                    return uri;
                }
            }
        }

        for (String uri : configuredUris) {
            if (uri != null && !uri.isBlank()) {
                return uri;
            }
        }
        return null;
    }

    private String extractGoogleSubFromIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return null;
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            JsonNode payloadJson = objectMapper.readTree(payload);

            String sub = payloadJson.path("sub").asText(null);
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to parse google id_token", e);
            return null;
        }
    }

    private String fetchGoogleSubFromUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            String userInfoUrl = "https://openidconnect.googleapis.com/v1/userinfo";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/json");
            org.springframework.http.HttpEntity<String> reqEntity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> resp = restTemplate.exchange(
                userInfoUrl,
                org.springframework.http.HttpMethod.GET,
                reqEntity,
                String.class
            );
            String body = resp.getBody();
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode userInfoJson = objectMapper.readTree(body);
            String sub = userInfoJson.path("sub").asText(null);
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to fetch google userinfo", e);
            return null;
        }
    }

    private String decodeIfPercentEncoded(String value) {
        if (value == null || value.isBlank() || !value.contains("%")) {
            return value;
        }
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            logger.warn("Ignore malformed percent-encoded oauth value: {}", value);
            return value;
        }
    }

    /**
     * Microsoft OAuth 回调处理（登录、绑定、解绑）
     */
    private ResponseEntity<ApiResponse<Object>> handleMicrosoftCallback(OauthCallbackRequest req,
                                                                        HttpServletRequest request,
                                                                        HttpServletResponse response,
                                                                        String expectedOperation,
                                                                        Authentication authentication) {
        String code = req.getCode();
        String state = req.getState();
        String codeVerifier = req.getCodeVerifier();

        if (code == null || code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "code 不能为空"));
        }
        if (state == null || state.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "state 不能为空"));
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "codeVerifier 不能为空"));
        }

        // state 格式：verifyToken;operation;env
        String[] stateParts = state.split(";", -1);
        if (stateParts.length != 3 || stateParts[0].isBlank() || stateParts[1].isBlank() || stateParts[2].isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 格式不正确，需为: verifyToken;operation;env"));
        }
        String verifyToken = stateParts[0].trim();
        String operationType = stateParts[1].trim().toLowerCase();
        String env = stateParts[2].trim().toLowerCase();

        if (!("login".equals(operationType) || "bind".equals(operationType) || "unbind".equals(operationType))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型不支持，仅支持 login/bind/unbind"));
        }
        if (!expectedOperation.equals(operationType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 操作类型与当前接口不匹配"));
        }
        if (!("prd".equals(env) || "dev".equals(env))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "state 环境标识不支持，仅支持 prd/dev"));
        }

        String redirectUri = resolveMicrosoftRedirectUriByEnv(env);
        if (redirectUri == null) {
            logger.error("No microsoft redirectUri configured for env={}", env);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Microsoft redirectUri 未配置"));
        }

        String tenantId = (microsoftTenantId == null || microsoftTenantId.isBlank()) ? "common" : microsoftTenantId.trim();
        if (microsoftClientId == null || microsoftClientId.isBlank()) {
            logger.error("Microsoft OAuth client_id not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Microsoft OAuth client_id 未配置"));
        }

        User currentUser = null;
        if (!"login".equals(expectedOperation)) {
            if (authentication == null
                || authentication.getPrincipal() == null
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal().toString())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, expectedOperation + " 操作需要有效登录态"));
            }
            String uuid = authentication.getPrincipal().toString();
            var userOpt = userService.findByUuid(uuid);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "当前登录态对应用户不存在"));
            }
            currentUser = userOpt.get();
        }

        String clientIp = rateLimitService.getClientIp(request);
        String composite = code + ":" + verifyToken + ":" + operationType + ":" + env + ":" + clientIp;
        String codeUsedKey = "oauth:microsoft:used-code:" + composite;
        String codeProcessingKey = "oauth:microsoft:processing-code:" + composite;
        String rawCodeUsedKey = "oauth:microsoft:raw-used-code:" + code;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(codeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 Microsoft 登录"));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rawCodeUsedKey))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "授权码已使用，请重新发起 Microsoft 登录"));
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(codeProcessingKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "登录处理中，请勿重复提交"));
        }

        // 速率限制（按 IP）
        if (!rateLimitService.isIpAllowed(clientIp, RateLimitService.TYPE_LOGIN)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiResponse<>(429, "请求过于频繁"));
        }
        rateLimitService.recordIpRequest(clientIp, RateLimitService.TYPE_LOGIN);

        long startTime = System.currentTimeMillis();
        try {
            String tokenUrl = String.format(
                "https://login.microsoftonline.com/%s/oauth2/v2.0/token",
                URLEncoder.encode(tenantId, StandardCharsets.UTF_8)
            );

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");

            org.springframework.util.MultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
            form.add("client_id", microsoftClientId);
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            form.add("code_verifier", codeVerifier);
            if (microsoftClientSecret != null && !microsoftClientSecret.isBlank()) {
                form.add("client_secret", microsoftClientSecret);
            }

            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> tokenReqEntity =
                new org.springframework.http.HttpEntity<>(form, headers);
            org.springframework.http.ResponseEntity<String> tokenRespEntity = restTemplate.exchange(
                tokenUrl,
                org.springframework.http.HttpMethod.POST,
                tokenReqEntity,
                String.class
            );
            String tokenResp = tokenRespEntity.getBody();
            if (tokenResp == null || tokenResp.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "Microsoft token 接口无响应"));
            }

            JsonNode tokenJson = objectMapper.readTree(tokenResp);
            if (tokenJson.has("error")) {
                String err = tokenJson.path("error_description").asText(tokenJson.path("error").asText());
                if (err != null && err.toLowerCase().contains("already redeemed")) {
                    redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
                    redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "Microsoft token 错误: " + err));
            }

            String accessToken = tokenJson.path("access_token").asText(null);
            String idToken = tokenJson.path("id_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 Microsoft 返回 access_token"));
            }
            redisTemplate.opsForValue().set(codeUsedKey, "1", Duration.ofMinutes(10));
            redisTemplate.opsForValue().set(rawCodeUsedKey, "1", Duration.ofMinutes(10));

            String openid = extractMicrosoftOpenidFromIdToken(idToken);
            if (openid == null || openid.isBlank()) {
                openid = fetchMicrosoftOpenidFromUserInfo(accessToken);
            }
            if (openid == null || openid.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, "未从 Microsoft 返回用户标识"));
            }

            // bind 操作：为当前登录用户直接写入 Microsoft 绑定关系
            if ("bind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "bind 操作需要有效登录态"));
                }

                if (oauthRepo.findByProviderAndUserId("microsoft", user.getId()).isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前账号已绑定 Microsoft"));
                }

                java.util.Optional<UserOauthAccount> existingBinding = oauthRepo.findByProviderAndProviderUserId("microsoft", openid);
                if (existingBinding.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "该 Microsoft 账号已被绑定"));
                }

                UserOauthAccount acct = new UserOauthAccount();
                acct.setProvider("microsoft");
                acct.setProviderUserId(openid);
                acct.setUnionId(null);
                acct.setUserId(user.getId());
                acct.setIsEnabled(true);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                acct.setLinkedAt(now);
                acct.setCreatedAt(now);
                oauthRepo.save(acct);

                sensitiveLogUtil.log(request, user.getId(), "BIND_OAUTH", "microsoft",
                    cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, startTime);

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("bound", true);
                data.put("provider", "microsoft");
                data.put("openid", openid);
                data.put("message", "Microsoft 绑定成功");
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "Microsoft 绑定成功", data));
            }

            if ("unbind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "unbind 操作需要有效登录态"));
                }

                String callbackIdentity = openid;
                String uuid = user.getUuid();
                String clientIpReq = rateLimitService.getClientIp(request);
                if (!sensitiveOperationService.isVerified(uuid, clientIpReq)) {
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("needVerification", true);
                    data.put("message", "解绑 Microsoft 帐号属于敏感操作，需要先完成身份验证");
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "需要完成敏感操作验证", data));
                }

                var acctOpt = oauthRepo.findByProviderAndUserId("microsoft", user.getId());
                if (acctOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "未绑定 Microsoft 帐号"));
                }
                var acct = acctOpt.get();

                String boundIdentity = acct.getProviderUserId();
                if (!callbackIdentity.equals(boundIdentity)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前授权的 Microsoft 账号与已绑定账号不一致"));
                }

                boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
                boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
                if (!hasPassword && !hasPasskey) {
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("canUnbind", false);
                    data.put("reason", "last_login_method");
                    data.put("message", "无法解绑，这是您的最后一种登录方式");
                    data.put("suggestions", java.util.Arrays.asList(
                        "请先设置密码或绑定 Passkey",
                        "设置密码后可以使用邮箱+密码登录",
                        "绑定 Passkey 后可以使用无密码登录"
                    ));
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "无法解绑最后登录方式", data));
                }

                oauthRepo.delete(acct);
                sensitiveLogUtil.log(request, user.getId(), "UNBIND_OAUTH", "microsoft",
                    cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, System.currentTimeMillis());

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("canUnbind", true);
                data.put("message", "Microsoft 解绑校验完成");
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "Microsoft 解绑成功", data));
            }

            // login 操作：查询是否已绑定
            var bound = oauthRepo.findByProviderAndProviderUserId("microsoft", openid);
            if (bound.isPresent() && Boolean.TRUE.equals(bound.get().getIsEnabled())) {
                UserOauthAccount acct = bound.get();
                var userOpt = userRepository.findById(acct.getUserId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();

                    UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
                    boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
                    if (mfaEnabled) {
                        List<String> mfaMethods = resolveOauthMfaMethods(user.getId());
                        if (mfaMethods.isEmpty()) {
                            // do nothing
                        } else {
                        String userAgentReq = rateLimitService.getClientUserAgent(request);
                        String challengeId = mfaService.createChallenge(user.getId(), clientIp, userAgentReq,
                            "microsoft", new HashSet<>(mfaMethods));
                        java.util.Map<String, Object> resp = new java.util.HashMap<>();
                        resp.put("challengeId", challengeId);
                        resp.put("method", mfaMethods.get(0));
                        resp.put("methods", mfaMethods);
                        resp.put("operationType", operationType);
                        resp.put("env", env);
                        return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new ApiResponse<>(201, "需要 MFA 验证", resp));
                        }
                    }

                    String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
                    String userAgent = rateLimitService.getClientUserAgent(request);
                    UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
                    int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
                    String accessTokenLocal = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

                    setRefreshTokenCookie(response, refreshToken);

                    acct.setLastLoginAt(java.time.LocalDateTime.now());
                    oauthRepo.save(acct);

                    sensitiveLogUtil.logLogin(request, user.getId(), "MICROSOFT", true, null, startTime);

                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("accessToken", accessTokenLocal);
                    data.put("user", user);
                    data.put("operationType", operationType);
                    data.put("env", env);
                    return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "登录成功", data));
                }
            }

            sensitiveLogUtil.logLogin(request, null, "MICROSOFT", false, "Not bound", startTime);
            java.util.Map<String, Object> data = createPendingOauthBindData("microsoft", openid, null, operationType, env);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "未绑定，需要注册或绑定", data));

        } catch (Exception e) {
            logger.error("Microsoft oauth callback failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "内部错误"));
        } finally {
            redisTemplate.delete(codeProcessingKey);
        }
    }

    /**
     * 解绑 Microsoft（前端独立解绑入口）
     */
    private ResponseEntity<ApiResponse<Object>> handleMicrosoftUnbind(HttpServletRequest request,
                                                                      Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = authentication.getPrincipal().toString();
        var userOpt = userService.findByUuid(userUuid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "用户不存在"));
        }

        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);

        if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("needVerification", true);
            data.put("message", "解绑 Microsoft 帐号属于敏感操作，需要先完成身份验证");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "需要完成敏感操作验证", data));
        }

        var acctOpt = oauthRepo.findByProviderAndUserId("microsoft", user.getId());
        if (acctOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "未绑定 Microsoft 帐号"));
        }

        var acct = acctOpt.get();
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
        boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
        if (!hasPassword && !hasPasskey) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("canUnbind", false);
            data.put("reason", "last_login_method");
            data.put("message", "无法解绑，这是您的最后一种登录方式");
            data.put("suggestions", java.util.Arrays.asList(
                "请先设置密码或绑定 Passkey",
                "设置密码后可以使用邮箱+密码登录",
                "绑定 Passkey 后可以使用无密码登录"
            ));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "无法解绑最后登录方式", data));
        }

        oauthRepo.delete(acct);
        sensitiveLogUtil.log(request, user.getId(), "UNBIND_OAUTH", "microsoft",
            cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "Microsoft 解绑成功"));
    }

    private String resolveMicrosoftRedirectUriByEnv(String env) {
        List<String> configuredUris = appProperties.getMicrosoft().getOauth().getRedirectUris();
        if (configuredUris == null || configuredUris.isEmpty()) {
            return null;
        }

        if ("dev".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && (uri.contains("localhost") || uri.contains("127.0.0.1"))) {
                    return uri;
                }
            }
        }

        if ("prd".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && !uri.contains("localhost") && !uri.contains("127.0.0.1")) {
                    return uri;
                }
            }
        }

        for (String uri : configuredUris) {
            if (uri != null && !uri.isBlank()) {
                return uri;
            }
        }
        return null;
    }

    private String extractMicrosoftOpenidFromIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return null;
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            JsonNode payloadJson = objectMapper.readTree(payload);

            String oid = payloadJson.path("oid").asText(null);
            if (oid != null && !oid.isBlank()) {
                return oid;
            }
            String sub = payloadJson.path("sub").asText(null);
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to parse microsoft id_token", e);
            return null;
        }
    }

    private String fetchMicrosoftOpenidFromUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            String userInfoUrl = "https://graph.microsoft.com/oidc/userinfo";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/json");
            org.springframework.http.HttpEntity<String> reqEntity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> resp = restTemplate.exchange(
                userInfoUrl,
                org.springframework.http.HttpMethod.GET,
                reqEntity,
                String.class
            );
            String body = resp.getBody();
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode userInfoJson = objectMapper.readTree(body);
            String oid = userInfoJson.path("oid").asText(null);
            if (oid != null && !oid.isBlank()) {
                return oid;
            }
            String sub = userInfoJson.path("sub").asText(null);
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to fetch microsoft userinfo", e);
            return null;
        }
    }

    /**
     * GitHub OAuth 解绑（前端仅传 state）
     */
    private ResponseEntity<ApiResponse<Object>> handleGithubUnbind(OauthCallbackRequest req,
                                                                   HttpServletRequest request,
                                                                   Authentication authentication) {
        String state = req.getState();
        
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "未认证"));
        }

        String userUuid = authentication.getPrincipal().toString();
        var userOpt = userService.findByUuid(userUuid);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "用户不存在"));
        }
        
        User user = userOpt.get();
        String clientIp = rateLimitService.getClientIp(request);
        
        // 检查敏感操作验证
        if (!sensitiveOperationService.isVerified(userUuid, clientIp)) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("needVerification", true);
            data.put("message", "解绑 GitHub 帐号属于敏感操作，需要先完成身份验证");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "需要完成敏感操作验证", data));
        }

        var acctOpt = oauthRepo.findByProviderAndUserId("github", user.getId());
        if (acctOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, "未绑定 GitHub 帐号"));
        }
        
        var acct = acctOpt.get();

        // 检查是否为最后可登录方式
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
        boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
        
        if (!hasPassword && !hasPasskey) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("canUnbind", false);
            data.put("reason", "last_login_method");
            data.put("message", "无法解绑，这是您的最后一种登录方式");
            data.put("suggestions", java.util.Arrays.asList(
                "请先设置密码或绑定 Passkey",
                "设置密码后可以使用邮箱+密码登录",
                "绑定 Passkey 后可以使用无密码登录"
            ));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "无法解绑最后登录方式", data));
        }

        // 删除绑定
        oauthRepo.delete(acct);
        
        // 记录敏感操作日志
        sensitiveLogUtil.log(request, user.getId(), "UNBIND_OAUTH", "github", 
            cn.ksuser.api.entity.UserSensitiveLog.OperationResult.SUCCESS, null, System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "GitHub 解绑成功"));
    }

    /**
     * 根据环境选择 GitHub redirectUri
     */
    private String resolveGithubRedirectUriByEnv(String env) {
        List<String> configuredUris = appProperties.getGithub().getOauth().getRedirectUris();
        if (configuredUris == null || configuredUris.isEmpty()) {
            return null;
        }

        if ("dev".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && (uri.contains("localhost") || uri.contains("127.0.0.1"))) {
                    return uri;
                }
            }
        }

        if ("prd".equals(env)) {
            for (String uri : configuredUris) {
                if (uri != null && !uri.contains("localhost") && !uri.contains("127.0.0.1")) {
                    return uri;
                }
            }
        }

        for (String uri : configuredUris) {
            if (uri != null && !uri.isBlank()) {
                return uri;
            }
        }
        return null;
    }

    private java.util.Map<String, Object> createPendingOauthBindData(String provider,
                                                                     String providerUserId,
                                                                     String unionId,
                                                                     String operationType,
                                                                     String env) throws Exception {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        java.util.Map<String, Object> pending = new java.util.HashMap<>();
        pending.put("provider", provider);
        pending.put("providerUserId", providerUserId);
        pending.put("unionId", unionId);
        redisTemplate.opsForValue().set(pendingOauthKey(token), objectMapper.writeValueAsString(pending), Duration.ofMinutes(15));

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("needBind", true);
        data.put("provider", provider);
        data.put("oauthBindToken", token);
        data.put("openid", providerUserId);
        data.put("operationType", operationType);
        data.put("env", env);
        data.put("message", "该 " + providerDisplayName(provider) + " 账号尚未绑定，请绑定已有 Ksuser 账号或注册新账号");
        return data;
    }

    private String pendingOauthKey(String token) {
        return "oauth:pending-bind:" + token;
    }

    private PendingOauthIdentity resolvePendingOauthIdentity(String provider, String bindToken) {
        String normalizedProvider = normalizeOauthProvider(provider);
        if (normalizedProvider == null) {
            throw new IllegalArgumentException("不支持的第三方账号类型");
        }

        if (bindToken != null && !bindToken.isBlank()) {
            String raw = redisTemplate.opsForValue().get(pendingOauthKey(bindToken.trim()));
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("第三方登录状态已过期，请重新授权");
            }
            try {
                JsonNode json = objectMapper.readTree(raw);
                String tokenProvider = normalizeOauthProvider(json.path("provider").asText(null));
                String providerUserId = json.path("providerUserId").asText(null);
                String unionId = json.path("unionId").asText(null);
                if (!normalizedProvider.equals(tokenProvider) || providerUserId == null || providerUserId.isBlank()) {
                    throw new IllegalArgumentException("第三方登录状态无效，请重新授权");
                }
                if (unionId != null && unionId.isBlank()) {
                    unionId = null;
                }
                return new PendingOauthIdentity(providerUserId, unionId);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("第三方登录状态无效，请重新授权");
            }
        }

        throw new IllegalArgumentException("第三方登录状态缺失，请重新授权");
    }

    private void consumePendingOauthIdentity(String bindToken) {
        if (bindToken != null && !bindToken.isBlank()) {
            redisTemplate.delete(pendingOauthKey(bindToken.trim()));
        }
    }

    private ResponseEntity<ApiResponse<Object>> ensureCanLinkOauth(String provider, User user, PendingOauthIdentity pending) {
        String displayName = providerDisplayName(provider);
        if (oauthRepo.findByProviderAndUserId(provider, user.getId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前账号已绑定 " + displayName));
        }
        if (findExistingOauthBinding(provider, pending).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "该 " + displayName + " 账号已被绑定"));
        }
        return null;
    }

    private java.util.Optional<UserOauthAccount> findExistingOauthBinding(String provider, PendingOauthIdentity pending) {
        if ("qq".equals(provider)) {
            if (pending.unionId() == null || pending.unionId().isBlank()) {
                throw new IllegalArgumentException("QQ unionid 缺失，请重新授权");
            }
            return oauthRepo.findByProviderAndUnionId(provider, pending.unionId());
        }
        if (pending.unionId() != null && !pending.unionId().isBlank()) {
            return oauthRepo.findByProviderAndUnionId(provider, pending.unionId());
        }
        return oauthRepo.findByProviderAndProviderUserId(provider, pending.providerUserId());
    }

    private void linkOauthAccount(String provider, User user, PendingOauthIdentity pending) {
        UserOauthAccount acct = new UserOauthAccount();
        acct.setProvider(provider);
        acct.setProviderUserId(pending.providerUserId());
        acct.setUnionId(pending.unionId());
        acct.setUserId(user.getId());
        acct.setIsEnabled(true);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        acct.setLinkedAt(now);
        acct.setCreatedAt(now);
        oauthRepo.save(acct);
    }

    private ResponseEntity<ApiResponse<Object>> issueOauthLoginResponse(User user,
                                                                        String provider,
                                                                        HttpServletRequest request,
                                                                        HttpServletResponse response,
                                                                        long startTime,
                                                                        String message) {
        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
        if (mfaEnabled) {
            List<String> mfaMethods = resolveOauthMfaMethods(user.getId());
            if (!mfaMethods.isEmpty()) {
                String clientIp = rateLimitService.getClientIp(request);
                String userAgentReq = rateLimitService.getClientUserAgent(request);
                String challengeId = mfaService.createChallenge(user.getId(), clientIp, userAgentReq,
                    provider, new HashSet<>(mfaMethods));
                java.util.Map<String, Object> resp = new java.util.HashMap<>();
                resp.put("challengeId", challengeId);
                resp.put("method", mfaMethods.get(0));
                resp.put("methods", mfaMethods);
                resp.put("provider", provider);
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(201, "需要 MFA 验证", resp));
            }
        }

        String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
        String userAgent = rateLimitService.getClientUserAgent(request);
        UserSession session = userSessionService.createSession(user, refreshToken, rateLimitService.getClientIp(request), userAgent);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessTokenLocal = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);
        setRefreshTokenCookie(response, refreshToken);
        sensitiveLogUtil.logLogin(request, user.getId(), provider.toUpperCase(Locale.ROOT), true, null, startTime);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("accessToken", accessTokenLocal);
        data.put("user", user);
        data.put("provider", provider);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, message, data));
    }

    private String resolveProviderFromRequest(HttpServletRequest request, String explicitProvider) {
        String normalized = normalizeOauthProvider(explicitProvider);
        if (normalized != null) {
            return normalized;
        }

        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("oauth".equals(parts[i])) {
                return normalizeOauthProvider(parts[i + 1]);
            }
        }
        return null;
    }

    private String normalizeOauthProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "qq", "github", "microsoft", "google" -> normalized;
            default -> null;
        };
    }

    private String providerDisplayName(String provider) {
        return switch (provider) {
            case "qq" -> "QQ";
            case "github" -> "GitHub";
            case "microsoft" -> "Microsoft";
            case "google" -> "Google";
            default -> "第三方";
        };
    }

    private record PendingOauthIdentity(String providerUserId, String unionId) {}

    private record QqMobileIdentity(String openid, String unionid) {}

    private UserSettings getOrCreateUserSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserSettings settings = new UserSettings();
                settings.setUserId(userId);
                settings.setMfaEnabled(false);
                settings.setDetectUnusualLogin(true);
                settings.setNotifySensitiveActionEmail(true);
                settings.setSubscribeNewsEmail(false);
                settings.setPreferredMfaMethod("totp");
                settings.setPreferredSensitiveMethod("password");
                return userSettingsRepository.save(settings);
            });
    }

    private List<String> resolveOauthMfaMethods(Long userId) {
        List<String> methods = new java.util.ArrayList<>();
        if (totpService.isTotpEnabled(userId)) {
            methods.add("totp");
        }
        if (!userPasskeyRepository.findByUserId(userId).isEmpty()) {
            methods.add("passkey");
        }
        methods.add("qr");

        String preferredMfaMethod = userSettingsRepository.findByUserId(userId)
            .map(UserSettings::getPreferredMfaMethod)
            .orElse("totp");
        String normalizedPreferred = normalizeMfaPreference(preferredMfaMethod);
        methods.sort((a, b) -> Integer.compare(methodPriority(a, normalizedPreferred), methodPriority(b, normalizedPreferred)));
        return methods;
    }

    private String normalizeMfaPreference(String raw) {
        if (raw == null) {
            return "totp";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return ("totp".equals(normalized) || "passkey".equals(normalized) || "qr".equals(normalized)) ? normalized : "totp";
    }

    private int methodPriority(String method, String preferred) {
        if (method == null) {
            return Integer.MAX_VALUE;
        }
        if (preferred != null && method.equals(preferred)) {
            return 0;
        }
        return switch (method) {
            case "totp" -> 1;
            case "passkey" -> 2;
            case "qr" -> 3;
            default -> 10;
        };
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        boolean hasToken = token != null && !token.isEmpty();
        org.springframework.http.ResponseCookie responseCookie = org.springframework.http.ResponseCookie
            .from("refreshToken", hasToken ? token : "")
            .httpOnly(true)
            .secure(!appProperties.isDebug())
            .path("/")
            .maxAge(hasToken ? 604800 : 0)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", responseCookie.toString());
    }
}
