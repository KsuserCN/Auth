package cn.ksuser.api.dto;

public class UserSettingsResponse {
    private boolean mfaEnabled;
    private boolean detectUnusualLogin;
    private boolean notifySensitiveActionEmail;
    private boolean subscribeNewsEmail;
    private String preferredMfaMethod;
    private String preferredSensitiveMethod;

    public UserSettingsResponse() {
    }

    public UserSettingsResponse(boolean mfaEnabled, boolean detectUnusualLogin,
                                boolean notifySensitiveActionEmail, boolean subscribeNewsEmail) {
        this.mfaEnabled = mfaEnabled;
        this.detectUnusualLogin = detectUnusualLogin;
        this.notifySensitiveActionEmail = notifySensitiveActionEmail;
        this.subscribeNewsEmail = subscribeNewsEmail;
        this.preferredMfaMethod = "totp";
        this.preferredSensitiveMethod = "password";
    }

    public UserSettingsResponse(boolean mfaEnabled, boolean detectUnusualLogin,
                                boolean notifySensitiveActionEmail, boolean subscribeNewsEmail,
                                String preferredMfaMethod, String preferredSensitiveMethod) {
        this.mfaEnabled = mfaEnabled;
        this.detectUnusualLogin = detectUnusualLogin;
        this.notifySensitiveActionEmail = notifySensitiveActionEmail;
        this.subscribeNewsEmail = subscribeNewsEmail;
        this.preferredMfaMethod = preferredMfaMethod;
        this.preferredSensitiveMethod = preferredSensitiveMethod;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public boolean isDetectUnusualLogin() {
        return detectUnusualLogin;
    }

    public void setDetectUnusualLogin(boolean detectUnusualLogin) {
        this.detectUnusualLogin = detectUnusualLogin;
    }

    public boolean isNotifySensitiveActionEmail() {
        return notifySensitiveActionEmail;
    }

    public void setNotifySensitiveActionEmail(boolean notifySensitiveActionEmail) {
        this.notifySensitiveActionEmail = notifySensitiveActionEmail;
    }

    public boolean isSubscribeNewsEmail() {
        return subscribeNewsEmail;
    }

    public void setSubscribeNewsEmail(boolean subscribeNewsEmail) {
        this.subscribeNewsEmail = subscribeNewsEmail;
    }

    public String getPreferredMfaMethod() {
        return preferredMfaMethod;
    }

    public void setPreferredMfaMethod(String preferredMfaMethod) {
        this.preferredMfaMethod = preferredMfaMethod;
    }

    public String getPreferredSensitiveMethod() {
        return preferredSensitiveMethod;
    }

    public void setPreferredSensitiveMethod(String preferredSensitiveMethod) {
        this.preferredSensitiveMethod = preferredSensitiveMethod;
    }
}
