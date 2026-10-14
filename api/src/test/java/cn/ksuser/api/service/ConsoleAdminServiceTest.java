package cn.ksuser.api.service;

import cn.ksuser.api.entity.ConsoleAuditLog;
import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.ConsoleAuditLogRepository;
import cn.ksuser.api.repository.ConsoleUserRepository;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsoleAdminServiceTest {
    private final ConsoleUserRepository users=mock(ConsoleUserRepository.class);
    private final OfficialApplicationRepository apps=mock(OfficialApplicationRepository.class);
    private final ConsoleAuditLogRepository audits=mock(ConsoleAuditLogRepository.class);
    private final ConsoleSessionService sessions=mock(ConsoleSessionService.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    private final ConsoleSessionService.ConsolePrincipal actor=new ConsoleSessionService.ConsolePrincipal("session","super-uuid",7L,Long.MAX_VALUE,Long.MAX_VALUE);
    private ConsoleAdminService service;
    @BeforeEach
    void setup() {
        ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        service=new ConsoleAdminService(users,apps,audits,sessions,passwords,json,
            mock(cn.ksuser.api.repository.Oauth2ApplicationRepository.class));
        when(sessions.callbackUri()).thenReturn("https://console.ksuser.cn/auth/callback");
    }

    @Test
    void roleUpdateAllowsOnlyOrdinaryRolesAndRecordsNoCredentials() {
        User target=new User("user-uuid","name","email@example.com","must-not-return-password");
        when(users.findByUuidForUpdate("user-uuid")).thenReturn(Optional.of(target));
        ConsoleAdminService.UserView result=service.changeRole(actor,"user-uuid","ADMIN","127.0.0.1");
        assertEquals("ADMIN",result.systemRole());
        verify(sessions).requireFresh(actor);
        verify(users).save(target);
        ArgumentCaptor<ConsoleAuditLog> capture=ArgumentCaptor.forClass(ConsoleAuditLog.class);
        verify(audits).save(capture.capture());
        assertTrue(capture.getValue().getDetails().contains("previousRole"));
        assertFalse(capture.getValue().getDetails().contains("must-not-return-password"));
        assertThrows(Oauth2Exception.class,()->service.changeRole(actor,"user-uuid","SUPER_ADMIN","127.0.0.1"));
        target.setSystemRole("SUPER_ADMIN");
        assertThrows(Oauth2Exception.class,()->service.changeRole(actor,"user-uuid","USER","127.0.0.1"));
    }

    @Test
    void reservedConsoleCannotBeDeletedDisabledOrLoseItsCallbackOrScopes() {
        OfficialApplication app=console();
        when(apps.findByClientIdForUpdate("ksuser_console")).thenReturn(Optional.of(app));
        assertThrows(Oauth2Exception.class,()->service.deleteApplication(actor,"ksuser_console","127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.updateApplication(actor,"ksuser_console",request(false,List.of("https://console.ksuser.cn/auth/callback"),List.of("openid","profile","email")),"127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.updateApplication(actor,"ksuser_console",request(true,List.of("https://different.example/callback"),List.of("openid","profile","email")),"127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.updateApplication(actor,"ksuser_console",request(true,List.of("https://console.ksuser.cn/auth/callback"),List.of("openid")),"127.0.0.1"));
        verify(apps,never()).delete(any());
        verify(apps,never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test
    void clientIdentityAndTypeAreImmutableAndPublicClientsHaveNoRotatingSecret() {
        OfficialApplication app=console();
        when(apps.findByClientIdForUpdate("ksuser_console")).thenReturn(Optional.of(app));
        ConsoleAdminService.ApplicationRequest idChange=new ConsoleAdminService.ApplicationRequest("changed-client","Console",null,"public",List.of("https://console.ksuser.cn/auth/callback"),List.of("openid","profile","email"),true,false);
        assertThrows(Oauth2Exception.class,()->service.updateApplication(actor,"ksuser_console",idChange,"127.0.0.1"));
        ConsoleAdminService.ApplicationRequest typeChange=new ConsoleAdminService.ApplicationRequest("ksuser_console","Console",null,"confidential",List.of("https://console.ksuser.cn/auth/callback"),List.of("openid","profile","email"),true,false);
        assertThrows(Oauth2Exception.class,()->service.updateApplication(actor,"ksuser_console",typeChange,"127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.rotateSecret(actor,"ksuser_console","127.0.0.1"));
    }

    @Test
    void confidentialSecretIsHashedAndNeverAppearsInAuditOrSubsequentApplicationViews() throws Exception {
        when(passwords.encode(anyString())).thenReturn("stored-hash");
        ConsoleAdminService.ApplicationRequest request=new ConsoleAdminService.ApplicationRequest("official_test","Test",null,"confidential",List.of("https://test.example/callback"),List.of("openid","email"),true,false);
        ConsoleAdminService.ApplicationSecret result=service.createApplication(actor,request,"127.0.0.1");
        assertNotNull(result.clientSecret());
        ArgumentCaptor<OfficialApplication> saved=ArgumentCaptor.forClass(OfficialApplication.class);
        verify(apps).saveAndFlush(saved.capture());
        assertEquals("stored-hash",saved.getValue().getClientSecretHash());
        verify(passwords).encode(result.clientSecret());
        ArgumentCaptor<ConsoleAuditLog> audit=ArgumentCaptor.forClass(ConsoleAuditLog.class);
        verify(audits).save(audit.capture());
        assertFalse(audit.getValue().getDetails().contains(result.clientSecret()));
        assertFalse(audit.getValue().getDetails().contains("stored-hash"));
        assertFalse(new ObjectMapper().writeValueAsString(result.application()).contains("clientSecret"));
    }

    @Test
    void missingNonceCanBeEnabledOnlyForNonReservedConfidentialApplications() {
        var confidential = new ConsoleAdminService.ApplicationRequest("official_ezbk", "EzBookKeep", null,
            "confidential", List.of("https://books.example.test/oauth2/callback"),
            List.of("openid", "profile", "email"), true, true);
        service.createApplication(actor, confidential, "127.0.0.1");
        ArgumentCaptor<OfficialApplication> saved = ArgumentCaptor.forClass(OfficialApplication.class);
        verify(apps).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().getAllowMissingNonce());
        var publicRequest = new ConsoleAdminService.ApplicationRequest("official_public", "Public", null,
            "public", confidential.redirectUris(), confidential.scopes(), true, true);
        assertThrows(Oauth2Exception.class, () -> service.createApplication(actor, publicRequest, "127.0.0.1"));
        OfficialApplication reserved = console();
        when(apps.findByClientIdForUpdate("ksuser_console")).thenReturn(Optional.of(reserved));
        var reservedRequest = new ConsoleAdminService.ApplicationRequest("ksuser_console", "Console", null,
            "public", List.of("https://console.ksuser.cn/auth/callback"),
            List.of("openid", "profile", "email"), true, true);
        assertThrows(Oauth2Exception.class, () -> service.updateApplication(actor, "ksuser_console", reservedRequest, "127.0.0.1"));
    }

    @Test
    void freshnessFailurePreventsAnyDatabaseMutation() {
        doThrow(new Oauth2Exception(org.springframework.http.HttpStatus.FORBIDDEN,"access_denied",cn.ksuser.api.result.ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED))
            .when(sessions).requireFresh(actor);
        assertThrows(Oauth2Exception.class,()->service.changeRole(actor,"user-uuid","ADMIN","127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.changeVerification(actor,"user-uuid",
            new ConsoleAdminService.VerificationRequest("personal", "已核验的姓名"), "127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.createApplication(actor,request(true,List.of("https://test.example/callback"),List.of("openid")),"127.0.0.1"));
        verifyNoInteractions(users,apps,audits);
    }

    @Test
    void unsafeCallbacksAndUnknownScopesAreRejected() {
        assertThrows(Oauth2Exception.class,()->service.createApplication(actor,request(true,List.of("https://test.example/callback#fragment"),List.of("openid")),"127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.createApplication(actor,request(true,List.of("http://test.example/callback"),List.of("openid")),"127.0.0.1"));
        assertThrows(Oauth2Exception.class,()->service.createApplication(actor,request(true,List.of("https://test.example/callback"),List.of("openid","credentials")),"127.0.0.1"));
        verifyNoInteractions(apps,audits);
    }

    private static ConsoleAdminService.ApplicationRequest request(boolean active,List<String> redirects,List<String> scopes) {
        return new ConsoleAdminService.ApplicationRequest(null,"Console",null,"public",redirects,scopes,active,false);
    }
    private static OfficialApplication console() {
        OfficialApplication app=new OfficialApplication();
        app.setClientId("ksuser_console");app.setClientName("Console");app.setClientType("public");app.setReserved(true);app.setIsActive(true);
        app.setRedirectUris("https://console.ksuser.cn/auth/callback");app.setScopes("openid profile email");
        return app;
    }
}
