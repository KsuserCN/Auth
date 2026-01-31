package cn.ksuser.api.service;

import cn.ksuser.api.dto.RegisterResult;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
     * 刷新 Token 版本，使旧 AccessToken 失效
     * @param user 用户
     * @return 更新后的用户
     */
    public User bumpTokenVersion(User user) {
        Integer current = user.getTokenVersion();
        if (current == null) {
            current = 0;
        }
        user.setTokenVersion(current + 1);
        return userRepository.save(user);
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
}
