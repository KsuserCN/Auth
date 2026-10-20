package cn.ksuser.api.service;

import cn.ksuser.api.entity.Oauth2AppPlatform;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.Oauth2AppPlatformRepository;
import cn.ksuser.api.dto.MobileAppPlatforms;
import cn.ksuser.api.exception.Oauth2Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual conditional SQL, including stale results and overlapping verification claims. */
@DataJpaTest
class MobileAppPlatformRepositoryTest {
    @Autowired Oauth2AppPlatformRepository repository;
    @Autowired Oauth2ApplicationRepository applications;
    @BeforeEach void createParent() {
        var app = new Oauth2Application(); app.setAppId("test-app"); app.setOwnerUserId(1L); app.setAppName("Test App");
        app.setContactInfo("help@example.com"); app.setRedirectUri("https://login.example.com/callback");
        applications.saveAndFlush(app);
    }
    private Oauth2AppPlatform insert() {
        var row = new Oauth2AppPlatform(); row.setAppId("test-app"); row.setPlatform("ios");
        row.setConfigJson("{}"); row.setConfigRevision("revision-1");
        return repository.saveAndFlush(row);
    }
    @Test void onlyOneVerifierCanClaimTheCooldownWindow() {
        insert(); LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        assertEquals(1, repository.claimVerification("test-app", "ios", "revision-1", now, now.minusMinutes(1)));
        assertEquals(0, repository.claimVerification("test-app", "ios", "revision-1", now.plusSeconds(1), now.minusMinutes(1).plusSeconds(1)));
        assertEquals(1, repository.finishVerification("test-app", "ios", "revision-1", now, "verified", "ok", now));
        assertEquals("verified", repository.findByAppIdAndPlatform("test-app", "ios").orElseThrow().getVerificationStatus());
    }
    @Test void oldConfigurationResultCannotUpdateNewConfiguration() {
        insert(); LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        assertEquals(1, repository.claimVerification("test-app", "ios", "revision-1", now, now.minusMinutes(1)));
        var current = repository.findByAppIdAndPlatform("test-app", "ios").orElseThrow();
        current.setConfigRevision("revision-2"); current.setConfigJson("{\"changed\":true}"); repository.saveAndFlush(current);
        assertEquals(0, repository.finishVerification("test-app", "ios", "revision-1", now, "verified", "stale", now));
        assertEquals("unverified", repository.findByAppIdAndPlatform("test-app", "ios").orElseThrow().getVerificationStatus());
    }
    @Test void olderAttemptCannotOverrideANewerAttemptOnSameConfiguration() {
        insert(); LocalDateTime first = LocalDateTime.of(2026, 10, 19, 12, 0), second = first.plusMinutes(2);
        assertEquals(1, repository.claimVerification("test-app", "ios", "revision-1", first, first.minusMinutes(1)));
        assertEquals(1, repository.claimVerification("test-app", "ios", "revision-1", second, second.minusMinutes(1)));
        assertEquals(0, repository.finishVerification("test-app", "ios", "revision-1", first, "verified", "stale", first));
        assertEquals(1, repository.finishVerification("test-app", "ios", "revision-1", second, "failed", "latest", null));
        assertEquals("failed", repository.findByAppIdAndPlatform("test-app", "ios").orElseThrow().getVerificationStatus());
    }
    @Test void realDeleteAndReaddCannotBypassVerificationCooldown() {
        var verifier = mock(MobileAssociationVerifier.class);
        var service = new MobileAppPlatformService(repository, new ObjectMapper(), verifier);
        var ios = new MobileAppPlatforms.Ios("com.example.app", "TEAMID1234", "https://login.example.com/callback", null, null, null, null, null);
        var android = new MobileAppPlatforms.Android("com.example.app", List.of("AA".repeat(32)), "https://login.example.com/android", null, null, null, null);
        service.savePlatforms("test-app", new MobileAppPlatforms(ios, android)); repository.flush();
        var row = repository.findByAppIdAndPlatform("test-app", "ios").orElseThrow();
        LocalDateTime attempt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        assertEquals(1, repository.claimVerification("test-app", "ios", row.getConfigRevision(), attempt, attempt.minusMinutes(1)));
        service.savePlatforms("test-app", new MobileAppPlatforms(null, android)); repository.flush();
        service.savePlatforms("test-app", new MobileAppPlatforms(ios, android)); repository.flush();
        assertEquals(429, assertThrows(Oauth2Exception.class, () -> service.verifyPlatform("test-app", "ios")).getStatus().value());
        verifyNoInteractions(verifier);
    }
    @Test void deletingApplicationCascadesNativePlatforms() {
        insert();
        applications.delete(applications.findByAppId("test-app").orElseThrow()); applications.flush();
        assertEquals(0, repository.count());
    }
}
