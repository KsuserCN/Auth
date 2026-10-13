package cn.ksuser.api.dto;

public class QrScanPreviewResponse {
    private String codeType;
    private String clientName;
    private String browser;
    private String system;
    private String ipAddress;
    private String ipLocation;
    private Long expiresInSeconds;
    private String targetName;
    private String targetOrigin;
    private String targetLogoUrl;
    private java.util.List<String> requestedScopes;
    private Boolean consoleApplication;
    private String authorizationTicket;
    public String getAuthorizationTicket() { return authorizationTicket; }
    public void setAuthorizationTicket(String value) { authorizationTicket = value; }

    public String getTargetLogoUrl() { return targetLogoUrl; }
    public void setTargetLogoUrl(String value) { targetLogoUrl = value; }
    public java.util.List<String> getRequestedScopes() { return requestedScopes; }
    public void setRequestedScopes(java.util.List<String> value) { requestedScopes = value; }
    public Boolean getConsoleApplication() { return consoleApplication; }
    public void setConsoleApplication(Boolean value) { consoleApplication = value; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getTargetOrigin() { return targetOrigin; }
    public void setTargetOrigin(String targetOrigin) { this.targetOrigin = targetOrigin; }

    public QrScanPreviewResponse() {
    }

    public QrScanPreviewResponse(
            String codeType,
            String clientName,
            String browser,
            String system,
            String ipAddress,
            String ipLocation,
            Long expiresInSeconds) {
        this.codeType = codeType;
        this.clientName = clientName;
        this.browser = browser;
        this.system = system;
        this.ipAddress = ipAddress;
        this.ipLocation = ipLocation;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getCodeType() {
        return codeType;
    }

    public void setCodeType(String codeType) {
        this.codeType = codeType;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIpLocation() {
        return ipLocation;
    }

    public void setIpLocation(String ipLocation) {
        this.ipLocation = ipLocation;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
