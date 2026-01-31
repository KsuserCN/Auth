package cn.ksuser.api.repository;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Query("SELECT s FROM UserSession s WHERE s.user = :user AND s.expiresAt > :now")
    List<UserSession> findActiveSessions(@Param("user") User user, @Param("now") LocalDateTime now);
}
