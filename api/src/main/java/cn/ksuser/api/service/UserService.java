package cn.ksuser.api.service;

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
     * @return 注册成功返回 User，否则返回 null
     */
    public User register(String username, String email, String password) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            return null;
        }

        // 检查邮箱是否已存在
        if (userRepository.findByEmail(email).isPresent()) {
            return null;
        }

        // 生成 UUID
        String uuid = UUID.randomUUID().toString();

        // 对密码进行加密
        String passwordHash = passwordEncoder.encode(password);

        // 创建新用户
        User user = new User(uuid, username, email, passwordHash);
        return userRepository.save(user);
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
     * 验证密码
     * @param password 明文密码
     * @param passwordHash 加密后的密码
     * @return 是否匹配
     */
    public boolean verifyPassword(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }
}
