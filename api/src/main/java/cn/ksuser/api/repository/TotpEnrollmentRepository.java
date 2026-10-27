package cn.ksuser.api.repository;

import cn.ksuser.api.entity.TotpEnrollment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface TotpEnrollmentRepository extends JpaRepository<TotpEnrollment, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TotpEnrollment e where e.id = :id and e.userId = :userId")
    Optional<TotpEnrollment> findForUpdate(@Param("id") String id, @Param("userId") Long userId);

    List<TotpEnrollment> findByUserIdAndCompletedAtIsNullAndCancelledAtIsNull(Long userId);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
