package cn.ksuser.api.dto;

public class AccountRecoveryCompleteRequest {
    private String recoveryCode;
    private String newPassword;

    public AccountRecoveryCompleteRequest() {
    }

    public AccountRecoveryCompleteRequest(String recoveryCode, String newPassword) {
        this.recoveryCode = recoveryCode;
        this.newPassword = newPassword;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
