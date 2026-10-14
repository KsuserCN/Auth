package cn.ksuser.api.service;

import cn.ksuser.api.entity.ConsoleAuditLog;
import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.ConsoleAuditLogRepository;
import cn.ksuser.api.repository.ConsoleUserRepository;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Uses real JPA transactions and an isolated H2 database. No outer test transaction obscures commits
 * or rollbacks. Only the Console freshness check is mocked; audit failures come from SQL constraints.
 */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:console-admin-transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConsoleAdminTransactionTest {
    @Autowired private ConsoleAdminService service;
    @Autowired private ConsoleUserRepository users;
    @Autowired private OfficialApplicationRepository applications;
    @Autowired private ConsoleAuditLogRepository audits;
    @Autowired private Oauth2ApplicationRepository oauthApplications;
    @MockitoBean private ConsoleSessionService sessions;
    private User actorUser;
    private User target;
    private OfficialApplication app;
    private ConsoleSessionService.ConsolePrincipal actor;

    @BeforeEach
    void setup() {
        cleanup();
        actorUser=user("actor", "SUPER_ADMIN");
        target=user("target", "USER");
        actor=principal(actorUser.getUuid());
        app=new OfficialApplication();
        app.setClientId("official_tx_"+UUID.randomUUID());
        app.setClientName("Original application");
        app.setClientType("public");
        app.setRedirectUris("https://example.test/callback");
        app.setScopes("openid profile");
        app.setIsActive(true);
        app.setReserved(false);
        app=applications.saveAndFlush(app);
        when(sessions.callbackUri()).thenReturn("https://console.ksuser.cn/auth/callback");
    }

    @AfterEach
    void cleanup() {
        audits.deleteAllInBatch();
        oauthApplications.deleteAllInBatch();
        applications.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void roleChangeAndAuditCommitTogether() {
        service.changeRole(actor,target.getUuid(),"ADMIN","127.0.0.1");

        assertEquals("ADMIN",users.findByUuid(target.getUuid()).orElseThrow().getSystemRole());
        ConsoleAuditLog audit=onlyAudit();
        assertEquals(actorUser.getUuid(),audit.getActorUuid());
        assertEquals(target.getUuid(),audit.getTarget());
        assertEquals("user.role.update",audit.getAction());
        assertTrue(audit.getDetails().contains("\"previousRole\":\"USER\""));
        assertTrue(audit.getDetails().contains("\"systemRole\":\"ADMIN\""));
        verify(sessions).requireFresh(actor);
    }

    @Test
    void certificationAndAuditCommitTogetherWithoutChangingRoleOrEditableName() {
        target.setRealName("自行填写的姓名");
        users.saveAndFlush(target);
        var updated = service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("personal", "  已核验的姓名  "), "127.0.0.1");

        assertEquals("personal", updated.developerVerificationType());
        assertEquals("已核验的姓名", updated.verificationEntityName());
        User saved = users.findByUuid(target.getUuid()).orElseThrow();
        assertEquals("已核验的姓名", saved.getVerificationEntityName());
        assertEquals("自行填写的姓名", saved.getRealName());
        assertEquals("USER", saved.getSystemRole());
        assertEquals("user.verification.update", onlyAudit().getAction());

        service.changeRole(actor, target.getUuid(), "ADMIN", "127.0.0.1");
        saved = users.findByUuid(target.getUuid()).orElseThrow();
        assertEquals("personal", saved.getDeveloperVerificationType());
        assertEquals("已核验的姓名", saved.getVerificationEntityName());
    }

    @Test
    void staleProfileSaveCannotRevertANewConsoleCertification() {
        // target was loaded before Console updated certification in a separate transaction.
        assertEquals("none", target.getDeveloperVerificationType());
        service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("enterprise", "已核验企业"), "127.0.0.1");
        target.setBio("更新的个人简介");
        users.saveAndFlush(target);

        User saved = users.findByUuid(target.getUuid()).orElseThrow();
        assertEquals("更新的个人简介", saved.getBio());
        assertEquals("enterprise", saved.getDeveloperVerificationType());
        assertEquals("已核验企业", saved.getVerificationEntityName());
    }

    @Test
    void auditFailureRollsBackCertificationIncludingTheReadOnlyEntityColumn() {
        service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("personal", "已核验的姓名"), "127.0.0.1");
        audits.deleteAllInBatch();
        var invalidActor = principal("actor-too-long-for-the-database-column".repeat(3));

        assertThrows(DataIntegrityViolationException.class, () -> service.changeVerification(invalidActor,
            target.getUuid(), new ConsoleAdminService.VerificationRequest("enterprise", "企业名称"), "127.0.0.1"));
        User saved = users.findByUuid(target.getUuid()).orElseThrow();
        assertEquals("personal", saved.getDeveloperVerificationType());
        assertEquals("已核验的姓名", saved.getVerificationEntityName());
        assertEquals(0, audits.count());
    }

    @Test
    void revokingCertificationClearsTheOldNameAndInvalidValuesCannotWrite() {
        for (var request : List.of(
            new ConsoleAdminService.VerificationRequest("admin", "姓名"),
            new ConsoleAdminService.VerificationRequest("personal", "  "),
            new ConsoleAdminService.VerificationRequest("enterprise", "x".repeat(201)))) {
            assertThrows(Oauth2Exception.class, () -> service.changeVerification(actor, target.getUuid(), request, "127.0.0.1"));
        }
        assertEquals(0, audits.count());
        service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("enterprise", "企业名称"), "127.0.0.1");
        var revoked = service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("none", "旧企业名称"), "127.0.0.1");
        assertEquals("none", revoked.developerVerificationType());
        assertNull(revoked.verificationEntityName());
        target = users.findByUuid(target.getUuid()).orElseThrow();
        target.setVerificationType("personal");
        users.saveAndFlush(target);
        assertNull(users.findByUuid(target.getUuid()).orElseThrow().getVerificationEntityName());
    }

    @Test
    void administratorNameCanBeRegisteredWithoutGrantingSuperAdminOrDeveloperCertification() {
        service.changeRole(actor, target.getUuid(), "ADMIN", "127.0.0.1");
        assertThrows(Oauth2Exception.class, () -> service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("none", null), "127.0.0.1"));
        var updated = service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("none", "管理员真实姓名"), "127.0.0.1");
        assertEquals("ADMIN", updated.systemRole());
        assertEquals("none", updated.developerVerificationType());
        assertEquals("管理员真实姓名", updated.verificationEntityName());
    }

    @Test
    void verifiedUserFiltersIncludeAdministratorsAndRespectSearchAndPagination() {
        service.changeVerification(actor, target.getUuid(),
            new ConsoleAdminService.VerificationRequest("personal", "个人姓名"), "127.0.0.1");
        User enterprise = user("enterprise", "USER");
        service.changeVerification(actor, enterprise.getUuid(),
            new ConsoleAdminService.VerificationRequest("enterprise", "企业名称"), "127.0.0.1");
        User administrator = user("admin", "ADMIN");
        user("unverified", "USER");

        assertEquals(4, service.listUsers(0, 20, "", "", "createdAt", "desc", "verified").total());
        var secondPage = service.listUsers(1, 2, "", "", "uuid", "asc", "verified");
        assertEquals(4, secondPage.total());
        assertEquals(2, secondPage.items().size());
        assertEquals(List.of(target.getUuid()), service.listUsers(0, 20, target.getUuid(), "", "createdAt", "desc", "personal")
            .items().stream().map(ConsoleAdminService.UserView::uuid).toList());
        assertEquals(List.of(enterprise.getUuid()), service.listUsers(0, 20, "", "", "createdAt", "desc", "enterprise")
            .items().stream().map(ConsoleAdminService.UserView::uuid).toList());
        assertEquals(2, service.listUsers(0, 20, "", "", "createdAt", "desc", "admin").total());
        assertEquals(List.of(administrator.getUuid()), service.listUsers(0, 20, "", "ADMIN", "createdAt", "desc", "verified")
            .items().stream().map(ConsoleAdminService.UserView::uuid).toList());
        assertThrows(Oauth2Exception.class, () -> service.listUsers(0, 20, "", "", "createdAt", "desc", "unsupported"));
    }

    @Test
    void applicationsUseActualOwnerAndResponsesExcludeSecretsAndOtherAccounts() throws Exception {
        oauthApp(target, "owned-oauth", "private-oauth-secret");
        oauthApp(actorUser, "other-oauth", "other-private-secret");
        var result = service.userApplications(target.getUuid());
        assertEquals(target.getUuid(), result.user().uuid());
        assertEquals(java.util.Set.of("owned-oauth"), result.applications().stream()
            .map(ConsoleAdminService.OwnedApplicationView::clientId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(result.applications().stream().allMatch(item -> item.redirectUris().size() == 2));
        String response = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        for (String secret : List.of("private-oauth-secret", "other-private-secret", "other-oauth"))
            assertFalse(response.contains(secret));
        assertTrue(service.userApplications(user("empty", "USER").getUuid()).applications().isEmpty());
        assertThrows(Oauth2Exception.class, () -> service.userApplications(UUID.randomUUID().toString()));
    }

    private void oauthApp(User owner, String id, String secret) {
        Oauth2Application application = new Oauth2Application();
        application.setOwnerUserId(owner.getId());
        application.setAppId(id);
        application.setAppName("OAuth 应用");
        application.setAppSecretHash(secret);
        application.setRedirectUri("https://example.test/callback;https://example.test/another");
        application.setContactInfo("contact@example.test");
        oauthApplications.saveAndFlush(application);
    }

    @Test
    void auditInsertFailureRollsBackRoleChange() {
        ConsoleSessionService.ConsolePrincipal invalidAuditActor=principal("actor-too-long-for-the-database-column".repeat(3));

        assertThrows(DataIntegrityViolationException.class,
            ()->service.changeRole(invalidAuditActor,target.getUuid(),"ADMIN","127.0.0.1"));

        assertEquals("USER",users.findByUuid(target.getUuid()).orElseThrow().getSystemRole());
        assertEquals(0,audits.count());
        verify(sessions).requireFresh(invalidAuditActor);
    }

    @Test
    void applicationConfigurationAndAuditCommitTogether() {
        service.updateApplication(actor,app.getClientId(),modifiedApplication(),"127.0.0.1");

        OfficialApplication saved=applications.findByClientId(app.getClientId()).orElseThrow();
        assertEquals("Updated application",saved.getClientName());
        assertEquals("openid email",saved.getScopes());
        assertFalse(saved.getIsActive());
        ConsoleAuditLog audit=onlyAudit();
        assertEquals(app.getClientId(),audit.getTarget());
        assertEquals("official.application.update",audit.getAction());
        assertTrue(audit.getDetails().contains("Original application"));
        assertTrue(audit.getDetails().contains("Updated application"));
    }

    @Test
    void auditInsertFailureRollsBackAlreadyFlushedApplicationConfiguration() {
        ConsoleSessionService.ConsolePrincipal invalidAuditActor=principal("actor-too-long-for-the-database-column".repeat(3));

        assertThrows(DataIntegrityViolationException.class,
            ()->service.updateApplication(invalidAuditActor,app.getClientId(),modifiedApplication(),"127.0.0.1"));

        OfficialApplication saved=applications.findByClientId(app.getClientId()).orElseThrow();
        assertEquals("Original application",saved.getClientName());
        assertEquals("openid profile",saved.getScopes());
        assertTrue(saved.getIsActive());
        assertNull(saved.getLogoUrl());
        assertEquals(0,audits.count());
    }

    @Test
    void auditInsertFailureRollsBackNewApplicationAndDoesNotLeaveAnUnauditedRegistration() {
        ConsoleSessionService.ConsolePrincipal invalidAuditActor=principal("actor-too-long-for-the-database-column".repeat(3));
        String newClientId="official_created_"+UUID.randomUUID();
        var request=new ConsoleAdminService.ApplicationRequest(newClientId,"New application",null,"public",
            List.of("https://created.example.test/callback"),List.of("openid"),true,false);

        assertThrows(DataIntegrityViolationException.class,
            ()->service.createApplication(invalidAuditActor,request,"127.0.0.1"));

        assertFalse(applications.existsByClientId(newClientId));
        assertEquals(1,applications.count());
        assertEquals(0,audits.count());
    }

    @Test
    void auditInsertFailureRollsBackApplicationDeletion() {
        ConsoleSessionService.ConsolePrincipal invalidAuditActor=principal("actor-too-long-for-the-database-column".repeat(3));

        assertThrows(DataIntegrityViolationException.class,
            ()->service.deleteApplication(invalidAuditActor,app.getClientId(),"127.0.0.1"));

        assertTrue(applications.existsByClientId(app.getClientId()));
        assertEquals(0,audits.count());
    }

    private User user(String prefix,String role) {
        String uuid=UUID.randomUUID().toString();
        User user=new User(uuid,prefix+"_"+uuid,prefix+"_"+uuid+"@example.test",null);
        user.setSystemRole(role);
        return users.saveAndFlush(user);
    }
    private static ConsoleSessionService.ConsolePrincipal principal(String uuid) {
        return new ConsoleSessionService.ConsolePrincipal("isolated-session",uuid,7L,Long.MAX_VALUE,Long.MAX_VALUE);
    }
    private ConsoleAdminService.ApplicationRequest modifiedApplication() {
        return new ConsoleAdminService.ApplicationRequest(app.getClientId(),"Updated application","https://example.test/logo.png",
            "public",List.of("https://example.test/callback"),List.of("openid","email"),false,false);
    }
    private ConsoleAuditLog onlyAudit() {
        List<ConsoleAuditLog> records=audits.findAll();
        assertEquals(1,records.size());
        return records.getFirst();
    }
}
