package cn.ksuser.api.exception;

import org.springframework.http.HttpStatus;

public class Oauth2Exception extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final String description;

    public Oauth2Exception(HttpStatus status, String error, String description) {
        super(description);
        this.status = status;
        this.error = error;
        this.description = description;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getDescription() {
        return description;
    }
}
