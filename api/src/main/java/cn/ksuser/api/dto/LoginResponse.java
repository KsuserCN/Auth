package cn.ksuser.api.dto;

public class LoginResponse {
    private String uuid;

    public LoginResponse() {
    }

    public LoginResponse(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
