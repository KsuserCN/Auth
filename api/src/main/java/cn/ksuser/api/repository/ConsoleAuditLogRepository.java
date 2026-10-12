package cn.ksuser.api.repository;
import cn.ksuser.api.entity.ConsoleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConsoleAuditLogRepository extends JpaRepository<ConsoleAuditLog, Long> {}
