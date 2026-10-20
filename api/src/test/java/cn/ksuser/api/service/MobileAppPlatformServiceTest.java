package cn.ksuser.api.service;

import cn.ksuser.api.dto.MobileAppPlatforms;
import cn.ksuser.api.entity.Oauth2AppPlatform;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2AppPlatformRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileAppPlatformServiceTest {
    private final Oauth2AppPlatformRepository repository = mock(Oauth2AppPlatformRepository.class);
    private final MobileAssociationVerifier verifier = mock(MobileAssociationVerifier.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MobileAppPlatformService service = new MobileAppPlatformService(repository, mapper, verifier);
    private final Map<String, Oauth2AppPlatform> rows = new HashMap<>();
    private static final String APP = "app", CALLBACK = "https://login.example.com/oauth/callback";

    @BeforeEach void setup() {
        when(repository.findByAppIdAndPlatform(eq(APP), anyString())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(1))));
        when(repository.findAllByAppId(APP)).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(repository.save(any())).thenAnswer(i -> { Oauth2AppPlatform row = i.getArgument(0); rows.put(row.getPlatform(), row); return row; });
        doAnswer(i -> { rows.remove(((Oauth2AppPlatform) i.getArgument(0)).getPlatform()); return null; }).when(repository).delete(any());
        when(repository.claimVerification(eq(APP), anyString(), anyString(), any(), any())).thenReturn(1);
        when(repository.finishVerification(eq(APP), anyString(), anyString(), any(), anyString(), anyString(), any())).thenAnswer(i -> {
            var row = rows.get(i.getArgument(1));
            if (row == null || !row.getConfigRevision().equals(i.getArgument(2))) return 0;
            row.setVerificationStatus(i.getArgument(4)); row.setVerificationMessage(i.getArgument(5)); row.setVerifiedAt(i.getArgument(6)); return 1;
        });
    }
    private MobileAppPlatforms.Ios ios(String callback) { return new MobileAppPlatforms.Ios("com.example.app", "TEAMID1234", callback, "example", "123456", "verified", "client forgery", "yesterday"); }
    private MobileAppPlatforms.Android android() { return new MobileAppPlatforms.Android("com.example.app", List.of("ab".repeat(32)), "https://android.example.com/oauth/callback", null, "verified", null, null); }
    private MobileAppPlatforms both() { return new MobileAppPlatforms(ios(CALLBACK), android()); }

    @Test void normalizeDiscardsClaimedVerificationAndSupportsTwoPlatforms() {
        var result = service.normalize(both());
        assertEquals("unverified", result.ios().verificationStatus()); assertNull(result.ios().verificationMessage()); assertNull(result.ios().verifiedAt());
        assertEquals("AB:".repeat(31) + "AB", result.android().sha256CertFingerprints().getFirst());
        assertEquals(CALLBACK + ";https://android.example.com/oauth/callback", service.redirectUris(result));
    }
    @Test void rejectsMissingPlatformAndIncompleteIosOrAndroid() {
        assertThrows(Oauth2Exception.class, () -> service.normalize(null));
        assertThrows(Oauth2Exception.class, () -> service.normalize(new MobileAppPlatforms(null, null)));
        assertThrows(Oauth2Exception.class, () -> service.normalize(new MobileAppPlatforms(new MobileAppPlatforms.Ios("", "TEAMID1234", CALLBACK, null, null, null, null, null), null)));
        assertThrows(Oauth2Exception.class, () -> service.normalize(new MobileAppPlatforms(null, new MobileAppPlatforms.Android("com.app", List.of("MD5"), CALLBACK, null, null, null, null))));
    }
    @Test void rejectsUnsafeOrAmbiguousCallbacks() {
        for (String uri : List.of("http://example.com/cb", "example://cb", "https://user@example.com/cb", "https://example.com:8443/cb", "https://example.com/cb?q=1", "https://example.com/cb#x", "https://example.com/a/../cb", "https://example.com/a/%2e%2e/cb", "https://example.com/a%5ccb", "https://example.com")) {
            assertThrows(Oauth2Exception.class, () -> service.normalize(new MobileAppPlatforms(ios(uri), null)), uri);
        }
    }
    @Test void sameConfigurationPreservesVerificationButChangeResetsIt() {
        service.savePlatforms(APP, both());
        var row = rows.get("ios"); String revision = row.getConfigRevision();
        row.setVerificationStatus("verified"); row.setVerifiedAt(LocalDateTime.now());
        service.savePlatforms(APP, both());
        assertEquals(revision, rows.get("ios").getConfigRevision()); assertEquals("verified", service.getPlatforms(APP).ios().verificationStatus());
        service.savePlatforms(APP, new MobileAppPlatforms(ios(CALLBACK + "/new"), null));
        assertNotEquals(revision, rows.get("ios").getConfigRevision()); assertEquals("unverified", service.getPlatforms(APP).ios().verificationStatus());
        assertNull(service.getPlatforms(APP).android()); assertNull(service.getPlatforms(APP).ios().verifiedAt());
    }
    @Test void nativeOnlyAllowsExactVerifiedIosAndReturnsRevision() {
        service.savePlatforms(APP, both());
        assertThrows(Oauth2Exception.class, () -> service.validateNative(APP, "ios", CALLBACK));
        rows.get("ios").setVerificationStatus("verified");
        assertEquals(rows.get("ios").getConfigRevision(), service.nativeRevision(APP, "ios", CALLBACK));
        assertThrows(Oauth2Exception.class, () -> service.validateNative(APP, "android", CALLBACK));
        assertThrows(Oauth2Exception.class, () -> service.validateMobileRedirect(APP, CALLBACK + "/other"));
        assertThrows(Oauth2Exception.class, () -> service.validateMobileRedirect(APP, "https://android.example.com/oauth/callback"));
    }
    @Test void verificationSuccessUpdatesOnlyMatchingConfiguration() {
        service.savePlatforms(APP, both());
        var result = service.verifyPlatform(APP, "ios");
        assertEquals("verified", result.ios().verificationStatus()); assertNotNull(result.ios().verifiedAt());
        verify(verifier).verifyIos(any());
    }
    @Test void androidCanBeVerifiedWhileNativeAuthorizationRemainsUnavailable() {
        service.savePlatforms(APP, both());
        assertEquals("verified", service.verifyPlatform(APP, "android").android().verificationStatus());
        assertThrows(Oauth2Exception.class, () -> service.validateNative(APP, "android", android().appLink()));
    }
    @Test void verificationFailureReturnsSafeStatusAndRevokesPreviousVerification() {
        service.savePlatforms(APP, both()); rows.get("ios").setVerificationStatus("verified");
        doThrow(MobileAssociationVerifier.failure("证书不匹配")).when(verifier).verifyIos(any());
        var result = service.verifyPlatform(APP, "ios");
        assertEquals("failed", result.ios().verificationStatus()); assertEquals("证书不匹配", result.ios().verificationMessage()); assertNull(result.ios().verifiedAt());
    }
    @Test void configurationChangedDuringRequestCannotBeMarkedVerified() {
        service.savePlatforms(APP, both());
        doAnswer(i -> { service.savePlatforms(APP, new MobileAppPlatforms(ios(CALLBACK + "/new"), null)); return null; }).when(verifier).verifyIos(any());
        var result = service.verifyPlatform(APP, "ios");
        assertEquals(CALLBACK + "/new", result.ios().universalLink()); assertEquals("unverified", result.ios().verificationStatus());
    }
    @Test void rateLimitIsEnforcedBeforeNetworkRequest() {
        service.savePlatforms(APP, both());
        when(repository.claimVerification(eq(APP), anyString(), anyString(), any(), any())).thenReturn(0);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(Oauth2Exception.class, () -> service.verifyPlatform(APP, "ios")).getStatus());
        verifyNoInteractions(verifier);
    }
    @Test void malformedOrMissingPlatformIsRejected() {
        assertThrows(Oauth2Exception.class, () -> service.verifyPlatform(APP, "../ios"));
        assertThrows(Oauth2Exception.class, () -> service.verifyPlatform(APP, "ios"));
    }
    @Test void androidDownloadMayUseStoreQueryParameters() {
        var a = android();
        var result = service.normalize(new MobileAppPlatforms(null, new MobileAppPlatforms.Android(a.packageName(), a.sha256CertFingerprints(), a.appLink(), "https://play.google.com/store/apps/details?id=com.example.app", null, null, null)));
        assertEquals("https://play.google.com/store/apps/details?id=com.example.app", result.android().downloadUrl());
    }
    @Test void removingAndReaddingPlatformPreservesCooldownAndChangesRevision() {
        service.savePlatforms(APP, both());
        LocalDateTime attempt = LocalDateTime.now(); rows.get("ios").setLastVerificationAttemptAt(attempt);
        String firstRevision = rows.get("ios").getConfigRevision();
        service.savePlatforms(APP, new MobileAppPlatforms(null, android()));
        assertFalse(rows.get("ios").isConfigured()); assertNull(service.getPlatforms(APP).ios());
        assertThrows(Oauth2Exception.class, () -> service.verifyPlatform(APP, "ios"));
        service.savePlatforms(APP, both());
        assertTrue(rows.get("ios").isConfigured()); assertEquals(attempt, rows.get("ios").getLastVerificationAttemptAt());
        assertNotEquals(firstRevision, rows.get("ios").getConfigRevision());
    }
}
