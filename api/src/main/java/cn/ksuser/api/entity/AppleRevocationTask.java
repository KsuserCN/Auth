package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Independent of the user FK so deletion cannot cascade away pending revocations. */
@Entity
@Table(name = "apple_revocation_tasks")
public class AppleRevocationTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="client_id",nullable=false,length=255) private String clientId;
    @Column(name="encrypted_token",nullable=false,columnDefinition="TEXT") private String encryptedToken;
    @Column(nullable=false) private int attempts;
    @Column(name="next_attempt_at",nullable=false) private LocalDateTime nextAttemptAt;
    public Long getId() { return id; }
    public String getClientId() { return clientId; }
    public void setClientId(String value) { clientId=value; }
    public String getEncryptedToken() { return encryptedToken; }
    public void setEncryptedToken(String value) { encryptedToken=value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts=value; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime value) { nextAttemptAt=value; }
}
