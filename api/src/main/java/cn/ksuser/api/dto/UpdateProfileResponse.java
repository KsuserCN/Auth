package cn.ksuser.api.dto;

import java.time.LocalDateTime;

public class UpdateProfileResponse {
    private String uuid;
    private String username;
    private String email;
    private String avatarUrl;
    private String realName;
    private String gender;
    private LocalDateTime birthDate;
    private String region;
    private String bio;
    private LocalDateTime updatedAt;

    public UpdateProfileResponse() {
    }

    public UpdateProfileResponse(String uuid, String username, String email, String avatarUrl) {
        this.uuid = uuid;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public UpdateProfileResponse(String uuid, String username, String email, String avatarUrl,
                                String realName, String gender, LocalDateTime birthDate,
                                String region, String bio, LocalDateTime updatedAt) {
        this.uuid = uuid;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.realName = realName;
        this.gender = gender;
        this.birthDate = birthDate;
        this.region = region;
        this.bio = bio;
        this.updatedAt = updatedAt;
    }

    public static UpdateProfileResponse fromUser(cn.ksuser.api.entity.User user) {
        return new UpdateProfileResponse(
            user.getUuid(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getRealName(),
            user.getGender(),
            user.getBirthDate(),
            user.getRegion(),
            user.getBio(),
            user.getUpdatedAt()
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


    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDateTime getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDateTime birthDate) {
        this.birthDate = birthDate;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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
