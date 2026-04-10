package cn.ksuser.api.dto;

import java.util.List;

public class Oauth2AppsOverviewResponse {
    private String verificationType;
    private boolean verified;
    private int maxApps;
    private int currentCount;
    private boolean canCreate;
    private List<Oauth2AppResponse> apps;

    public Oauth2AppsOverviewResponse() {
    }

    public Oauth2AppsOverviewResponse(String verificationType, boolean verified, int maxApps,
                                      int currentCount, boolean canCreate, List<Oauth2AppResponse> apps) {
        this.verificationType = verificationType;
        this.verified = verified;
        this.maxApps = maxApps;
        this.currentCount = currentCount;
        this.canCreate = canCreate;
        this.apps = apps;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public int getMaxApps() {
        return maxApps;
    }

    public void setMaxApps(int maxApps) {
        this.maxApps = maxApps;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(int currentCount) {
        this.currentCount = currentCount;
    }

    public boolean isCanCreate() {
        return canCreate;
    }

    public void setCanCreate(boolean canCreate) {
        this.canCreate = canCreate;
    }

    public List<Oauth2AppResponse> getApps() {
        return apps;
    }

    public void setApps(List<Oauth2AppResponse> apps) {
        this.apps = apps;
    }
}
