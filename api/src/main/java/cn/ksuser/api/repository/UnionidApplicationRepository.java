package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UnionidApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UnionidApplicationRepository extends JpaRepository<UnionidApplication, Long> {
    @org.springframework.data.jpa.repository.Query("select r.appId from UnionidApplication r where r.id = :id")
    Optional<String> findAppIdById(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<UnionidApplication> findFirstByAppIdOrderByCreatedAtDescIdDesc(String appId);
    Page<UnionidApplication> findAllByStatus(String status, Pageable pageable);
}
