package cn.ksuser.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ApiResponse<T> {
    private Integer code;
    private String msg;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ApiResponse(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
