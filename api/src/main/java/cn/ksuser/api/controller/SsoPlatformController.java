package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.SsoAuthorizeApproveResponse;
import cn.ksuser.api.dto.SsoAuthorizeContextResponse;
import cn.ksuser.api.dto.SsoAuthorizeRequest;
import cn.ksuser.api.dto.SsoClientCreateRequest;
import cn.ksuser.api.dto.SsoClientCreateResponse;
import cn.ksuser.api.dto.SsoClientResponse;
import cn.ksuser.api.dto.SsoClientUpdateRequest;
import cn.ksuser.api.dto.SsoClientsOverviewResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.service.SsoPlatformService;
import cn.ksuser.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/sso")
public class SsoPlatformController {
    private static final long LOGO_MAX_SIZE_BYTES = 3L * 1024 * 1024;
    private static final String LOGO_STORAGE_DIR = "static/sso-logos";

    private final SsoPlatformService ssoPlatformService;
    private final UserService userService;
    private final AppProperties appProperties;

    public SsoPlatformController(SsoPlatformService ssoPlatformService,
                                 UserService userService,
                                 AppProperties appProperties) {
        this.ssoPlatformService = ssoPlatformService;
        this.userService = userService;
        this.appProperties = appProperties;
    }

    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<SsoClientsOverviewResponse>> listClients(Authentication authentication) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功", ssoPlatformService.listClients(user)));
    }

    @PostMapping("/clients")
    public ResponseEntity<ApiResponse<SsoClientCreateResponse>> createClient(@RequestBody SsoClientCreateRequest request,
                                                                             Authentication authentication) {
        User user = requireUser(authentication);
        SsoClientCreateResponse response = ssoPlatformService.createClient(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ApiResponse<>(201, "创建成功，请妥善保存 ClientSecret", response));
    }

    @PutMapping("/clients/{clientId}")
    public ResponseEntity<ApiResponse<SsoClientResponse>> updateClient(@PathVariable String clientId,
                                                                       @RequestBody SsoClientUpdateRequest request,
                                                                       Authentication authentication) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(new ApiResponse<>(200, "更新成功", ssoPlatformService.updateClient(user, clientId, request)));
    }

    @DeleteMapping("/clients/{clientId}")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable String clientId,
                                                          Authentication authentication) {
        User user = requireUser(authentication);
        ssoPlatformService.deleteClient(user, clientId);
        return ResponseEntity.ok(new ApiResponse<>(200, "删除成功"));
    }

    @PostMapping(value = "/clients/{clientId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SsoClientResponse>> uploadClientLogo(@PathVariable String clientId,
                                                                            @RequestPart("file") MultipartFile file,
                                                                            Authentication authentication) {
        User user = requireUser(authentication);
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "请选择 Logo 文件"));
        }
        if (file.getSize() > LOGO_MAX_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "Logo 文件不能超过 3MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "仅支持图片文件"));
        }

        String extension = resolveFileExtension(contentType, file.getOriginalFilename());
        if (extension == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "不支持的图片格式"));
        }

        try {
            Path logoDir = Paths.get(LOGO_STORAGE_DIR).toAbsolutePath().normalize();
            Files.createDirectories(logoDir);

            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path target = logoDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String logoUrl = getStaticHostPrefix() + "/static/sso-logos/" + filename;
            SsoClientResponse response = ssoPlatformService.updateClientLogo(user, clientId, logoUrl);
            return ResponseEntity.ok(new ApiResponse<>(200, "Logo 上传成功", response));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Logo 上传失败"));
        }
    }

    @GetMapping("/authorize/context")
    public ResponseEntity<ApiResponse<SsoAuthorizeContextResponse>> authorizeContext(
        @RequestParam(name = "client_id") String clientId,
        @RequestParam(name = "redirect_uri") String redirectUri,
        @RequestParam(name = "response_type") String responseType,
        @RequestParam(name = "scope", required = false) String scope,
        @RequestParam(name = "nonce", required = false) String nonce,
        @RequestParam(name = "code_challenge", required = false) String codeChallenge,
        @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod) {
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功", ssoPlatformService.buildAuthorizeContext(
            clientId, redirectUri, responseType, scope, nonce, codeChallenge, codeChallengeMethod
        )));
    }

    @PostMapping("/authorize/approve")
    public ResponseEntity<ApiResponse<SsoAuthorizeApproveResponse>> approveAuthorization(@RequestBody SsoAuthorizeRequest request,
                                                                                         Authentication authentication) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(new ApiResponse<>(200, "授权成功", ssoPlatformService.approveAuthorization(user, request)));
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> exchangeToken(
        @RequestParam(name = "grant_type", required = false) String grantType,
        @RequestParam(name = "code", required = false) String code,
        @RequestParam(name = "client_id", required = false) String clientId,
        @RequestParam(name = "client_secret", required = false) String clientSecret,
        @RequestParam(name = "redirect_uri", required = false) String redirectUri,
        @RequestParam(name = "code_verifier", required = false) String codeVerifier) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(ssoPlatformService.exchangeAuthorizationCode(
                grantType, code, clientId, clientSecret, redirectUri, codeVerifier
            ));
    }

    @GetMapping("/userinfo")
    public ResponseEntity<Map<String, Object>> userInfo(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(name = "access_token", required = false) String accessToken) {
        String token = extractBearerToken(authorization, accessToken);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(ssoPlatformService.buildUserInfo(token));
    }

    @ExceptionHandler(Oauth2Exception.class)
    public ResponseEntity<?> handleOauth2Exception(Oauth2Exception ex, HttpServletRequest request) {
        if (isSsoProtocolRequest(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", ex.getError());
            body.put("error_description", ex.getDescription());
            return ResponseEntity.status(ex.getStatus()).body(body);
        }
        return ResponseEntity.status(ex.getStatus())
            .body(new ApiResponse<>(ex.getStatus().value(), ex.getDescription()));
    }

    private boolean isSsoProtocolRequest(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return path.endsWith("/sso/token")
            || path.endsWith("/sso/token/")
            || path.endsWith("/sso/userinfo")
            || path.endsWith("/sso/userinfo/");
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "未登录");
        }
        String uuid = authentication.getPrincipal().toString();
        return userService.findByUuid(uuid)
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "用户不存在"));
    }

    private String extractBearerToken(String authorizationHeader, String queryToken) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return queryToken;
    }

    private String getStaticHostPrefix() {
        return appProperties.isDebug() ? "http://localhost:8000" : "https://api.ksuser.cn";
    }

    private String resolveFileExtension(String contentType, String originalFilename) {
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        switch (normalizedType) {
            case "image/jpeg":
            case "image/jpg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            case "image/gif":
                return ".gif";
            default:
                break;
        }
        if (originalFilename == null) {
            return null;
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return null;
        }
        String extension = originalFilename.substring(index).toLowerCase(Locale.ROOT);
        if (".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension)
            || ".webp".equals(extension) || ".gif".equals(extension)) {
            return ".jpeg".equals(extension) ? ".jpg" : extension;
        }
        return null;
    }
}
