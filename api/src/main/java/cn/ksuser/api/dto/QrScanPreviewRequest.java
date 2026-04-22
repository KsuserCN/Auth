package cn.ksuser.api.dto;

public class QrScanPreviewRequest {
    private String approveCode;
    private String transferCode;

    public QrScanPreviewRequest() {
    }

    public QrScanPreviewRequest(String approveCode, String transferCode) {
        this.approveCode = approveCode;
        this.transferCode = transferCode;
    }

    public String getApproveCode() {
        return approveCode;
    }

    public void setApproveCode(String approveCode) {
        this.approveCode = approveCode;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }
}
