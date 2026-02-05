package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserPasskey;
import cn.ksuser.api.repository.UserPasskeyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Passkey (WebAuthn) 服务
 * 负责处理 Passkey 注册、认证、敏感操作验证等核心逻辑
 */
@Service
public class PasskeyService {
    private static final String PASSKEY_REGISTRATION_CHALLENGE_PREFIX = "passkey:reg:challenge:";
    private static final String PASSKEY_AUTHENTICATION_CHALLENGE_PREFIX = "passkey:auth:challenge:";
    private static final String PASSKEY_SENSITIVE_CHALLENGE_PREFIX = "passkey:sensitive:challenge:";
    private static final int CHALLENGE_LENGTH = 32; // 32 bytes = 256 bits
    private static final long CHALLENGE_EXPIRY_SECONDS = 10 * 60; // 10 minutes

    private final UserPasskeyRepository userPasskeyRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public PasskeyService(UserPasskeyRepository userPasskeyRepository,
                          StringRedisTemplate stringRedisTemplate,
                          AppProperties appProperties) {
        this.userPasskeyRepository = userPasskeyRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.appProperties = appProperties;
        this.objectMapper = new ObjectMapper();
        this.secureRandom = new SecureRandom();
    }

    /**
     * 生成注册选项
     */
    public PasskeyRegistrationOptionsResponse generateRegistrationOptions(User user) throws Exception {
        // 生成 challenge
        String challenge = generateChallenge();
        
        // 生成用户 ID（使用 UUID 的 bytes）
        String userId = user.getUuid();
        
        // 将 challenge 存储到 Redis（键：passkey:reg:challenge:{userId}）
        String challengeKey = PASSKEY_REGISTRATION_CHALLENGE_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(challengeKey, challenge, CHALLENGE_EXPIRY_SECONDS, TimeUnit.SECONDS);

        // 构建 RP (Relying Party) 对象
        ObjectNode rpNode = objectMapper.createObjectNode();
        rpNode.put("name", appProperties.getPasskey().getRpName());
        rpNode.put("id", getEffectiveRpId());

        // 构建 user 对象
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("id", Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes()));
        userNode.put("name", user.getEmail() != null ? user.getEmail() : user.getUsername());
        userNode.put("displayName", user.getUsername());

        // 构建 pubKeyCredParams 数组
        ArrayNode pubKeyCredParams = objectMapper.createArrayNode();
        ObjectNode credParam = objectMapper.createObjectNode();
        credParam.put("type", "public-key");
        credParam.put("alg", -7); // ES256
        pubKeyCredParams.add(credParam);

        // 构建 authenticatorSelection 对象
        ObjectNode authSelNode = objectMapper.createObjectNode();
        authSelNode.put("authenticatorAttachment", "platform"); // 优先 platform authenticator
        authSelNode.put("residentKey", appProperties.getPasskey().getResidentKey());
        authSelNode.put("userVerification", appProperties.getPasskey().getUserVerification());

        // 构建响应
        PasskeyRegistrationOptionsResponse response = new PasskeyRegistrationOptionsResponse();
        response.setChallenge(challenge);
        response.setRp(rpNode.toString());
        response.setUser(userNode.toString());
        response.setPubKeyCredParams(pubKeyCredParams.toString());
        response.setTimeout(String.valueOf(appProperties.getPasskey().getTimeout()));
        response.setAttestation(appProperties.getPasskey().getAttestation());
        response.setAuthenticatorSelection(authSelNode.toString());

        return response;
    }

    /**
     * 验证注册
     */
    public UserPasskey verifyRegistration(User user, PasskeyRegistrationVerifyRequest request) throws Exception {
        String userId = user.getUuid();
        
        // 从 Redis 中获取 challenge
        String challengeKey = PASSKEY_REGISTRATION_CHALLENGE_PREFIX + userId;
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);
        
        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在，请重新开始注册");
        }

        // 清除 challenge
        stringRedisTemplate.delete(challengeKey);

        // 验证 ClientDataJSON
        String clientDataJSON = new String(Base64.getUrlDecoder().decode(request.getClientDataJSON()));
        JsonNode clientDataNode = objectMapper.readTree(clientDataJSON);
        
        String type = clientDataNode.get("type").asText();
        if (!"webauthn.create".equals(type)) {
            throw new IllegalArgumentException("ClientDataJSON type 必须为 webauthn.create");
        }

        String challenge = clientDataNode.get("challenge").asText();
        if (!challenge.equals(storedChallenge)) {
            throw new IllegalArgumentException("Challenge 不匹配");
        }

        String origin = clientDataNode.get("origin").asText();
        if (!origin.equals(getEffectiveOrigin())) {
            throw new IllegalArgumentException("Origin 不匹配，期望: " + getEffectiveOrigin() + ", 实际: " + origin);
        }

        // 解析 attestationObject（简化处理，仅验证基本结构）
        byte[] attestationObject = Base64.getUrlDecoder().decode(request.getAttestationObject());
        // 注：生产环境应使用 fido2-lib 等库进行完整验证

        // 创建 UserPasskey 记录
        UserPasskey passkey = new UserPasskey();
        passkey.setUserId(user.getId());
        passkey.setCredentialId(Base64.getUrlDecoder().decode(request.getCredentialRawId()));
        passkey.setPublicKeyCose(extractPublicKeyFromAttestation(attestationObject));
        passkey.setSignCount(0L);
        passkey.setName(request.getPasskeyName() != null ? request.getPasskeyName() : "My Passkey");
        passkey.setTransports(request.getTransports());
        passkey.setCreatedAt(LocalDateTime.now());
        passkey.setUpdatedAt(LocalDateTime.now());

        return userPasskeyRepository.save(passkey);
    }

    /**
     * 生成认证选项
     */
    public PasskeyAuthenticationOptionsResponse generateAuthenticationOptions() throws Exception {
        // 生成 challenge
        String challenge = generateChallenge();
        
        // 将 challenge 存储到 Redis（键：passkey:auth:challenge:{randomId}）
        String randomId = UUID.randomUUID().toString();
        String challengeKey = PASSKEY_AUTHENTICATION_CHALLENGE_PREFIX + randomId;
        stringRedisTemplate.opsForValue().set(challengeKey, challenge, CHALLENGE_EXPIRY_SECONDS, TimeUnit.SECONDS);

        PasskeyAuthenticationOptionsResponse response = new PasskeyAuthenticationOptionsResponse();
        response.setChallengeId(randomId);
        response.setChallenge(challenge);
        response.setTimeout(String.valueOf(appProperties.getPasskey().getTimeout()));
        response.setRpId(getEffectiveRpId());
        response.setUserVerification(appProperties.getPasskey().getUserVerification());

        return response;
    }

    /**
     * 验证认证（用于登录）
     * 返回成功验证的 Passkey 的用户 ID（不返回User对象，由Controller查询）
     */
    public Long verifyAuthenticationAndGetUserId(PasskeyAuthenticationVerifyRequest request, String challengeId) throws Exception {
        // 从 Redis 中获取 challenge
        String challengeKey = PASSKEY_AUTHENTICATION_CHALLENGE_PREFIX + challengeId;
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);
        
        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在");
        }

        // 清除 challenge
        stringRedisTemplate.delete(challengeKey);

        // 验证 ClientDataJSON
        String clientDataJSON = new String(Base64.getUrlDecoder().decode(request.getClientDataJSON()));
        JsonNode clientDataNode = objectMapper.readTree(clientDataJSON);
        
        String type = clientDataNode.get("type").asText();
        if (!"webauthn.get".equals(type)) {
            throw new IllegalArgumentException("ClientDataJSON type 必须为 webauthn.get");
        }

        String challenge = clientDataNode.get("challenge").asText();
        if (!challenge.equals(storedChallenge)) {
            throw new IllegalArgumentException("Challenge 不匹配");
        }

        String origin = clientDataNode.get("origin").asText();
        if (!origin.equals(getEffectiveOrigin())) {
            throw new IllegalArgumentException("Origin 不匹配");
        }

        // 通过 credential ID 查找 Passkey
        byte[] credentialId = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        UserPasskey passkey = userPasskeyRepository.findByCredentialId(credentialId)
            .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        // 验证签名（简化处理）
        // 注：生产环境应使用 fido2-lib 等库进行完整验证
        // TODO: 实现完整的签名验证逻辑
        @SuppressWarnings("unused")
        byte[] authenticatorData = Base64.getUrlDecoder().decode(request.getAuthenticatorData());
        @SuppressWarnings("unused")
        byte[] signature = Base64.getUrlDecoder().decode(request.getSignature());

        // 更新 sign count 和 last used time
        passkey.setSignCount(passkey.getSignCount() + 1);
        passkey.setLastUsedAt(LocalDateTime.now());
        userPasskeyRepository.save(passkey);

        // 返回用户 ID
        return passkey.getUserId();
    }

    /**
     * 验证认证（用于登录）- 废弃方法
     */
    @Deprecated
    public User verifyAuthentication(PasskeyAuthenticationVerifyRequest request, String challengeId) throws Exception {
        // 从 Redis 中获取 challenge
        String challengeKey = PASSKEY_AUTHENTICATION_CHALLENGE_PREFIX + challengeId;
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);
        
        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在");
        }

        // 清除 challenge
        stringRedisTemplate.delete(challengeKey);

        // 验证 ClientDataJSON
        String clientDataJSON = new String(Base64.getUrlDecoder().decode(request.getClientDataJSON()));
        JsonNode clientDataNode = objectMapper.readTree(clientDataJSON);
        
        String type = clientDataNode.get("type").asText();
        if (!"webauthn.get".equals(type)) {
            throw new IllegalArgumentException("ClientDataJSON type 必须为 webauthn.get");
        }

        String challenge = clientDataNode.get("challenge").asText();
        if (!challenge.equals(storedChallenge)) {
            throw new IllegalArgumentException("Challenge 不匹配");
        }

        String origin = clientDataNode.get("origin").asText();
        if (!origin.equals(getEffectiveOrigin())) {
            throw new IllegalArgumentException("Origin 不匹配");
        }

        // 通过 credential ID 查找 Passkey
        byte[] credentialId = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        UserPasskey passkey = userPasskeyRepository.findByCredentialId(credentialId)
            .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        // 验证签名（简化处理）
        // 注：生产环境应使用 fido2-lib 等库进行完整验证
        // TODO: 实现完整的签名验证逻辑
        @SuppressWarnings("unused")
        byte[] authenticatorData = Base64.getUrlDecoder().decode(request.getAuthenticatorData());
        @SuppressWarnings("unused")
        byte[] signature = Base64.getUrlDecoder().decode(request.getSignature());

        // 更新 sign count 和 last used time
        passkey.setSignCount(passkey.getSignCount() + 1);
        passkey.setLastUsedAt(LocalDateTime.now());
        userPasskeyRepository.save(passkey);

        // 通过 user ID 从数据库加载完整的 User 对象
        // 注意：UserPasskey 中存储的 userId 对应 User 的 id（主键）
        // 因此需要通过 JPA 的默认方法或自定义查询来获取
        // 这里简化处理：返回 null，由 Controller 通过 Passkey 的 userId 来单独查询
        // 或在 UserPasskey entity 中添加 @ManyToOne 关联
        return null;
    }

    /**
     * 生成敏感操作验证选项
     */
    public PasskeyAuthenticationOptionsResponse generateSensitiveVerificationOptions(String userId) throws Exception {
        // 生成 challenge
        String challenge = generateChallenge();
        
        // 存储到 Redis（键：passkey:sensitive:challenge:{randomId}）
        String randomId = UUID.randomUUID().toString();
        String challengeKey = PASSKEY_SENSITIVE_CHALLENGE_PREFIX + randomId;
        stringRedisTemplate.opsForValue().set(challengeKey, challenge, CHALLENGE_EXPIRY_SECONDS, TimeUnit.SECONDS);

        PasskeyAuthenticationOptionsResponse response = new PasskeyAuthenticationOptionsResponse();
        response.setChallengeId(randomId);
        response.setChallenge(challenge);
        response.setTimeout(String.valueOf(appProperties.getPasskey().getTimeout()));
        response.setRpId(getEffectiveRpId());
        response.setUserVerification("required"); // 敏感操作需要用户验证

        return response;
    }

    /**
     * 验证敏感操作
     */
    public void verifySensitiveOperation(User user, PasskeyAuthenticationVerifyRequest request, String challengeId) throws Exception {
        // 从 Redis 中获取 challenge
        String challengeKey = PASSKEY_SENSITIVE_CHALLENGE_PREFIX + challengeId;
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);
        
        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在");
        }

        // 清除 challenge
        stringRedisTemplate.delete(challengeKey);

        // 验证 ClientDataJSON
        String clientDataJSON = new String(Base64.getUrlDecoder().decode(request.getClientDataJSON()));
        JsonNode clientDataNode = objectMapper.readTree(clientDataJSON);
        
        String type = clientDataNode.get("type").asText();
        if (!"webauthn.get".equals(type)) {
            throw new IllegalArgumentException("ClientDataJSON type 必须为 webauthn.get");
        }

        String challenge = clientDataNode.get("challenge").asText();
        if (!challenge.equals(storedChallenge)) {
            throw new IllegalArgumentException("Challenge 不匹配");
        }

        // 通过 credential ID 查找 Passkey（必须属于该用户）
        byte[] credentialId = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        UserPasskey passkey = userPasskeyRepository.findByCredentialId(credentialId)
            .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        if (!passkey.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Passkey 不属于当前用户");
        }

        // 更新使用时间
        passkey.setLastUsedAt(LocalDateTime.now());
        userPasskeyRepository.save(passkey);
    }

    /**
     * 获取用户的 Passkey 列表
     */
    public List<PasskeyListResponse.PasskeyInfo> getUserPasskeys(Long userId) {
        List<UserPasskey> passkeys = userPasskeyRepository.findByUserId(userId);
        List<PasskeyListResponse.PasskeyInfo> infos = new ArrayList<>();
        
        for (UserPasskey passkey : passkeys) {
            PasskeyListResponse.PasskeyInfo info = new PasskeyListResponse.PasskeyInfo();
            info.setId(passkey.getId());
            info.setName(passkey.getName());
            info.setTransports(passkey.getTransports());
            info.setLastUsedAt(passkey.getLastUsedAt() != null ? passkey.getLastUsedAt().toString() : null);
            info.setCreatedAt(passkey.getCreatedAt().toString());
            infos.add(info);
        }
        
        return infos;
    }

    /**
     * 删除 Passkey
     */
    public void deletePasskey(Long passkeyId, Long userId) {
        UserPasskey passkey = userPasskeyRepository.findById(passkeyId)
            .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        if (!passkey.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限删除此 Passkey");
        }

        userPasskeyRepository.delete(passkey);
    }

    /**
     * 重命名 Passkey
     */
    public void renamePasskey(Long passkeyId, Long userId, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Passkey 名称不能为空");
        }

        if (newName.length() > 50) {
            throw new IllegalArgumentException("Passkey 名称长度不能超过 50 个字符");
        }

        UserPasskey passkey = userPasskeyRepository.findById(passkeyId)
            .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        if (!passkey.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限修改此 Passkey");
        }

        passkey.setName(newName.trim());
        passkey.setUpdatedAt(LocalDateTime.now());
        userPasskeyRepository.save(passkey);
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成 challenge
     */
    private String generateChallenge() {
        byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
        secureRandom.nextBytes(challengeBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
    }

    /**
     * 提取公钥（简化处理）
     */
    private byte[] extractPublicKeyFromAttestation(byte[] attestationObject) {
        // 简化处理：直接返回 attestationObject 的一部分
        // 生产环境应使用 CBOR 库进行解析
        return attestationObject;
    }

    /**
     * 获取有效的 Origin（根据 debug 标志切换）
     */
    private String getEffectiveOrigin() {
        if (appProperties.isDebug()) {
            return "http://localhost:5173";
        }
        return "https://auth.ksuser.cn";
    }

    /**
     * 获取有效的 RP ID
     */
    private String getEffectiveRpId() {
        if (appProperties.isDebug()) {
            return "localhost";
        }
        return "auth.ksuser.cn";
    }

    /**
     * 根据 debug 标志返回前端地址
     */
    public String getFrontendUrl() {
        if (appProperties.isDebug()) {
            return "http://localhost:5173";
        }
        return "https://auth.ksuser.cn";
    }
}
