package cn.ksuser.api.repository;

import cn.ksuser.api.entity.Oauth2AppPlatform;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface Oauth2AppPlatformRepository extends JpaRepository<Oauth2AppPlatform, Long> {
    List<Oauth2AppPlatform> findAllByAppId(String appId);
    Optional<Oauth2AppPlatform> findByAppIdAndPlatform(String appId, String platform);
    @Modifying(clearAutomatically = true) @Transactional
    @Query("update Oauth2AppPlatform p set p.lastVerificationAttemptAt = :now, p.version = p.version + 1 where p.appId = :appId and p.platform = :platform and p.configured = true and p.configRevision = :revision and (p.lastVerificationAttemptAt is null or p.lastVerificationAttemptAt <= :cutoff)")
    int claimVerification(@Param("appId") String appId, @Param("platform") String platform,
                          @Param("revision") String revision, @Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
    @Modifying(clearAutomatically = true) @Transactional
    @Query("update Oauth2AppPlatform p set p.verificationStatus = :status, p.verificationMessage = :message, p.verifiedAt = :verifiedAt, p.version = p.version + 1 where p.appId = :appId and p.platform = :platform and p.configured = true and p.configRevision = :revision and p.lastVerificationAttemptAt = :attempt")
    int finishVerification(@Param("appId") String appId, @Param("platform") String platform,
                           @Param("revision") String revision, @Param("attempt") LocalDateTime attempt,
                           @Param("status") String status, @Param("message") String message, @Param("verifiedAt") LocalDateTime verifiedAt);
}
