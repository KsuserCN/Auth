package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "console_audit_logs")
public class ConsoleAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="actor_uuid", length=36, nullable=false, columnDefinition="CHAR(36) CHARACTER SET ascii COLLATE ascii_bin") private String actorUuid;
    @Column(length=128, nullable=false) private String target;
    @Column(length=64, nullable=false) private String action;
    @Column(columnDefinition="TEXT", nullable=false) private String details;
    @Column(name="ip_address", length=45) private String ipAddress;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    public ConsoleAuditLog() {}
    public ConsoleAuditLog(String actorUuid, String target, String action, String details, String ipAddress) {
        this.actorUuid=actorUuid; this.target=target; this.action=action; this.details=details;
        this.ipAddress=ipAddress; this.createdAt=LocalDateTime.now();
    }
    public Long getId() { return id; }
    public String getActorUuid() { return actorUuid; }
    public String getTarget() { return target; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
