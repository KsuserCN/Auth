package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.OauthCallbackRequest;
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
import java.util.HashMap;
import java.util.List;
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
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();
        
        // 2. 检查是否绑定了 Passkey
        boolean hasPasskey = !userPasskeyRepository.findByUserId(user.getId()).isEmpty();
        
        // 3. 检查是否绑定了其他 OAuth 提供商（未来扩展）
        // 目前只有 QQ OAuth，所以不需要检查
        
        // 如果既没有密码也没有 Passkey，则不允许解绑（因为解绑后用户将无法登录）
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

        List<String> providers = Arrays.asList("wechat", "qq", "microsoft", "github");
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
        String redirectUri = req.getRedirectUri();
        String state = req.getState();

        if (code == null || code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "code 不能为空"));
        }
        if (redirectUri == null || redirectUri.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "redirectUri 不能为空"));
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

        // 检查 redirectUri 白名单
        if (!appProperties.getQq().getOauth().getRedirectUris().contains(redirectUri)) {
            logger.warn("Unallowed redirectUri: {}", redirectUri);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "不允许的 redirectUri"));
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

            // bind 操作：为当前登录用户直接写入 QQ 绑定关系
            if ("bind".equals(operationType)) {
                User user = currentUser;
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "bind 操作需要有效登录态"));
                }

                if (oauthRepo.findByProviderAndUserId("qq", user.getId()).isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "当前账号已绑定 QQ"));
                }

                java.util.Optional<UserOauthAccount> existingBinding;
                if (unionid != null) {
                    existingBinding = oauthRepo.findByProviderAndUnionId("qq", unionid);
                } else {
                    existingBinding = oauthRepo.findByProviderAndProviderUserId("qq", openid);
                }
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

                String callbackIdentity = unionid != null ? unionid : openid;
                if (callbackIdentity == null || callbackIdentity.isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "未从 QQ 返回可用于解绑校验的身份标识"));
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

                String boundIdentity = (acct.getUnionId() != null && !acct.getUnionId().isBlank())
                    ? acct.getUnionId()
                    : acct.getProviderUserId();
                if (!callbackIdentity.equals(boundIdentity)) {
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
            var bound = oauthRepo.findByProviderAndProviderUserId("qq", openid);
            if (bound.isPresent() && Boolean.TRUE.equals(bound.get().getIsEnabled())) {
                UserOauthAccount acct = bound.get();
                var userOpt = userRepository.findById(acct.getUserId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // 检查是否需要 MFA（用户设置 + TOTP 已启用）
                    User userEntity = user;
                    UserSettings settings = userSettingsRepository.findByUserId(userEntity.getId()).orElse(null);
                    boolean mfaEnabled = settings != null && Boolean.TRUE.equals(settings.getMfaEnabled());
                    if (mfaEnabled && totpService.isTotpEnabled(userEntity.getId())) {
                        String clientIpReq = rateLimitService.getClientIp(request);
                        String userAgentReq = rateLimitService.getClientUserAgent(request);
                        String challengeId = mfaService.createChallenge(userEntity.getId(), clientIpReq, userAgentReq);
                        // 记录为 QQ_MFA，表示需要 MFA 验证
                        sensitiveLogUtil.logLogin(request, userEntity.getId(), "QQ_MFA", true, null, startTime);
                        java.util.Map<String,Object> resp = new java.util.HashMap<>();
                        resp.put("challengeId", challengeId);
                        resp.put("method", "totp");
                        resp.put("operationType", operationType);
                        resp.put("env", env);
                        return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new ApiResponse<>(201, "需要 TOTP 验证", resp));
                    }

                    // 签发系统 token + session（与现有登录流程一致）
                    String refreshToken = jwtUtil.generateRefreshToken(user.getUuid());
                    String userAgent = rateLimitService.getClientUserAgent(request);
                    UserSession session = userSessionService.createSession(user, refreshToken, clientIp, userAgent);
                    int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
                    String accessTokenLocal = jwtUtil.generateAccessToken(user.getUuid(), session.getId(), sessionVersion);

                    // 设置 refreshToken Cookie（复制 AuthController 的实现）
                    jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refreshToken", refreshToken);
                    cookie.setHttpOnly(true);
                    cookie.setSecure(!appProperties.isDebug());
                    cookie.setPath("/");
                    cookie.setMaxAge(604800);
                    response.addCookie(cookie);
                    String sameSiteValue = "SameSite=Strict";
                    String setCookieHeader = String.format("refreshToken=%s; Path=/; HttpOnly; %s%s; Max-Age=%d",
                        refreshToken != null ? refreshToken : "",
                        cookie.getSecure() ? "Secure; " : "",
                        sameSiteValue,
                        cookie.getMaxAge()
                    );
                    response.addHeader("Set-Cookie", setCookieHeader);

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
            java.util.Map<String,Object> data = new java.util.HashMap<>();
            data.put("needBind", true);
            data.put("openid", openid);
            data.put("operationType", operationType);
            data.put("env", env);
            data.put("message", "未绑定，请使用绑定或注册接口完成账号关联");
            // 202 表示已接受，需前端引导用户注册或绑定
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, "未绑定，需要注册或绑定", data));

        } catch (Exception e) {
            logger.error("QQ oauth callback failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "内部错误"));
        } finally {
            redisTemplate.delete(codeProcessingKey);
        }
    }

    /**
     * 绑定已有账号（前端传入 openid、email、password）
     */
    @PostMapping("/qq/bind-existing")
    public ResponseEntity<ApiResponse<Object>> bindExisting(@RequestBody cn.ksuser.api.dto.OauthBindRequest req) {
        String openid = req.getOpenid();
        String email = req.getEmail();
        String password = req.getPassword();
        if (openid == null || openid.isEmpty() || email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "参数缺失"));
        }

        // 检查 openid 是否已被绑定
        if (oauthRepo.findByProviderAndProviderUserId("qq", openid).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "openid 已被绑定"));
        }

        // 验证用户凭据（使用 UserService.login）
        var loginOpt = userService.login(email, password);
        if (loginOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, "邮箱或密码错误"));
        }
        var user = loginOpt.get();

        // 绑定 openid
        UserOauthAccount acct = new UserOauthAccount();
        acct.setProvider("qq");
        acct.setProviderUserId(openid);
        acct.setUserId(user.getId());
        acct.setIsEnabled(true);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        acct.setLinkedAt(now);
        acct.setCreatedAt(now);
        oauthRepo.save(acct);

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "绑定成功"));
    }

    /**
     * 注册并绑定（前端传入 openid, username, email, password）
     */
    @PostMapping("/qq/register-bind")
    public ResponseEntity<ApiResponse<Object>> registerAndBind(@RequestBody cn.ksuser.api.dto.OauthRegisterBindRequest req,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        String openid = req.getOpenid();
        String username = req.getUsername();
        String email = req.getEmail();
        String password = req.getPassword();
        if (openid == null || openid.isEmpty() || username == null || username.isEmpty() || email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, "参数缺失"));
        }

        // 检查 openid 是否已被绑定
        if (oauthRepo.findByProviderAndProviderUserId("qq", openid).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, "openid 已被绑定"));
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

        // 绑定 openid
        UserOauthAccount acct = new UserOauthAccount();
        acct.setProvider("qq");
        acct.setProviderUserId(openid);
        acct.setUserId(newUser.getId());
        acct.setIsEnabled(true);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        acct.setLinkedAt(now);
        acct.setCreatedAt(now);
        oauthRepo.save(acct);

        // 签发 token 并创建 session（登录成功）
        String refreshToken = jwtUtil.generateRefreshToken(newUser.getUuid());
        String userAgent = rateLimitService.getClientUserAgent(request);
        UserSession session = userSessionService.createSession(newUser, refreshToken, rateLimitService.getClientIp(request), userAgent);
        int sessionVersion = session.getSessionVersion() == null ? 0 : session.getSessionVersion();
        String accessTokenLocal = jwtUtil.generateAccessToken(newUser.getUuid(), session.getId(), sessionVersion);

        // 设置 refreshToken Cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(!appProperties.isDebug());
        cookie.setPath("/");
        cookie.setMaxAge(604800);
        response.addCookie(cookie);
        String sameSiteValue = "SameSite=Strict";
        String setCookieHeader = String.format("refreshToken=%s; Path=/; HttpOnly; %s%s; Max-Age=%d",
            refreshToken != null ? refreshToken : "",
            cookie.getSecure() ? "Secure; " : "",
            sameSiteValue,
            cookie.getMaxAge()
        );
        response.addHeader("Set-Cookie", setCookieHeader);

        // 记录登录日志
        sensitiveLogUtil.logLogin(request, newUser.getId(), "QQ", true, null, System.currentTimeMillis());

        java.util.Map<String,Object> data = new java.util.HashMap<>();
        data.put("accessToken", accessTokenLocal);
        data.put("user", newUser);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse<>(200, "注册并绑定并登录成功", data));
    }
}
