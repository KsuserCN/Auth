package cn.ksuser.api.service;

import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.UnionidApplicationRepository;
import cn.ksuser.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real transactions exercise row locks and prevent stale reviews from changing newer applications. */
@SpringBootTest
class UnionidApplicationPersistenceTest {
    @Autowired UnionidApplicationService service;
    @Autowired Oauth2ApplicationRepository apps;
    @Autowired UnionidApplicationRepository requests;
    @Autowired UserRepository users;
    @MockitoBean ConsoleSessionService sessions;
    private User owner;
    private String appId;
    private final ConsoleSessionService.ConsolePrincipal admin = new ConsoleSessionService.ConsolePrincipal(
        "session", "admin-unionid-reviewer", 7L, Long.MAX_VALUE, Long.MAX_VALUE);

    @BeforeEach void setup() {
        reset(sessions);
        String suffix = UUID.randomUUID().toString();
        owner = new User(suffix, "developer-" + suffix, suffix + "@example.invalid", null);
        owner.setVerificationType("personal"); owner = users.saveAndFlush(owner);
        appId = "ksapp_" + suffix;
        Oauth2Application app = new Oauth2Application(); app.setAppId(appId); app.setOwnerUserId(owner.getId());
        app.setAppName("UnionID Test"); app.setRedirectUri("https://example.test/callback");
        app.setContactInfo("test@example.test"); apps.saveAndFlush(app);
    }
    private Long apply() {
        service.apply(owner, appId, "网站与移动应用使用统一账号");
        return service.list(0, 100, "pending").items().stream().filter(row -> appId.equals(row.appId())).findFirst().orElseThrow().id();
    }
    private String status() { return apps.findByAppId(appId).orElseThrow().getUnionidStatus(); }
    private UnionidApplicationService.View review(Long id, String action) {
        return service.review(admin, id, new UnionidApplicationService.Review(action, "审核资料与申请用途"), "127.0.0.1");
    }

    @Test void newApplicationRequiresReviewAndSupportsRejectionReapplicationWithdrawalAndRevocation() {
        assertEquals("none", status());
        Long first = apply(); assertEquals("pending", status());
        assertThrows(Oauth2Exception.class, this::apply);
        review(first, "reject"); assertEquals("rejected", status());
        Long withdrawn = apply(); service.disable(owner, appId); assertEquals("none", status());
        assertEquals("withdrawn", requests.findById(withdrawn).orElseThrow().getStatus());
        assertThrows(Oauth2Exception.class, () -> review(withdrawn, "approve"));
        Long granted = apply(); review(granted, "approve"); assertEquals("approved", status());
        service.disable(owner, appId); assertEquals("revoked", status());
        Long renewed = apply(); review(renewed, "approve");
        assertThrows(Oauth2Exception.class, () -> review(granted, "revoke"));
        assertEquals("approved", status());
        review(renewed, "revoke"); assertEquals("revoked", status());
        verify(sessions, atLeastOnce()).requireFresh(admin);
    }

    @Test void concurrentReviewsCommitExactlyOneDecision() throws Exception {
        Long id = apply(); CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> approve = () -> {
                start.await();
                try { review(id, "approve"); return true; }
                catch (Oauth2Exception ex) { assertEquals(409, ex.getStatus().value()); return false; }
            };
            Future<Boolean> one = executor.submit(approve), two = executor.submit(approve); start.countDown();
            assertNotEquals(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
            assertEquals("approved", status());
        } finally { executor.shutdownNow(); }
    }

    @Test void applicationOwnershipAndDeveloperVerificationAreEnforced() {
        User other = new User(); other.setId(owner.getId() + 100000); other.setVerificationType("personal");
        assertEquals(404, assertThrows(Oauth2Exception.class, () -> service.apply(other, appId, "其他账号申请")).getStatus().value());
        owner.setVerificationType("none");
        assertEquals(403, assertThrows(Oauth2Exception.class, this::apply).getStatus().value());
        owner.setVerificationType("personal");
        apply();
        assertEquals(404, assertThrows(Oauth2Exception.class, () -> service.disable(other, appId)).getStatus().value());
    }

    @Test void deletingApplicationCascadesItsRequestsAndDoesNotBreakTheReviewList() {
        Long id = apply();
        apps.delete(apps.findByAppId(appId).orElseThrow()); apps.flush();
        assertFalse(requests.existsById(id));
        assertDoesNotThrow(() -> service.list(0, 100, "all"));
    }

    @Test void missingReasonsAndStaleReviewsCannotChangePermission() {
        assertThrows(Oauth2Exception.class, () -> service.apply(owner, appId, " "));
        assertThrows(Oauth2Exception.class, () -> service.apply(owner, appId, "x".repeat(1001)));
        Long old = apply(); review(old, "reject");
        Long current = apply();
        assertThrows(Oauth2Exception.class, () -> review(old, "approve"));
        assertThrows(Oauth2Exception.class, () -> service.review(admin, current, new UnionidApplicationService.Review("approve", ""), "127.0.0.1"));
        assertEquals("pending", status());
        review(current, "approve"); assertEquals("approved", status());
    }
}
