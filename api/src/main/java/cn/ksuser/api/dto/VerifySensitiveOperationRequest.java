package cn.ksuser.api.dto;

public class VerifySensitiveOperationRequest {
    private String method; // "password" 或 "email-code"
    private String password; // 当 method=password 时需要
    private String code; // 当 method=email-code 时需要

    public VerifySensitiveOperationRequest() {
    }

    public VerifySensitiveOperationRequest(String method, String password, String code) {
        this.method = method;
        this.password = password;
        this.code = code;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
