package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserOauth2Authorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOauth2AuthorizationRepository extends JpaRepository<UserOauth2Authorization, Long> {
    List<UserOauth2Authorization> findAllByUserIdOrderByLastAuthorizedAtDesc(Long userId);
    Optional<UserOauth2Authorization> findByUserIdAndAppId(Long userId, String appId);
    void deleteByUserIdAndAppId(Long userId, String appId);
}
