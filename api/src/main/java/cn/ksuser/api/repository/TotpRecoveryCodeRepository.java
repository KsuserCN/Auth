package cn.ksuser.api.repository;

import cn.ksuser.api.entity.TotpRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, Long> {
    /**
     * 查询用户的所有未使用的恢复码
     * 未使用 = used_at IS NULL
     */
    @Query("SELECT r FROM TotpRecoveryCode r WHERE r.userId = ?1 AND r.usedAt IS NULL ORDER BY r.createdAt ASC")
    List<TotpRecoveryCode> findByUserIdAndUnusedOrderByCreatedAtAsc(Long userId);

    /**
     * 根据用户 ID 和恢复码哈希查询单个恢复码
     */
    Optional<TotpRecoveryCode> findByUserIdAndCodeHash(Long userId, byte[] codeHash);

    /**
     * 查询用户的所有恢复码
     */
    List<TotpRecoveryCode> findByUserIdOrderByCreatedAtAsc(Long userId);

    /**
     * 统计用户未使用的恢复码数量
     */
    @Query("SELECT COUNT(r) FROM TotpRecoveryCode r WHERE r.userId = ?1 AND r.usedAt IS NULL")
    long countByUserIdAndUnused(Long userId);

    /**
     * 删除用户的所有恢复码
     */
    void deleteByUserId(Long userId);

    /**
     * 查询用户的最近生成的未使用恢复码
     */
    @Query("SELECT r FROM TotpRecoveryCode r WHERE r.userId = ?1 AND r.usedAt IS NULL ORDER BY r.createdAt DESC LIMIT ?2")
    List<TotpRecoveryCode> findLatestRecoveryCodes(Long userId, int limit);
}
