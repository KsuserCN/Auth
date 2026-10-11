package cn.ksuser.api.exception;

import cn.ksuser.api.result.ApiResult;
import cn.ksuser.api.result.ApiResultCode;
import org.springframework.http.HttpStatus;

public class Oauth2Exception extends RuntimeException {
    private final HttpStatus status;
    private final String error;
    private final ApiResult result;
    public Oauth2Exception(HttpStatus status, String error, ApiResultCode code) { this(status, error, ApiResult.of(code)); }
    public Oauth2Exception(HttpStatus status, String error, ApiResult result) {
        super(result.errorCode().name());
        this.status = status;
        this.error = error;
        this.result = result;
    }
    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
    public ApiResult getResult() { return result; }
    /** The OAuth optional description contains only the stable identifier. */
    public String getDescription() { return result.errorCode().name(); }
}
