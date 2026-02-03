package cn.ksuser.api.service;

import cn.ksuser.api.dto.RegisterResult;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户
     * @param username 用户名
     * @param email 邮箱
     * @param password 密码
     * @return 注册结果（包含状态与用户）
     */
    public RegisterResult register(String username, String email, String password) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            return new RegisterResult(RegisterResult.Status.USERNAME_EXISTS, null);
        }

        // 检查邮箱是否已存在
        if (userRepository.findByEmail(email).isPresent()) {
            return new RegisterResult(RegisterResult.Status.EMAIL_EXISTS, null);
        }

        // 生成 UUID
        String uuid = UUID.randomUUID().toString();

        // 对密码进行加密
        String passwordHash = passwordEncoder.encode(password);

        // 创建新用户
        User user = new User(uuid, username, email, passwordHash);
        User savedUser = userRepository.save(user);
        return new RegisterResult(RegisterResult.Status.SUCCESS, savedUser);
    }

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return Optional<User>
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据 UUID 查询用户
     * @param uuid UUID
     * @return Optional<User>
     */
    public Optional<User> findByUuid(String uuid) {
        return userRepository.findByUuid(uuid);
    }

    /**
     * 根据邮箱查询用户
     * @param email 邮箱
     * @return Optional<User>
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * 验证密码
     * @param password 明文密码
     * @param passwordHash 加密后的密码
     * @return 是否匹配
     */
    public boolean verifyPassword(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }

    /**
     * 用户登录
     * @param email 邮箱
     * @param password 密码
     * @return 登录成功返回用户，否则返回 Optional.empty
     */
    public Optional<User> login(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    /**
     * 更新用户信息
     * @param user 用户对象
     * @param newUsername 新用户名（null 表示不更新）
     * @param newAvatarUrl 新头像 URL（null 表示不更新）
     * @return 更新结果（包含状态与用户）
     */
    public RegisterResult updateProfile(User user, String newUsername, String newAvatarUrl,
                                       String newRealName, String newGender, LocalDateTime newBirthDate,
                                       String newRegion, String newBio) {
        // 如果需要更新用户名，检查新用户名是否已存在
        if (newUsername != null && !newUsername.trim().isEmpty()) {
            if (!newUsername.equals(user.getUsername()) && userRepository.findByUsername(newUsername).isPresent()) {
                return new RegisterResult(RegisterResult.Status.USERNAME_EXISTS, null);
            }
            user.setUsername(newUsername);
        }

        // 如果需要更新头像 URL
        if (newAvatarUrl != null && !newAvatarUrl.trim().isEmpty()) {
            user.setAvatarUrl(newAvatarUrl);
        }

        // 如果需要更新真实姓名
        if (newRealName != null && !newRealName.trim().isEmpty()) {
            user.setRealName(newRealName);
        }

        // 如果需要更新性别
        if (newGender != null && !newGender.trim().isEmpty()) {
            user.setGender(newGender);
        }

        // 如果需要更新出生日期
        if (newBirthDate != null) {
            user.setBirthDate(newBirthDate);
        }

        // 如果需要更新地区
        if (newRegion != null && !newRegion.trim().isEmpty()) {
            user.setRegion(newRegion);
        }

        // 如果需要更新个人简介
        if (newBio != null && !newBio.trim().isEmpty()) {
            user.setBio(newBio);
        }

        // 设置更新时间
        user.setUpdatedAt(LocalDateTime.now());

        // 保存更新
        User updatedUser = userRepository.save(user);
        return new RegisterResult(RegisterResult.Status.SUCCESS, updatedUser);
    }

    /**
     * 保存用户
     * @param user 用户对象
     * @return 保存后的用户
     */
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * 更新用户密码
     * @param user 用户对象
     * @param newPassword 新密码（明文）
     * @return 保存后的用户
     */
    public User updatePassword(User user, String newPassword) {
        String passwordHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }
}
