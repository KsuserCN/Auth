package cn.ksuser.api.dto;

public class PasskeyRenameRequest {
    private String newName;

    public PasskeyRenameRequest() {
    }

    public PasskeyRenameRequest(String newName) {
        this.newName = newName;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }
}
