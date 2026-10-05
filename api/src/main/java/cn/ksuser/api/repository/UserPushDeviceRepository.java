package cn.ksuser.api.repository;

import cn.ksuser.api.entity.UserPushDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserPushDeviceRepository extends JpaRepository<UserPushDevice, Long> {
    Optional<UserPushDevice> findByDeviceTokenAndEnvironment(String token, UserPushDevice.Environment environment);
    Optional<UserPushDevice> findBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);

    @Query("select d from UserPushDevice d join fetch d.session s join fetch s.user u " +
           "where u.id = :userId and s.revokedAt is null and s.expiresAt > :now")
    List<UserPushDevice> findActiveDevices(Long userId, LocalDateTime now);

    @Transactional @Modifying
    @Query("delete from UserPushDevice d where d.id = :id and d.version = :version")
    int deleteInvalidDevice(Long id, Long version);

    @Transactional @Modifying
    @Query("delete from UserPushDevice d where d.session.id in " +
           "(select s.id from UserSession s where s.revokedAt is not null or s.expiresAt <= :now)")
    int deleteInactiveDevices(LocalDateTime now);
}
