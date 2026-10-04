package cn.ksuser.api.repository;
import cn.ksuser.api.entity.AppleRevocationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface AppleRevocationTaskRepository extends JpaRepository<AppleRevocationTask,Long> {
    List<AppleRevocationTask> findTop20ByNextAttemptAtBeforeOrderByNextAttemptAtAsc(LocalDateTime time);
}
