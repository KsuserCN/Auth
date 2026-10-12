package cn.ksuser.api.service;

import cn.ksuser.api.entity.ConsoleAuditLog;
import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.repository.ConsoleAuditLogRepository;
import cn.ksuser.api.repository.ConsoleUserRepository;
import cn.ksuser.api.repository.OfficialApplicationRepository;
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
            List.of("https://created.example.test/callback"),List.of("openid"),true);

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
            "public",List.of("https://example.test/callback"),List.of("openid","email"),false);
    }
    private ConsoleAuditLog onlyAudit() {
        List<ConsoleAuditLog> records=audits.findAll();
        assertEquals(1,records.size());
        return records.getFirst();
    }
}
