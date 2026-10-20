package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oauth2_unionid_applications")
public class UnionidApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", referencedColumnName = "app_id", insertable = false, updatable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Oauth2Application application;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "review_reason", length = 1000)
    private String reviewReason;
    @Column(name = "reviewer_uuid", length = 36)
    private String reviewerUuid;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist void initialize() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getAppId() { return appId; }
    public void setAppId(String value) { appId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String value) { reviewReason = value; }
    public String getReviewerUuid() { return reviewerUuid; }
    public void setReviewerUuid(String value) { reviewerUuid = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt = value; }
}
