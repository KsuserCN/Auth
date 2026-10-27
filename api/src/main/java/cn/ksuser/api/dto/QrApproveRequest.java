package cn.ksuser.api.dto;

public class QrApproveRequest {
    private String approveCode;
    private String proofId;

    public String getProofId() { return proofId; }
    public void setProofId(String proofId) { this.proofId = proofId; }

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
