package cn.ksuser.api.dto;

import java.util.List;

public class SsoClientsOverviewResponse {
    private String verificationType;
    private boolean admin;
    private int maxClients;
    private int currentCount;
    private boolean canCreate;
    private List<SsoClientResponse> clients;

    public SsoClientsOverviewResponse() {
    }

    public SsoClientsOverviewResponse(String verificationType, boolean admin, int maxClients,
                                      int currentCount, boolean canCreate,
                                      List<SsoClientResponse> clients) {
        this.verificationType = verificationType;
        this.admin = admin;
        this.maxClients = maxClients;
        this.currentCount = currentCount;
        this.canCreate = canCreate;
        this.clients = clients;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(String verificationType) {
        this.verificationType = verificationType;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public int getMaxClients() {
        return maxClients;
    }

    public void setMaxClients(int maxClients) {
        this.maxClients = maxClients;
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

    public List<SsoClientResponse> getClients() {
        return clients;
    }

    public void setClients(List<SsoClientResponse> clients) {
        this.clients = clients;
    }
}
