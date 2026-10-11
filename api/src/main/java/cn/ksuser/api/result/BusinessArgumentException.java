package cn.ksuser.api.result;

/** Invalid business input; preserves the IllegalArgumentException catch hierarchy. */
public class BusinessArgumentException extends IllegalArgumentException {
    private final ApiResult result;
    public BusinessArgumentException(ApiResultCode code) { this(ApiResult.of(code)); }
    public BusinessArgumentException(ApiResult result) { super(result.errorCode().name()); this.result = result; }
    public BusinessArgumentException(ApiResultCode code, Throwable cause) { this(ApiResult.of(code), cause); }
    public BusinessArgumentException(ApiResult result, Throwable cause) { super(result.errorCode().name(), cause); this.result = result; }
    public ApiResult getResult() { return result; }
}
