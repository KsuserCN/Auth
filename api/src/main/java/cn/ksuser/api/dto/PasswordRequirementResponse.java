package cn.ksuser.api.dto;

public class PasswordRequirementResponse {
    private int minLength;
    private int maxLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigits;
    private boolean requireSpecialChars;
    private boolean rejectCommonWeakPasswords;
    private String requirementMessage;

    public PasswordRequirementResponse() {
    }

    public PasswordRequirementResponse(int minLength, int maxLength,
                                       boolean requireUppercase, boolean requireLowercase,
                                       boolean requireDigits, boolean requireSpecialChars,
                                       boolean rejectCommonWeakPasswords, String requirementMessage) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.requireUppercase = requireUppercase;
        this.requireLowercase = requireLowercase;
        this.requireDigits = requireDigits;
        this.requireSpecialChars = requireSpecialChars;
        this.rejectCommonWeakPasswords = rejectCommonWeakPasswords;
        this.requirementMessage = requirementMessage;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean isRequireUppercase() {
        return requireUppercase;
    }

    public void setRequireUppercase(boolean requireUppercase) {
        this.requireUppercase = requireUppercase;
    }

    public boolean isRequireLowercase() {
        return requireLowercase;
    }

    public void setRequireLowercase(boolean requireLowercase) {
        this.requireLowercase = requireLowercase;
    }

    public boolean isRequireDigits() {
        return requireDigits;
    }

    public void setRequireDigits(boolean requireDigits) {
        this.requireDigits = requireDigits;
    }

    public boolean isRequireSpecialChars() {
        return requireSpecialChars;
    }

    public void setRequireSpecialChars(boolean requireSpecialChars) {
        this.requireSpecialChars = requireSpecialChars;
    }

    public boolean isRejectCommonWeakPasswords() {
        return rejectCommonWeakPasswords;
    }

    public void setRejectCommonWeakPasswords(boolean rejectCommonWeakPasswords) {
        this.rejectCommonWeakPasswords = rejectCommonWeakPasswords;
    }

    public String getRequirementMessage() {
        return requirementMessage;
    }

    public void setRequirementMessage(String requirementMessage) {
        this.requirementMessage = requirementMessage;
    }
}
