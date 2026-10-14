package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.OfficialAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/official")
public class OfficialAuthorizationController {
    private final OfficialAuthorizationService service;
    private final UserRepository users;

    public OfficialAuthorizationController(OfficialAuthorizationService service, UserRepository users) {
        this.service = service; this.users = users;
    }

    @GetMapping("/authorize/context")
    public ResponseEntity<ApiResponse<OfficialAuthorizationService.AuthorizeContext>> context(
        @RequestParam(name = "client_id") String clientId, @RequestParam(name = "redirect_uri") String redirectUri,
        @RequestParam(name = "response_type") String responseType, @RequestParam(name = "scope", required = false) String scope,
        @RequestParam(name = "state") String state, @RequestParam(name = "nonce", required = false) String nonce,
        @RequestParam(name = "code_challenge") String challenge, @RequestParam(name = "code_challenge_method") String method,
        Authentication authentication) {
        return noStore(new ApiResponse<>(200, ApiResultCode.OFFICIAL_SUCCESS, service.buildAuthorizeContext(resolveUser(authentication),
            new OfficialAuthorizeRequest(clientId, redirectUri, responseType, scope, state, nonce, challenge, method, null))));
    }

    @PostMapping("/authorize/approve")
    public ResponseEntity<ApiResponse<Map<String, String>>> approve(@RequestBody OfficialAuthorizeRequest body, Authentication authentication) {
        return noStore(new ApiResponse<>(200, ApiResultCode.OFFICIAL_AUTHORIZED, service.approveAuthorization(requireUser(authentication), body)));
    }

    @GetMapping("/authorizations")
    public ResponseEntity<ApiResponse<List<OfficialAuthorizationService.AuthorizedApplication>>> authorizations(Authentication authentication) {
        return noStore(new ApiResponse<>(200, ApiResultCode.OFFICIAL_SUCCESS, service.listAuthorizations(requireUser(authentication))));
    }

    @DeleteMapping("/authorizations/{clientId}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable String clientId, Authentication authentication) {
        service.revokeAuthorization(requireUser(authentication), clientId);
        return noStore(new ApiResponse<>(200, ApiResultCode.OFFICIAL_REVOKED));
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
        @RequestParam(name = "grant_type", required = false) String grantType, @RequestParam(name = "code", required = false) String code,
        @RequestParam(name = "client_id", required = false) String clientId, @RequestParam(name = "client_secret", required = false) String secret,
        @RequestParam(name = "redirect_uri", required = false) String redirectUri, @RequestParam(name = "code_verifier", required = false) String verifier,
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        if (authorization != null) {
            if (!authorization.startsWith("Basic ") || secret != null) throw protocolInvalidClient();
            try {
                String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
                int separator = decoded.indexOf(':');
                if (separator < 1) throw protocolInvalidClient();
                String basicId = java.net.URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8);
                if (clientId != null && !clientId.equals(basicId)) throw protocolInvalidClient();
                clientId = basicId;
                secret = java.net.URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) { throw protocolInvalidClient(); }
        }
        return noStore(service.exchangeAuthorizationCode(grantType, code, clientId, secret, redirectUri, verifier, origin));
    }

    @GetMapping("/userinfo")
    public ResponseEntity<Map<String, Object>> userinfo(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        return noStore(service.buildUserInfo(token, origin));
    }

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> discovery() { return noStore(service.discovery()); }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() { return noStore(service.jwks()); }

    @ExceptionHandler(Oauth2Exception.class)
    public ResponseEntity<?> oauthException(Oauth2Exception ex, HttpServletRequest request) {
        if (request.getRequestURI().endsWith("/token") || request.getRequestURI().endsWith("/userinfo")) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", ex.getError()); body.put("error_description", ex.getDescription()); body.put("errorCode", ex.getResult().errorCode());
            return ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore()).body(body);
        }
        return ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore())
            .body(new ApiResponse<>(ex.getStatus().value(), ex.getResult()));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) return null;
        return users.findByUuid(authentication.getPrincipal().toString()).orElse(null);
    }
    private User requireUser(Authentication authentication) {
        User user = resolveUser(authentication);
        if (user == null) throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "login_required", ApiResultCode.UNAUTHENTICATED);
        return user;
    }
    private Oauth2Exception protocolInvalidClient() { return new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_client", ApiResultCode.OFFICIAL_CLIENT_INVALID); }
    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").body(body);
    }
}
