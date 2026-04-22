package cn.ksuser.api.dto;

public class SendCodeRequest {
    private String email;
    private String type; // "register" 或 "login"

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
