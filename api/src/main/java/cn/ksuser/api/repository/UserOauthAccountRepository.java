package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserOauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, Long> {
    Optional<UserOauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
    Optional<UserOauthAccount> findByProviderAndUnionId(String provider, String unionId);
    Optional<UserOauthAccount> findByProviderAndUserId(String provider, Long userId);
}
