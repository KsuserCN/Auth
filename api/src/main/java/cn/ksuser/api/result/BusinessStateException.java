package cn.ksuser.api.result;

/** Unavailable business state; details and causes never become public messages. */
public class BusinessStateException extends IllegalStateException {
    private final ApiResult result;
    public BusinessStateException(ApiResultCode code) { this(ApiResult.of(code)); }
    public BusinessStateException(ApiResult result) { super(result.errorCode().name()); this.result = result; }
    public BusinessStateException(ApiResultCode code, Throwable cause) { this(ApiResult.of(code), cause); }
    public BusinessStateException(ApiResult result, Throwable cause) { super(result.errorCode().name(), cause); this.result = result; }
    public ApiResult getResult() { return result; }
}
