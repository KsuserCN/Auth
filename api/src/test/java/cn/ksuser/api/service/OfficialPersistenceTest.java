package cn.ksuser.api.service;

import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOfficialAuthorization;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import cn.ksuser.api.repository.UserOfficialAuthorizationRepository;
import cn.ksuser.api.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the official entities and grant identity against the isolated H2 test database. */
@SpringBootTest
@Transactional
class OfficialPersistenceTest {
    @Autowired OfficialApplicationRepository apps;
    @Autowired UserOfficialAuthorizationRepository grants;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    private OfficialApplication application() {
        OfficialApplication app = new OfficialApplication(); app.setClientId("official_" + UUID.randomUUID());
        app.setClientName("Persistence Test"); app.setClientType("public");
        app.setRedirectUris("https://example.test/callback"); app.setScopes("openid profile");
        return apps.saveAndFlush(app);
    }
    private User user() {
        String id = UUID.randomUUID().toString();
        User user = new User(id, "official_" + id, id + "@example.invalid", null);
        user.setSystemRole("SUPER_ADMIN");
        return users.saveAndFlush(user);
    }
    private UserOfficialAuthorization grant(User user, OfficialApplication app) {
        UserOfficialAuthorization grant = new UserOfficialAuthorization();
        grant.setUserId(user.getId()); grant.setClientId(app.getClientId()); grant.setScopes("openid profile");
        grant.setRedirectUri("https://example.test/callback");
        return grants.saveAndFlush(grant);
    }

    @Test
    void officialRegistryAndGrantRoundTripWithIndependentRole() {
        OfficialApplication app = application(); User user = user();
        UserOfficialAuthorization grant = grant(user, app);
        entityManager.clear();
        OfficialApplication loaded = apps.findByClientIdForUpdate(app.getClientId()).orElseThrow();
        assertEquals("public", loaded.getClientType()); assertTrue(loaded.getIsActive()); assertFalse(loaded.getReserved());
        assertNotNull(loaded.getCreatedAt()); assertNotNull(loaded.getUpdatedAt());
        User persistedUser = users.findByUuid(user.getUuid()).orElseThrow();
        assertEquals("SUPER_ADMIN", persistedUser.getSystemRole()); assertEquals("none", persistedUser.getDeveloperVerificationType());
        UserOfficialAuthorization loadedGrant = grants.findByUserIdAndClientId(user.getId(), app.getClientId()).orElseThrow();
        assertEquals(grant.getId(), loadedGrant.getId()); assertNotNull(loadedGrant.getAuthorizedAt()); assertNotNull(loadedGrant.getLastAuthorizedAt());
    }

    @Test
    void deletionAndReapprovalProduceNewGrantId() {
        OfficialApplication app = application(); User user = user(); Long first = grant(user, app).getId();
        grants.deleteByUserIdAndClientId(user.getId(), app.getClientId()); grants.flush(); entityManager.clear();
        assertTrue(grants.findByUserIdAndClientId(user.getId(), app.getClientId()).isEmpty());
        Long renewed = grant(user, app).getId();
        assertNotEquals(first, renewed);
    }

    @Test
    void duplicateOfficialGrantIsRejectedByDatabase() {
        OfficialApplication app = application(); User user = user(); grant(user, app);
        assertThrows(DataIntegrityViolationException.class, () -> grant(user, app));
    }
}
