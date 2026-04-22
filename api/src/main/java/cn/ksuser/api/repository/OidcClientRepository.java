package cn.ksuser.api.repository;

import cn.ksuser.api.entity.OidcClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OidcClientRepository extends JpaRepository<OidcClient, Long> {
    Optional<OidcClient> findByClientId(String clientId);
    List<OidcClient> findAllByOrderByCreatedAtDesc();
    boolean existsByClientId(String clientId);
}
