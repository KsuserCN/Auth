package cn.ksuser.api.util;

import cn.ksuser.api.service.SensitiveOperationService;
import jakarta.servlet.http.HttpServletRequest;

/** Session context is taken from the authenticated bearer token, never from client input. */
public record SensitiveSessionContext(Long sessionId, Integer version) {
    public static SensitiveSessionContext from(HttpServletRequest request, JwtUtil jwt) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return new SensitiveSessionContext(null, null);
        String token = header.substring(7);
        return new SensitiveSessionContext(jwt.getSessionId(token), jwt.getSessionVersion(token));
    }

    public boolean isVerified(SensitiveOperationService service, String uuid, String ip, String purpose) {
        if (sessionId == null || version == null) return service.isVerified(uuid, ip);
        return service.isVerified(uuid, ip, sessionId, version, purpose);
    }

    public boolean consume(SensitiveOperationService service, String uuid, String ip, String purpose) {
        if (sessionId == null || version == null) return service.consumeVerification(uuid, ip);
        return service.consumeVerification(uuid, ip, sessionId, version, purpose);
    }
}
