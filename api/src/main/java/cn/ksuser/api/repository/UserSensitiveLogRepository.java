package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserSensitiveLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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
}
