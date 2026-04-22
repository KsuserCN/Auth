package cn.ksuser.api.dto;

public class ChangeEmailRequest {
    private String newEmail;
    private String code;

    public ChangeEmailRequest() {
    }

    public ChangeEmailRequest(String newEmail, String code) {
        this.newEmail = newEmail;
        this.code = code;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
