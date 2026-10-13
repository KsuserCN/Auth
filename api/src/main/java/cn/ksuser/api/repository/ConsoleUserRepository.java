package cn.ksuser.api.repository;
import cn.ksuser.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ConsoleUserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUuid(String uuid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.uuid=:uuid")
    Optional<User> findByUuidForUpdate(@Param("uuid") String uuid);

    // The entity column remains read-only so ordinary profile saves cannot overwrite certification.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE users SET verification_type=:type, verification_entity_name=:name, updated_at=:updatedAt WHERE id=:id", nativeQuery = true)
    int updateVerification(@Param("id") Long id, @Param("type") String type, @Param("name") String name,
                           @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
