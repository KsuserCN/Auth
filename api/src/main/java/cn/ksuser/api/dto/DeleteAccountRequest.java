package cn.ksuser.api.dto;

public class DeleteAccountRequest {
    private String confirmText;

    public DeleteAccountRequest() {
    }

    public DeleteAccountRequest(String confirmText) {
        this.confirmText = confirmText;
    }

    public String getConfirmText() {
        return confirmText;
    }

    public void setConfirmText(String confirmText) {
        this.confirmText = confirmText;
    }
}
