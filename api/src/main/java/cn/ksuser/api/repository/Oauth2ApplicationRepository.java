package cn.ksuser.api.repository;

import cn.ksuser.api.entity.Oauth2Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Oauth2ApplicationRepository extends JpaRepository<Oauth2Application, Long> {
    Optional<Oauth2Application> findByAppId(String appId);
    List<Oauth2Application> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
    long countByOwnerUserId(Long ownerUserId);
    boolean existsByAppId(String appId);
}
