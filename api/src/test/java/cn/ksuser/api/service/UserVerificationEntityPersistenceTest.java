package cn.ksuser.api.service;

import cn.ksuser.api.controller.AuthController;
import cn.ksuser.api.dto.UpdateProfileRequest;
import cn.ksuser.api.dto.UpdateProfileResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.OidcClient;
import cn.ksuser.api.repository.OidcClientRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The certified entity is maintained in SQL, independently of editable profile data. */
@SpringBootTest
@Transactional
class UserVerificationEntityPersistenceTest {
    @Autowired UserRepository users;
    @Autowired OidcClientRepository clients;
    @Autowired UserService userService;
    @Autowired AuthController controller;
    @Autowired EntityManager entityManager;

    private User certifiedUser() {
        String uuid = UUID.randomUUID().toString();
        User user = new User(uuid, "entity_" + uuid, uuid + "@example.invalid", null);
        user.setVerificationType("personal");
        ReflectionTestUtils.setField(user, "verificationEntityName", "Not administrator verified");
        users.saveAndFlush(user);
        entityManager.refresh(user);
        assertNull(user.getVerificationEntityName(), "JPA inserts must not populate the database-only column");
        entityManager.createNativeQuery("UPDATE users SET verification_entity_name = :name WHERE id = :id")
            .setParameter("name", "已核验姓名").setParameter("id", user.getId()).executeUpdate();
        entityManager.refresh(user);
        return user;
    }

    @Test
    void applicationEditsCannotOverwriteAManuallyCorrectedSsoOwner() {
        User registeredOwner = certifiedUser();
        User correctedOwner = certifiedUser();
        OidcClient client = new OidcClient();
        client.setClientId("owner_test_" + UUID.randomUUID());
        client.setClientName("Original application name");
        client.setRedirectUris("https://example.invalid/callback");
        client.setOwnerUserId(registeredOwner.getId());
        clients.saveAndFlush(client);

        entityManager.createNativeQuery("UPDATE oidc_clients SET owner_user_id = :owner WHERE id = :id")
            .setParameter("owner", correctedOwner.getId()).setParameter("id", client.getId()).executeUpdate();
        // Save metadata from the instance loaded before the administrator's SQL correction.
        client.setClientName("Updated application name");
        clients.saveAndFlush(client);
        entityManager.clear();

        OidcClient reloaded = clients.findById(client.getId()).orElseThrow();
        assertEquals("Updated application name", reloaded.getClientName());
        assertEquals(correctedOwner.getId(), reloaded.getOwnerUserId());
    }

    @Test
    void profileChangesAndOrdinaryEntitySavesCannotOverwriteSqlCertification() {
        User user = certifiedUser();
        userService.updateProfileSingleField(user, "realName", "自行填写的姓名", null);
        userService.updateProfileSingleField(user, "username", "changed_" + user.getUuid(), null);
        assertEquals("已核验姓名", UpdateProfileResponse.fromUser(user).getVerificationEntityName());

        ReflectionTestUtils.setField(user, "verificationEntityName", "Attempted ORM replacement");
        users.saveAndFlush(user);
        entityManager.clear();
        User reloaded = users.findById(user.getId()).orElseThrow();
        assertEquals("自行填写的姓名", reloaded.getRealName());
        assertEquals("已核验姓名", reloaded.getVerificationEntityName());
    }

    @Test
    void profileApiRejectsCertificationFieldsWithoutChangingTheEntity() {
        User user = certifiedUser();
        var authentication = new UsernamePasswordAuthenticationToken(user.getUuid(), null, List.of());
        for (String key : List.of("verificationEntityName", "verification_entity_name", "verificationType")) {
            var response = controller.updateProfile(new UpdateProfileRequest(key, "Forged certification"), authentication);
            assertEquals(400, response.getStatusCode().value());
            assertEquals(ApiResultCode.AUTH_030, response.getBody().getErrorCode());
        }
        entityManager.refresh(user);
        assertEquals("已核验姓名", user.getVerificationEntityName());
    }

    @Test
    void jsonInputCannotReplaceTheCertifiedNameAndRevocationHidesIt() throws Exception {
        User user = certifiedUser();
        new ObjectMapper().readerForUpdating(user).readValue("{\"verificationEntityName\":\"Forged name\"}");
        assertEquals("已核验姓名", user.getVerificationEntityName());
        user.setVerificationType("none");
        assertNull(user.getVerificationEntityName());
    }
}
