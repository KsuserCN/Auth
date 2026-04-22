package cn.ksuser.api.dto;

public class AccountRecoveryStatusResponse {
    private long expiresInSeconds;
    private String username;
    private String maskedEmail;
    private String sponsorClientName;
    private String sponsorBrowser;
    private String sponsorSystem;
    private String sponsorIpLocation;

    public AccountRecoveryStatusResponse() {
    }

    public AccountRecoveryStatusResponse(
            long expiresInSeconds,
            String username,
            String maskedEmail,
            String sponsorClientName,
            String sponsorBrowser,
            String sponsorSystem,
            String sponsorIpLocation) {
        this.expiresInSeconds = expiresInSeconds;
        this.username = username;
        this.maskedEmail = maskedEmail;
        this.sponsorClientName = sponsorClientName;
        this.sponsorBrowser = sponsorBrowser;
        this.sponsorSystem = sponsorSystem;
        this.sponsorIpLocation = sponsorIpLocation;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public void setMaskedEmail(String maskedEmail) {
        this.maskedEmail = maskedEmail;
    }

    public String getSponsorClientName() {
        return sponsorClientName;
    }

    public void setSponsorClientName(String sponsorClientName) {
        this.sponsorClientName = sponsorClientName;
    }

    public String getSponsorBrowser() {
        return sponsorBrowser;
    }

    public void setSponsorBrowser(String sponsorBrowser) {
        this.sponsorBrowser = sponsorBrowser;
    }

    public String getSponsorSystem() {
        return sponsorSystem;
    }

    public void setSponsorSystem(String sponsorSystem) {
        this.sponsorSystem = sponsorSystem;
    }

    public String getSponsorIpLocation() {
        return sponsorIpLocation;
    }

    public void setSponsorIpLocation(String sponsorIpLocation) {
        this.sponsorIpLocation = sponsorIpLocation;
    }
}
