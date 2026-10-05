package cn.ksuser.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_push_devices", uniqueConstraints = {
    @UniqueConstraint(name = "uk_push_device_token", columnNames = {"device_token", "environment"}),
    @UniqueConstraint(name = "uk_push_device_session", columnNames = "session_id")
})
public class UserPushDevice {
    public enum Mode { ALL, LOGIN_AND_ABNORMAL, OFF }
    public enum Environment { SANDBOX, PRODUCTION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserSession session;

    @Column(name = "device_token", nullable = false, length = 512)
    private String deviceToken;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Environment environment;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Mode mode;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
