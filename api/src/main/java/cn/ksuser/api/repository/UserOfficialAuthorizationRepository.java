package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserOfficialAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserOfficialAuthorizationRepository extends JpaRepository<UserOfficialAuthorization, Long> {
    Optional<UserOfficialAuthorization> findByUserIdAndClientId(Long userId, String clientId);
    List<UserOfficialAuthorization> findAllByUserIdOrderByLastAuthorizedAtDesc(Long userId);
    void deleteByUserIdAndClientId(Long userId, String clientId);
}
