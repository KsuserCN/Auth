package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserSensitiveLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserSensitiveLogRepository extends JpaRepository<UserSensitiveLog, Long> {

    Page<UserSensitiveLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT l FROM UserSensitiveLog l WHERE l.userId = :userId " +
           "AND (:startDate IS NULL OR l.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR l.createdAt <= :endDate) " +
           "AND (:operationType IS NULL OR l.operationType = :operationType) " +
           "AND (:result IS NULL OR l.result = :result) " +
           "ORDER BY l.createdAt DESC")
    Page<UserSensitiveLog> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("operationType") String operationType,
            @Param("result") UserSensitiveLog.OperationResult result,
            Pageable pageable
    );

    // ==================== 风险评分查询 ====================
    
    /**
     * 查询用户24小时内的失败记录
     */
    @Query("SELECT l FROM UserSensitiveLog l WHERE l.userId = :userId " +
           "AND l.result = 'FAILURE' " +
           "AND l.createdAt >= :afterDate " +
           "ORDER BY l.createdAt DESC")
    List<UserSensitiveLog> findRecentFailuresByUser(
            @Param("userId") Long userId,
            @Param("afterDate") LocalDateTime afterDate
    );

    /**
     * 查询用户是否出现过指定IP地址
     */
    List<UserSensitiveLog> findByUserIdAndIpAddress(Long userId, String ipAddress);

    /**
     * 查询用户最近的登录记录
     */
    List<UserSensitiveLog> findByUserIdAndOperationTypeOrderByCreatedAtDesc(Long userId, String operationType);

    /**
     * 查询用户是否使用过指定设备类型
     */
    List<UserSensitiveLog> findByUserIdAndDeviceType(Long userId, String deviceType);

    /**
     * 查询用户是否使用过指定浏览器
     */
    List<UserSensitiveLog> findByUserIdAndBrowser(Long userId, String browser);

    /**
     * 查询用户5分钟内的操作记录
     */
    List<UserSensitiveLog> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime createdAt);
}
