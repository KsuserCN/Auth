package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauthAccount;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/** Real JPA transactions without an outer test transaction: commits, locks and rollback are observable. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:oauth-binding-transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class OauthBindingCompletionTransactionTest {
    @Autowired OauthBindingCompletionService completion;
    @Autowired UserRepository users;
    @MockitoSpyBean UserOauthAccountRepository accounts;
    @MockitoBean OauthPendingBindingService pending;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    private final Set<String> consumed = ConcurrentHashMap.newKeySet();
    private User user;

    @BeforeEach void setup() {
        cleanup(); consumed.clear(); reset(pending);
        doAnswer(invocation -> {
            OauthPendingBindingService.Claim claim = invocation.getArgument(0);
            if (!consumed.add(claim.token())) throw new OauthPendingBindingService.BindingFailure(
                HttpStatus.BAD_REQUEST, cn.ksuser.api.result.ApiResultCode.OAUTH_087);
            return null;
        }).when(pending).consumeClaim(any());
        user = users.saveAndFlush(new User(UUID.randomUUID().toString(), "oauth-user", "oauth@example.invalid", "hash"));
    }
    @AfterEach void cleanup() {
        if (accounts == null) return;
        accounts.deleteAllInBatch(); users.deleteAllInBatch();
    }
    private OauthPendingBindingService.Claim claim(Long target, String identity) {
        return new OauthPendingBindingService.Claim(UUID.randomUUID().toString(), "owner", target,
            new OauthPendingBindingService.Identity("github", identity, null));
    }

    @Test void onlyFinalCompletionWritesABindingAndAConsumedClaimCannotReplay() {
        var claim = claim(user.getId(), "provider-user");
        assertEquals(0, accounts.count());
        User result = completion.complete(claim);
        assertEquals(user.getId(), result.getId());
        UserOauthAccount linked = accounts.findByProviderAndUserId("github", user.getId()).orElseThrow();
        assertEquals("provider-user", linked.getProviderUserId()); assertTrue(linked.getIsEnabled());
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> completion.complete(claim));
        assertEquals(1, accounts.count());
    }

    @Test void simultaneousCompletionOfOneClaimCanInsertOnlyOnce() throws Exception {
        var claim = claim(user.getId(), "one-provider-user");
        assertEquals(1, concurrently(12, () -> completion.complete(claim)));
        assertEquals(1, accounts.count());
    }

    @Test void differentClaimsForOneLocalAccountSerializeTheProviderConflict() throws Exception {
        var first = claim(user.getId(), "first-provider-user");
        var second = claim(user.getId(), "second-provider-user");
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> attempt(first), () -> attempt(second));
            long success = 0;
            for (Future<Boolean> result : executor.invokeAll(tasks)) if (result.get()) success++;
            assertEquals(1, success);
        }
        assertEquals(1, accounts.count());
    }

    @Test void databaseIdentityUniquenessArbitratesDifferentTargetAccounts() throws Exception {
        User other = users.saveAndFlush(new User(UUID.randomUUID().toString(), "other-user", "other@example.invalid", "hash"));
        var first = claim(user.getId(), "same-provider-user");
        var second = claim(other.getId(), "same-provider-user");
        try (var executor = Executors.newFixedThreadPool(2)) {
            long success = 0;
            for (Future<Boolean> result : executor.invokeAll(List.<Callable<Boolean>>of(() -> attempt(first), () -> attempt(second))))
                if (result.get()) success++;
            assertEquals(1, success);
        }
        assertEquals(1, accounts.count());
    }

    @Test void aStorageFailureRollsBackTheBindingButDoesNotRestoreTheClaim() {
        var claim = claim(user.getId(), "provider-user");
        // Fail after INSERT was flushed, proving that the surrounding transaction rolls the insert back.
        doAnswer(invocation -> {
            entityManager.persist(invocation.getArgument(0));
            entityManager.flush();
            throw new DataIntegrityViolationException("fixture storage failure");
        }).when(accounts).saveAndFlush(any());
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> completion.complete(claim));
        assertEquals(0, accounts.count());
        assertTrue(consumed.contains(claim.token()));
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> completion.complete(claim));
    }

    @Test void expiredOrUnownedClaimCannotReachDatabaseChanges() {
        var claim = claim(user.getId(), "provider-user");
        doThrow(new OauthPendingBindingService.BindingFailure(HttpStatus.BAD_REQUEST,
            cn.ksuser.api.result.ApiResultCode.OAUTH_087)).when(pending).consumeClaim(claim);
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> completion.complete(claim));
        assertEquals(0, accounts.count());
    }

    @Test void deletionOfTheClaimedTargetCannotRedirectTheBindingToAnotherAccount() {
        var claim = claim(user.getId(), "provider-user");
        users.delete(user);
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> completion.complete(claim));
        assertEquals(0, accounts.count()); assertTrue(consumed.contains(claim.token()));
    }

    private boolean attempt(OauthPendingBindingService.Claim claim) {
        try { completion.complete(claim); return true; }
        catch (OauthPendingBindingService.BindingFailure ex) { return false; }
    }
    private long concurrently(int count, Callable<User> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) tasks.add(() -> {
                try { action.call(); return true; }
                catch (OauthPendingBindingService.BindingFailure ex) { return false; }
            });
            long successes = 0;
            for (Future<Boolean> result : executor.invokeAll(tasks)) if (result.get()) successes++;
            return successes;
        }
    }
}
