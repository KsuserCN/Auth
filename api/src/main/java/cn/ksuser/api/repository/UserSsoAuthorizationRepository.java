package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserSsoAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSsoAuthorizationRepository extends JpaRepository<UserSsoAuthorization, Long> {
    List<UserSsoAuthorization> findAllByUserIdOrderByLastAuthorizedAtDesc(Long userId);
    Optional<UserSsoAuthorization> findByUserIdAndClientId(Long userId, String clientId);
    void deleteByUserIdAndClientId(Long userId, String clientId);
}
