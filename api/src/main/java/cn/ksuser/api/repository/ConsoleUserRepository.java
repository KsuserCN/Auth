package cn.ksuser.api.repository;
import cn.ksuser.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ConsoleUserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUuid(String uuid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.uuid=:uuid")
    Optional<User> findByUuidForUpdate(@Param("uuid") String uuid);
}
