package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserSessionRepository;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.service.IpLocationService;
import cn.ksuser.api.service.UserAgentParserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final IpLocationService ipLocationService;
    private final UserAgentParserService userAgentParserService;
    private final TokenBlacklistService tokenBlacklistService;

    public UserSessionService(UserSessionRepository userSessionRepository,
                              PasswordEncoder passwordEncoder,
                              JwtUtil jwtUtil,
                              IpLocationService ipLocationService,
                              UserAgentParserService userAgentParserService,
                              TokenBlacklistService tokenBlacklistService) {
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.ipLocationService = ipLocationService;
        this.userAgentParserService = userAgentParserService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public UserSession createSession(User user, String refreshToken, String ipAddress, String userAgent) {
        String hashedToken = passwordEncoder.encode(refreshToken);
        byte[] tokenVerifier = hashedToken.getBytes(StandardCharsets.UTF_8);

        long expirationMs = jwtUtil.getRefreshTokenExpirationTime();
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(expirationMs));

        UserSession session = new UserSession(user, tokenVerifier, "argon2id", expiresAt);
        session.setSessionVersion(0);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setLastSeenAt(LocalDateTime.now());

        if (ipAddress != null) {
            String location = ipLocationService.getIpLocation(ipAddress);
            session.setIpLocation(location);
        }

        if (userAgent != null) {
            UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(userAgent);
            session.setBrowser(uaInfo.getBrowser());
            session.setDeviceType(uaInfo.getDeviceType());
        }

        return userSessionRepository.save(session);
    }

    public Optional<UserSession> verifyRefreshToken(User user, String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            return Optional.empty();
        }

        // ✅ 安全修复：检查Token是否在黑名单中
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            return Optional.empty();
        }

        String uuid = jwtUtil.getUuidFromToken(refreshToken);
        if (uuid == null || !uuid.equals(user.getUuid())) {
            return Optional.empty();
        }

        List<UserSession> sessions = userSessionRepository.findActiveSessions(user, LocalDateTime.now());
        for (UserSession session : sessions) {
            String storedHash = new String(session.getRefreshTokenVerifier(), StandardCharsets.UTF_8);
            if (passwordEncoder.matches(refreshToken, storedHash)) {
                return Optional.of(session);
            }
        }

        return Optional.empty();
    }

    public Optional<UserSession> findActiveSessionById(Long sessionId) {
        return userSessionRepository.findActiveSessionById(sessionId, LocalDateTime.now());
    }

    public Optional<UserSession> findSessionByIdForUser(Long sessionId, User user) {
        return userSessionRepository.findByIdAndUser(sessionId, user);
    }

    public List<UserSession> listActiveSessions(User user) {
        return userSessionRepository.findActiveSessions(user, LocalDateTime.now());
    }

    public UserSession bumpSessionVersion(UserSession session) {
        Integer current = session.getSessionVersion();
        if (current == null) {
            current = 0;
        }
        session.setSessionVersion(current + 1);
        return userSessionRepository.save(session);
    }

    /**
     * 更新会话的 RefreshToken（用于 Token 轮换）
     * @param session 会话对象
     * @param newRefreshToken 新的 RefreshToken
     * @return 更新后的会话
     */
    public UserSession updateRefreshToken(UserSession session, String newRefreshToken) {
        String hashedToken = passwordEncoder.encode(newRefreshToken);
        byte[] tokenVerifier = hashedToken.getBytes(StandardCharsets.UTF_8);
        
        // 更新 RefreshToken 和过期时间
        session.setRefreshTokenVerifier(tokenVerifier);
        long expirationMs = jwtUtil.getRefreshTokenExpirationTime();
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(expirationMs));
        session.setExpiresAt(expiresAt);
        
        return userSessionRepository.save(session);
    }

    public UserSession updateSessionActivity(UserSession session, String ipAddress, String userAgent) {
        session.setLastSeenAt(LocalDateTime.now());

        if (ipAddress != null && (session.getIpAddress() == null || !ipAddress.equals(session.getIpAddress()))) {
            session.setIpAddress(ipAddress);
            String location = ipLocationService.getIpLocation(ipAddress);
            session.setIpLocation(location);
        }

        if (userAgent != null && (session.getUserAgent() == null || !userAgent.equals(session.getUserAgent()))) {
            session.setUserAgent(userAgent);
            UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(userAgent);
            session.setBrowser(uaInfo.getBrowser());
            session.setDeviceType(uaInfo.getDeviceType());
        }

        return userSessionRepository.save(session);
    }

    public UserSession revokeSession(UserSession session) {
        session.setRevokedAt(LocalDateTime.now());
        return userSessionRepository.save(session);
    }

    public void revokeAllSessions(User user) {
        List<UserSession> activeSessions = userSessionRepository.findActiveSessions(user, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        for (UserSession session : activeSessions) {
            session.setRevokedAt(now);
        }
        userSessionRepository.saveAll(activeSessions);
    }

    /**
     * 删除用户的所有会话
     * @param user 用户对象
     */
    public void deleteAllSessionsByUser(User user) {
        userSessionRepository.deleteByUser(user);
    }
}
