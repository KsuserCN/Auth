package cn.ksuser.api.dto;

public class QrApproveRequest {
    private String approveCode;

    public QrApproveRequest() {
    }

    public QrApproveRequest(String approveCode) {
        this.approveCode = approveCode;
    }

    public String getApproveCode() {
        return approveCode;
    }

    public void setApproveCode(String approveCode) {
        this.approveCode = approveCode;
    }
}
