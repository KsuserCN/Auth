package cn.ksuser.api.dto;

import cn.ksuser.api.entity.User;

public class RegisterResult {

    public enum Status {
        SUCCESS,
        USERNAME_EXISTS,
        EMAIL_EXISTS
    }

    private final Status status;
    private final User user;

    public RegisterResult(Status status, User user) {
        this.status = status;
        this.user = user;
    }

    public Status getStatus() {
        return status;
    }

    public User getUser() {
        return user;
    }
}
