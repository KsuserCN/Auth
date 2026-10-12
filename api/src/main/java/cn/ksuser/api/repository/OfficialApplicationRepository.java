package cn.ksuser.api.repository;

import cn.ksuser.api.entity.OfficialApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OfficialApplicationRepository extends JpaRepository<OfficialApplication, Long> {
    Optional<OfficialApplication> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
    List<OfficialApplication> findAllByOrderByCreatedAtDesc();
    List<OfficialApplication> findAllByIsActiveTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OfficialApplication a where a.clientId = :clientId")
    Optional<OfficialApplication> findByClientIdForUpdate(@Param("clientId") String clientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OfficialApplication a where a.id = :id")
    Optional<OfficialApplication> findByIdForUpdate(@Param("id") Long id);
}
