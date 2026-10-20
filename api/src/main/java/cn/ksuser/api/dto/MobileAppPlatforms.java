package cn.ksuser.api.dto;

import java.util.List;

/** One product may register both native platforms under the same OAuth AppID. */
public record MobileAppPlatforms(Ios ios, Android android) {
    public record Ios(String bundleId, String appIdPrefix, String universalLink, String urlScheme,
                      String appStoreId, String verificationStatus, String verificationMessage, String verifiedAt) { }
    public record Android(String packageName, List<String> sha256CertFingerprints, String appLink,
                          String downloadUrl, String verificationStatus, String verificationMessage, String verifiedAt) { }
}
