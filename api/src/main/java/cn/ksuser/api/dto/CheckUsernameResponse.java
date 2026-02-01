package cn.ksuser.api.dto;

public class CheckUsernameResponse {
    private boolean exists;

    public CheckUsernameResponse(boolean exists) {
        this.exists = exists;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }
}
