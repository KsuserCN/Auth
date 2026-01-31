package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserSessionRepository;
import cn.ksuser.api.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserSessionService(UserSessionRepository userSessionRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public UserSession createSession(User user, String refreshToken) {
        String hashedToken = passwordEncoder.encode(refreshToken);
        byte[] tokenVerifier = hashedToken.getBytes(StandardCharsets.UTF_8);

        long expirationMs = jwtUtil.getRefreshTokenExpirationTime();
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(expirationMs));

        UserSession session = new UserSession(user, tokenVerifier, "argon2id", expiresAt);
        return userSessionRepository.save(session);
    }

    public boolean verifyRefreshToken(User user, String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            return false;
        }

        String uuid = jwtUtil.getUuidFromToken(refreshToken);
        if (uuid == null || !uuid.equals(user.getUuid())) {
            return false;
        }

        List<UserSession> sessions = userSessionRepository.findActiveSessions(user, LocalDateTime.now());
        for (UserSession session : sessions) {
            String storedHash = new String(session.getRefreshTokenVerifier(), StandardCharsets.UTF_8);
            if (passwordEncoder.matches(refreshToken, storedHash)) {
                return true;
            }
        }

        return false;
    }
}
