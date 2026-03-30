package cn.ksuser.api.dto;

public class UpdateUserSettingRequest {
    private String field;
    private Boolean value;
    private String stringValue;

    public UpdateUserSettingRequest() {
    }

    public UpdateUserSettingRequest(String field, Boolean value) {
        this.field = field;
        this.value = value;
    }

    public UpdateUserSettingRequest(String field, Boolean value, String stringValue) {
        this.field = field;
        this.value = value;
        this.stringValue = stringValue;
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

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
}
