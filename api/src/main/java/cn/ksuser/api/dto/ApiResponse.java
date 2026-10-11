package cn.ksuser.api.dto;

import cn.ksuser.api.result.ApiResult;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Numeric transport/flow status plus a stable, locale-independent result code. */
public class ApiResponse<T> {
    private Integer code;
    private ApiResultCode errorCode;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> params;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    public ApiResponse() { }
    public ApiResponse(Integer code, ApiResultCode resultCode) { this(code, ApiResult.of(resultCode), null); }
    public ApiResponse(Integer code, ApiResultCode resultCode, T data) { this(code, ApiResult.of(resultCode), data); }
    public ApiResponse(Integer code, ApiResult result) { this(code, result, null); }
    public ApiResponse(Integer code, ApiResult result, T data) {
        this.code = code;
        this.errorCode = result.errorCode();
        this.params = result.params();
        this.data = data;
    }
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public ApiResultCode getErrorCode() { return errorCode; }
    public void setErrorCode(ApiResultCode errorCode) { this.errorCode = errorCode; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
