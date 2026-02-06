package cn.ksuser.api.dto;

public class UserSettingsResponse {
    private boolean mfaEnabled;
    private boolean detectUnusualLogin;
    private boolean notifySensitiveActionEmail;
    private boolean subscribeNewsEmail;

    public UserSettingsResponse() {
    }

    public UserSettingsResponse(boolean mfaEnabled, boolean detectUnusualLogin,
                                boolean notifySensitiveActionEmail, boolean subscribeNewsEmail) {
        this.mfaEnabled = mfaEnabled;
        this.detectUnusualLogin = detectUnusualLogin;
        this.notifySensitiveActionEmail = notifySensitiveActionEmail;
        this.subscribeNewsEmail = subscribeNewsEmail;
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
}
