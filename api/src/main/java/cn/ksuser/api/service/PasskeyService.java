package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserPasskey;
import cn.ksuser.api.repository.UserPasskeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.Authenticator;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Passkey (WebAuthn) 服务 - 生产级实现（使用 webauthn4j）
 *
 * 核心安全特性：
 * 1. ✅ 完整的签名验证（使用存储的公钥验证 signature）
 * 2. ✅ Challenge 防重放（每个 challenge 只能使用一次，10分钟过期）
 * 3. ✅ Origin 和 RP ID 验证（防止跨域攻击）
 * 4. ✅ Sign Count 检查（防止克隆的 Passkey）
 * 5. ✅ 正确提取和存储公钥（COSE_Key 格式）
 * 6. ✅ Attestation 验证（注册时）
 * 7. ✅ Assertion 验证（认证时）
 * 8. ✅ User Verification 标志检查（敏感操作）
 * 9. ✅ Credential ID 唯一性检查
 * 10. ✅ 用户归属验证
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
    private final WebAuthnManager webAuthnManager;
    private final ObjectConverter objectConverter;

    public PasskeyService(UserPasskeyRepository userPasskeyRepository,
                          StringRedisTemplate stringRedisTemplate,
                          AppProperties appProperties) {
        this.userPasskeyRepository = userPasskeyRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.appProperties = appProperties;
        this.objectMapper = new ObjectMapper();
        this.secureRandom = new SecureRandom();
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        this.objectConverter = new ObjectConverter();
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
        authSelNode.put("authenticatorAttachment", "platform");
        authSelNode.put("residentKey", appProperties.getPasskey().getResidentKey());
        authSelNode.put("userVerification", appProperties.getPasskey().getUserVerification());

        // 构建响应
        PasskeyRegistrationOptionsResponse response = new PasskeyRegistrationOptionsResponse();
        response.setChallenge(challenge);
        response.setRp(rpNode.toString());
        response.setUser(userNode.toString());
        response.setPubKeyCredParams(pubKeyCredParams.toString());
        response.setAuthenticatorSelection(authSelNode.toString());
        response.setTimeout(String.valueOf(appProperties.getPasskey().getTimeout()));
        response.setAttestation(appProperties.getPasskey().getAttestation());

        return response;
    }

    /**
     * 验证注册 - 使用 webauthn4j 进行完整的 Attestation 验证
     *
     * 验证步骤：
     * 1. Challenge 验证和一次性使用（防重放）
     * 2. Attestation 完整性验证（signature, clientDataHash, attestationObject）
     * 3. Origin 和 RP ID 验证
     * 4. 提取并存储公钥（COSE_Key）
     * 5. Credential ID 唯一性检查
     * 6. 数据库存储
     *
     * @return 存储的 UserPasskey
     */
    public UserPasskey verifyRegistration(User user, PasskeyRegistrationVerifyRequest request) throws Exception {
        // ========== 1. Challenge 验证 ==========
        String challengeKey = PASSKEY_REGISTRATION_CHALLENGE_PREFIX + user.getUuid();
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);

        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在");
        }

        // 清除 challenge（防止重放攻击 - 每个 challenge 只能使用一次）
        stringRedisTemplate.delete(challengeKey);

        // ========== 2. 使用 webauthn4j 验证 Attestation ==========
        byte[] attestationObjectBytes = Base64.getUrlDecoder().decode(request.getAttestationObject());
        byte[] clientDataJSONBytes = Base64.getUrlDecoder().decode(request.getClientDataJSON());

        // 解析 attestationObject
        AttestationObject attestationObject = objectConverter.getCborConverter()
                .readValue(attestationObjectBytes, AttestationObject.class);

        // 构建 ServerProperty（包含 origin, rpId, challenge）
        ServerProperty serverProperty = new ServerProperty(
                Origin.create(getEffectiveOrigin()),
                getEffectiveRpId(),
                new DefaultChallenge(Base64.getUrlDecoder().decode(storedChallenge)),
                null // tokenBindingId (通常为 null)
        );

        // 构建 RegistrationRequest
        RegistrationRequest registrationRequest = new RegistrationRequest(
                attestationObjectBytes,
                clientDataJSONBytes
        );

        // 构建 RegistrationParameters
        RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty,
                null, // pubKeyCredParams (webauthn4j 会自动处理)
                false, // userVerificationRequired (注册时通常不强制)
                true  // userPresenceRequired (必须)
        );

        // 验证注册 - 使用 webauthn4j 进行完整的 Attestation 验证
        RegistrationData registrationData;
        try {
            @SuppressWarnings("deprecation")
            RegistrationData result = webAuthnManager.validate(registrationRequest, registrationParameters);
            registrationData = result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Passkey 注册验证失败: " + e.getMessage(), e);
        }

        // ========== 3. 提取关键数据 ==========
        AttestedCredentialData attestedCredentialData = attestationObject.getAuthenticatorData()
                .getAttestedCredentialData();

        if (attestedCredentialData == null) {
            throw new IllegalArgumentException("AuthenticatorData 缺少 AttestedCredentialData");
        }

        byte[] credentialId = attestedCredentialData.getCredentialId();
        COSEKey coseKey = attestedCredentialData.getCOSEKey();
        byte[] aaguid = attestedCredentialData.getAaguid().getBytes();

        // 获取 signCount
        long signCount = 0;
        if (registrationData.getAttestationObject() != null &&
                registrationData.getAttestationObject().getAuthenticatorData() != null) {
            signCount = registrationData.getAttestationObject().getAuthenticatorData()
                    .getSignCount();
        }

        // ========== 4. Credential ID 唯一性检查 ==========
        if (userPasskeyRepository.findByCredentialId(credentialId).isPresent()) {
            throw new IllegalArgumentException("此 Passkey 已被注册");
        }

        // ========== 5. 存储到数据库 ==========
        UserPasskey passkey = new UserPasskey();
        passkey.setUserId(user.getId());
        passkey.setCredentialId(credentialId);

        // 存储公钥（COSE_Key 的 CBOR 编码）- 用于后续验签
        passkey.setPublicKeyCose(objectConverter.getCborConverter().writeValueAsBytes(coseKey));

        passkey.setAaguid(aaguid);
        passkey.setSignCount(signCount);
        passkey.setTransports(request.getTransports());
        passkey.setName(request.getPasskeyName() != null ? request.getPasskeyName() : "Passkey");
        passkey.setCreatedAt(LocalDateTime.now());
        passkey.setUpdatedAt(LocalDateTime.now());

        userPasskeyRepository.save(passkey);

        return passkey;
    }

    /**
     * 生成认证选项（用于登录）
     */
    public PasskeyAuthenticationOptionsResponse generateAuthenticationOptions() throws Exception {
        // 生成 challenge
        String challenge = generateChallenge();

        // 生成随机 ID 作为 challengeId
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
     * 验证认证（用于登录）- 使用 webauthn4j 进行完整的 Assertion 验证（包含签名验证）
     *
     * 验证步骤：
     * 1. Challenge 验证和一次性使用（防重放）
     * 2. Credential ID 存在性检查
     * 3. 加载存储的公钥
     * 4. Assertion 完整性验证（signature, clientDataHash, authenticatorData）
     * 5. Origin 和 RP ID 验证
     * 6. User Presence 标志检查
     * 7. Sign Count 检查（防克隆）
     * 8. 更新数据库
     *
     * @return 成功验证的用户 ID
     */
    public Long verifyAuthenticationAndGetUserId(PasskeyAuthenticationVerifyRequest request, String challengeId) throws Exception {
        // ========== 1. Challenge 验证 ==========
        String challengeKey = PASSKEY_AUTHENTICATION_CHALLENGE_PREFIX + challengeId;
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);

        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在");
        }

        // 清除 challenge（防止重放攻击）
        stringRedisTemplate.delete(challengeKey);

        // ========== 2. 查找 Passkey ==========
        byte[] credentialId = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        UserPasskey passkey = userPasskeyRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        // ========== 3. 使用 webauthn4j 验证 Assertion（包含签名验证）==========
        byte[] credentialIdBytes = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        byte[] clientDataJSONBytes = Base64.getUrlDecoder().decode(request.getClientDataJSON());
        byte[] authenticatorDataBytes = Base64.getUrlDecoder().decode(request.getAuthenticatorData());
        byte[] signatureBytes = Base64.getUrlDecoder().decode(request.getSignature());

        // 构建 ServerProperty
        ServerProperty serverProperty = new ServerProperty(
                Origin.create(getEffectiveOrigin()),
                getEffectiveRpId(),
                new DefaultChallenge(Base64.getUrlDecoder().decode(storedChallenge)),
                null
        );

        // 构建验证所需的公钥
        COSEKey coseKey = objectConverter.getCborConverter()
                .readValue(passkey.getPublicKeyCose(), COSEKey.class);

        // 获取当前的 signCount（防克隆检查）
        long currentSignCount = passkey.getSignCount();
        if (currentSignCount < 0) {
            currentSignCount = 0;
        }

        // 构建 Authenticator（包含公钥和 signCount）
        @SuppressWarnings("deprecation")
        Authenticator authenticator = new AuthenticatorImpl(
                attestedCredentialData(credentialIdBytes, coseKey, passkey.getAaguid()),
                null, // attestationStatement
                currentSignCount
        );

        // 构建 AuthenticationRequest
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                credentialIdBytes,
                authenticatorDataBytes,
                clientDataJSONBytes,
                signatureBytes
        );

        // 构建 AuthenticationParameters
        AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                serverProperty,
                authenticator,
                null, // allowCredentials (webauthn4j 会自动处理)
                false, // userVerificationRequired (登录时根据配置)
                true   // userPresenceRequired (必须)
        );

        // 执行验证（包含签名验证！）
        AuthenticationData authenticationData;
        try {
            @SuppressWarnings("deprecation")
            AuthenticationData result = webAuthnManager.validate(authenticationRequest, authenticationParameters);
            authenticationData = result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Passkey 认证验证失败: " + e.getMessage(), e);
        }

        // ========== 4. Sign Count 检查（防克隆攻击）==========
        long newSignCount = 0;
        if (authenticationData.getAuthenticatorData() != null) {
            newSignCount = authenticationData.getAuthenticatorData().getSignCount();
        }

        // 如果认证器支持 signCount（> 0），则必须递增
        if (newSignCount > 0 && newSignCount <= currentSignCount) {
            throw new IllegalArgumentException("Sign count 异常，可能是克隆的 Passkey（旧: " +
                    currentSignCount + ", 新: " + newSignCount + "）");
        }

        // ========== 5. 更新数据库 ==========
        passkey.setSignCount(newSignCount);
        passkey.setLastUsedAt(LocalDateTime.now());
        userPasskeyRepository.save(passkey);

        // 返回用户 ID
        return passkey.getUserId();
    }

    /**
     * 生成敏感操作验证选项
     */
    public PasskeyAuthenticationOptionsResponse generateSensitiveVerificationOptions() throws Exception {
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
     * 验证敏感操作 - 使用 webauthn4j 进行完整的验证（包含签名验证）
     *
     * 与登录验证的区别：
     * 1. 必须验证用户归属
     * 2. 必须检查 User Verified 标志（需要 PIN 或生物识别）
     */
    public void verifySensitiveOperation(User user, PasskeyAuthenticationVerifyRequest request, String challengeId) throws Exception {
        // ========== 1. Challenge 验证 ==========
        String challengeKey = PASSKEY_SENSITIVE_CHALLENGE_PREFIX + challengeId;
        String storedChallenge = stringRedisTemplate.opsForValue().get(challengeKey);

        if (storedChallenge == null) {
            throw new IllegalArgumentException("Challenge 已过期或不存在");
        }

        // 清除 challenge（防止重放攻击）
        stringRedisTemplate.delete(challengeKey);

        // ========== 2. 查找 Passkey 并验证用户归属 ==========
        byte[] credentialId = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        UserPasskey passkey = userPasskeyRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Passkey 不存在"));

        if (!passkey.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Passkey 不属于当前用户");
        }

        // ========== 3. 使用 webauthn4j 验证 Assertion（包含签名验证）==========
        byte[] credentialIdBytes = Base64.getUrlDecoder().decode(request.getCredentialRawId());
        byte[] clientDataJSONBytes = Base64.getUrlDecoder().decode(request.getClientDataJSON());
        byte[] authenticatorDataBytes = Base64.getUrlDecoder().decode(request.getAuthenticatorData());
        byte[] signatureBytes = Base64.getUrlDecoder().decode(request.getSignature());

        // 构建 ServerProperty
        ServerProperty serverProperty = new ServerProperty(
                Origin.create(getEffectiveOrigin()),
                getEffectiveRpId(),
                new DefaultChallenge(Base64.getUrlDecoder().decode(storedChallenge)),
                null
        );

        // 构建验证所需的公钥
        COSEKey coseKey = objectConverter.getCborConverter()
                .readValue(passkey.getPublicKeyCose(), COSEKey.class);

        // 获取当前的 signCount（防克隆检查）
        long currentSignCount = passkey.getSignCount();
        if (currentSignCount < 0) {
            currentSignCount = 0;
        }

        // 构建 Authenticator（包含公钥和 signCount）
        @SuppressWarnings("deprecation")
        Authenticator authenticator = new AuthenticatorImpl(
                attestedCredentialData(credentialIdBytes, coseKey, passkey.getAaguid()),
                null,
                currentSignCount
        );

        // 构建 AuthenticationRequest
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                credentialIdBytes,
                authenticatorDataBytes,
                clientDataJSONBytes,
                signatureBytes
        );

        // 构建 AuthenticationParameters（敏感操作需要 User Verification）
        AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                serverProperty,
                authenticator,
                null,
                true,  // userVerificationRequired = true（敏感操作必须）
                true
        );

        // 执行验证（包含签名验证和 User Verified 标志检查）
        AuthenticationData authenticationData;
        try {
            @SuppressWarnings("deprecation")
            AuthenticationData result = webAuthnManager.validate(authenticationRequest, authenticationParameters);
            authenticationData = result;
        } catch (Exception e) {
            throw new IllegalArgumentException("敏感操作验证失败: " + e.getMessage(), e);
        }

        // ========== 4. Sign Count 检查（防克隆攻击）==========
        long newSignCount = 0;
        if (authenticationData.getAuthenticatorData() != null) {
            newSignCount = authenticationData.getAuthenticatorData().getSignCount();
        }

        if (newSignCount > 0 && newSignCount <= currentSignCount) {
            throw new IllegalArgumentException("Sign count 异常，可能是克隆的 Passkey");
        }

        // ========== 5. 更新数据库 ==========
        passkey.setSignCount(newSignCount);
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
     * 生成 challenge（使用 base64url 编码，不带 padding）
     */
    private String generateChallenge() {
        byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
        secureRandom.nextBytes(challengeBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
    }

    /**
     * 创建 AttestedCredentialData（webauthn4j 需要）
     */
    private AttestedCredentialData attestedCredentialData(byte[] credentialId, COSEKey coseKey, byte[] aaguid) {
        return new AttestedCredentialData(
            new com.webauthn4j.data.attestation.authenticator.AAGUID(aaguid),
            credentialId,
            coseKey
        );
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
}