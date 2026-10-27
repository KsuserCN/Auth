package cn.ksuser.api.dto;

/** Confirm only a server-held, independently authorized enrollment. */
public class TotpRegistrationConfirmRequest {
    private String code;
    private String enrollmentId;

    public TotpRegistrationConfirmRequest() { }
    public TotpRegistrationConfirmRequest(String enrollmentId, String code) {
        this.enrollmentId = enrollmentId; this.code = code;
    }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }
}
