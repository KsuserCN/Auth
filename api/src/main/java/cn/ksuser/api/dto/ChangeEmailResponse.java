package cn.ksuser.api.dto;

public class ChangeEmailResponse {
    private String uuid;
    private String username;
    private String email;
    private String avatarUrl;

    public ChangeEmailResponse() {
    }

    public ChangeEmailResponse(String uuid, String username, String email, String avatarUrl) {
        this.uuid = uuid;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public static ChangeEmailResponse fromUser(cn.ksuser.api.entity.User user) {
        return new ChangeEmailResponse(user.getUuid(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
