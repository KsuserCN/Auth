package cn.ksuser.api.controller;

import cn.ksuser.api.config.ApnsProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.PushDeviceRequest;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.service.PushNotificationService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth/push/device")
public class PushDeviceController {
    private final PushNotificationService push;
    private final UserSessionService sessions;
    private final JwtUtil jwt;
    private final ApnsProperties properties;

    public PushDeviceController(PushNotificationService push, UserSessionService sessions, JwtUtil jwt, ApnsProperties properties) {
        this.push = push; this.sessions = sessions; this.jwt = jwt; this.properties = properties;
    }

    private UserSession currentSession(String header, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        Long id = jwt.getSessionId(token);
        if (id == null) return null;
        UserSession session = sessions.findActiveSessionById(id).orElse(null);
        return session != null && authentication.getName().equals(session.getUser().getUuid())
            && session.getSessionVersion().equals(jwt.getSessionVersion(token)) ? session : null;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> register(
            @RequestHeader(value = "Authorization", required = false) String header,
            Authentication authentication, @Valid @RequestBody PushDeviceRequest request) {
        UserSession session = currentSession(header, authentication);
        if (session == null) return ResponseEntity.status(401).body(new ApiResponse<>(401, "请重新登录"));
        push.register(session, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "推送设置已保存", Map.of("enabled", properties.isEnabled())));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(
            @RequestHeader(value = "Authorization", required = false) String header, Authentication authentication) {
        UserSession session = currentSession(header, authentication);
        if (session == null) return ResponseEntity.status(401).body(new ApiResponse<>(401, "请重新登录"));
        push.unregister(session.getId());
        return ResponseEntity.ok(new ApiResponse<>(200, "此设备已停止接收推送"));
    }
}
