package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserTotp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTotpRepository extends JpaRepository<UserTotp, Long> {
    /**
     * 根据用户 ID 查询 TOTP 配置
     */
    Optional<UserTotp> findByUserId(Long userId);

    /**
     * 检查用户是否启用了 TOTP
     */
    boolean existsByUserIdAndIsEnabledTrue(Long userId);
}
