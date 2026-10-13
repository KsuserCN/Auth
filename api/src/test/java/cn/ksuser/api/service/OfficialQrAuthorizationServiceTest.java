package cn.ksuser.api.service;

import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OfficialQrAuthorizationServiceTest {
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String,String> values=mock(ValueOperations.class);
    private final OfficialAuthorizationService official=mock(OfficialAuthorizationService.class);
    private final QrChallengeService qr=mock(QrChallengeService.class);
    private final Map<String,String> memory=new HashMap<>();
    private final ObjectMapper json=new ObjectMapper();
    private OfficialQrAuthorizationService service;
    private QrChallengeService.QrChallengePayload challenge;
    private final User user=new User();
    private final String poll="a".repeat(32);
    private static final String CALLBACK="https://official.example/callback?existing=1";
    private OfficialAuthorizeRequest request() { return new OfficialAuthorizeRequest("public-app",CALLBACK,"code","openid email real_name",
        "original state+中文","original-nonce","b".repeat(43),"S256",true); }
    private OfficialQrAuthorizationService.BrowserRequest browser() { return new OfficialQrAuthorizationService.BrowserRequest("first",poll); }

    @BeforeEach void setup() {
        user.setId(7L);user.setUuid("ordinary-user");user.setSystemRole("USER");
        service=new OfficialQrAuthorizationService(official,qr,redis,json);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call->memory.get(call.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(call->memory.remove(call.getArgument(0)));
        doAnswer(call->{memory.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
        when(redis.delete(anyCollection())).thenAnswer(call->{List<String> keys=call.getArgument(0);keys.forEach(memory::remove);return (long)keys.size();});
        challenge=new QrChallengeService.QrChallengePayload();challenge.setChallengeId("first");challenge.setPollToken(poll);
        challenge.setApproveCode("phone-code");challenge.setType("official");challenge.setStatus("pending");
        when(qr.createChallenge(eq(QrChallengeService.ChallengeType.OFFICIAL_AUTHORIZATION),isNull(),isNull(),eq("official"),anyString(),anyString())).thenReturn(challenge);
        when(qr.getByChallengeId("first")).thenReturn(challenge);
        when(official.buildAuthorizeContext(any(),any())).thenReturn(context());
        when(official.approveAuthorization(eq(user),any())).thenReturn(Map.of("redirectUrl",CALLBACK+"&code=private-code&state=original-state"));
        doAnswer(call->{challenge.setStatus("approved");challenge.setResult(call.getArgument(2));return null;}).when(qr).approveChallenge(eq("first"),eq(7L),anyMap());
        doAnswer(call->{challenge.setStatus("rejected");return null;}).when(qr).rejectChallenge(eq("first"),eq(7L),anyMap());
    }
    private OfficialAuthorizationService.AuthorizeContext context() {
        return new OfficialAuthorizationService.AuthorizeContext("public-app","官方公共服务","https://official.example/logo.png",
            List.of("openid","email","real_name"),List.of(),true,false,false);
    }
    private void create() { service.create(request(),"127.0.0.1","browser"); }

    @Test void ordinaryAccountConfirmsOriginalRequestWithoutSharingBrowserReceiptOrPassportTokens() {
        var created=service.create(request(),"127.0.0.1","browser");
        assertEquals("KSUSER-AUTH-QR:phone-code",created.challenge().getQrText());
        assertFalse(created.challenge().getQrText().contains(poll));
        assertEquals(List.of("openid","email","real_name"),service.preview(challenge).requestedScopes());
        assertEquals(CALLBACK,service.redirectUri(challenge));
        assertEquals("pending",service.status(browser()).status());
        assertThrows(Oauth2Exception.class,()->service.consume(browser()));
        service.decide(challenge,user,true);
        assertEquals(Map.of("verified",true),challenge.getResult());
        assertEquals("approved",service.status(browser()).status());
        var captured=ArgumentCaptor.forClass(OfficialAuthorizeRequest.class);
        verify(official).approveAuthorization(eq(user),captured.capture());
        assertEquals(request(),captured.getValue(),"Original state, nonce, callback, scopes and PKCE must be preserved");
        assertThrows(Oauth2Exception.class,()->service.consume(new OfficialQrAuthorizationService.BrowserRequest("first","x".repeat(32))));
        assertTrue(service.consume(browser()).get("redirectUrl").contains("private-code"));
        assertThrows(Oauth2Exception.class,()->service.consume(browser()));
        assertEquals("expired",service.status(browser()).status());
        assertTrue(memory.isEmpty());
    }
    @Test void requestCreationNeverInheritsBrowserApprovalOrIdentity() {
        create();
        var captured=ArgumentCaptor.forClass(OfficialAuthorizeRequest.class);
        verify(official).buildAuthorizeContext(isNull(),captured.capture());
        assertNull(captured.getValue().approved());
        verify(official,never()).approveAuthorization(any(),any());
    }
    @Test void roleOrApplicationChangesAreCheckedBeforeConsumingApproval() {
        create();
        when(official.buildAuthorizeContext(eq(user),any())).thenThrow(new Oauth2Exception(HttpStatus.FORBIDDEN,"access_denied",ApiResultCode.OFFICIAL_ACCESS_DENIED));
        assertThrows(Oauth2Exception.class,()->service.decide(challenge,user,true));
        assertEquals("pending",memory.get("official:qr:first:approval"));
        verify(official,never()).approveAuthorization(any(),any());
    }
    @Test void denialsReturnOnlyTheServerValidatedCallbackAndCanBeConsumedOnce() {
        create();
        when(official.approveAuthorization(eq(user),any())).thenReturn(Map.of("redirectUrl",CALLBACK+"&error=access_denied&state=original-state"));
        service.decide(challenge,user,false);
        assertEquals("rejected",service.status(browser()).status());
        assertTrue(service.consume(browser()).get("redirectUrl").contains("error=access_denied"));
        verify(official).approveAuthorization(eq(user),eq(new OfficialAuthorizeRequest(request().clientId(),CALLBACK,"code",request().scope(),request().state(),request().nonce(),request().codeChallenge(),"S256",false)));
    }
    @Test void cancellationAndExpiryMakeOldScansUnusable() throws Exception {
        create();
        assertThrows(Oauth2Exception.class,()->service.cancel(new OfficialQrAuthorizationService.BrowserRequest("first","x".repeat(32))));
        service.cancel(browser());
        assertThrows(Oauth2Exception.class,()->service.decide(challenge,user,true));
        assertTrue(memory.isEmpty());
        create();
        memory.put("official:qr:first",json.writeValueAsString(new OfficialQrAuthorizationService.Flow(request(),poll,1)));
        assertEquals("expired",service.status(browser()).status());
        assertThrows(Oauth2Exception.class,()->service.decide(challenge,user,true));
        assertThrows(Oauth2Exception.class,()->service.consume(browser()));
    }
    @Test void simultaneousConfirmationsCannotReplaceTheChosenAccount() {
        create();
        service.decide(challenge,user,true);
        challenge.setStatus("pending"); // Simulate a second request which read the challenge before the first finished.
        assertThrows(Oauth2Exception.class,()->service.decide(challenge,user,true));
        verify(official,times(1)).approveAuthorization(any(),any());
    }
}
