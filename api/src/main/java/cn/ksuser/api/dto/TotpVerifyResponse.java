package cn.ksuser.api.dto;

public class TotpVerifyResponse {
    private boolean success;
    public TotpVerifyResponse() { }
    public TotpVerifyResponse(boolean success) { this.success = success; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
