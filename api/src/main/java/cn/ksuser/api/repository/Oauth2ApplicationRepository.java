package cn.ksuser.api.repository;

import cn.ksuser.api.entity.Oauth2Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Oauth2ApplicationRepository extends JpaRepository<Oauth2Application, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Oauth2Application a where a.appId = :appId")
    Optional<Oauth2Application> findByAppIdForUpdate(@org.springframework.data.repository.query.Param("appId") String appId);
    Optional<Oauth2Application> findByAppId(String appId);
    List<Oauth2Application> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
    long countByOwnerUserId(Long ownerUserId);
    boolean existsByAppId(String appId);
}
