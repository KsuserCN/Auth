package cn.ksuser.api.dto;

public class UpdateUserSettingRequest {
    private String field;
    private Boolean value;

    public UpdateUserSettingRequest() {
    }

    public UpdateUserSettingRequest(String field, Boolean value) {
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Boolean getValue() {
        return value;
    }

    public void setValue(Boolean value) {
        this.value = value;
    }
}
