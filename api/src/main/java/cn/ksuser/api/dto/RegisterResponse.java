package cn.ksuser.api.dto;

import cn.ksuser.api.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class RegisterResponse {
    private String uuid;
    private String username;
    private String email;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public RegisterResponse() {
    }

    public RegisterResponse(String uuid, String username, String email, LocalDateTime createdAt) {
        this.uuid = uuid;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    public static RegisterResponse fromUser(User user) {
        return new RegisterResponse(
            user.getUuid(),
            user.getUsername(),
            user.getEmail(),
            user.getCreatedAt()
        );
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
