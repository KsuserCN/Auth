package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsoleSessionServiceTest {
    private final Map<String,String> storage=new HashMap<>();
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String,String> values=mock(ValueOperations.class);
    private final UserRepository users=mock(UserRepository.class);
    private final OfficialAuthorizationService official=mock(OfficialAuthorizationService.class);
    private final QrChallengeService qr=mock(QrChallengeService.class);
    private ConsoleSessionService service;
    private User admin;

    @BeforeEach
    void setup() {
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call->{storage.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
        when(values.get(anyString())).thenAnswer(call->storage.get(call.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(call->storage.remove(call.getArgument(0)));
        when(redis.delete(anyCollection())).thenAnswer(call->{List<String> keys=call.getArgument(0);keys.forEach(storage::remove);return (long)keys.size();});
        admin=new User("e92ca6f9-67ea-4e43-b715-0b37affba178","console-admin","admin@example.com","hash");
        admin.setSystemRole("SUPER_ADMIN");
        when(users.findByUuid(admin.getUuid())).thenReturn(Optional.of(admin));
        when(official.isConsoleAuthorizationActive(admin.getUuid(),7L)).thenReturn(true);
        when(official.exchangeForConsole(anyString(),anyString(),anyString(),anyString()))
            .thenReturn(new OfficialAuthorizationService.ConsoleExchange(admin,7L,"ksuser_console"));
        AppProperties properties=new AppProperties(); properties.setDebug(true);
        service=new ConsoleSessionService(redis,new ObjectMapper(),users,official,qr,properties,"","");
    }

    @Test
    void loginRequiresBrowserAndStateAndConsumesFlowOnce() {
        MockHttpServletResponse start=new MockHttpServletResponse();
        String url=service.startLogin(start).get("authorizationUrl");
        String state=query(url,"state");
        assertEquals("S256",query(url,"code_challenge_method"));
        assertNotNull(query(url,"nonce"));
        assertThrows(Oauth2Exception.class,()->service.exchangeLogin("code",state,new MockHttpServletRequest(),new MockHttpServletResponse()));
        MockHttpServletRequest browser=request(cookieValue(start,ConsoleSessionService.LOGIN_COOKIE),ConsoleSessionService.LOGIN_COOKIE);
        assertThrows(Oauth2Exception.class,()->service.exchangeLogin("code","wrong-state",browser,new MockHttpServletResponse()));
        MockHttpServletResponse response=new MockHttpServletResponse();
        ConsoleSessionService.SessionView session=service.exchangeLogin("code",state,browser,response);
        assertEquals(admin.getUuid(),session.user().uuid());
        assertEquals(0,session.freshUntil());
        assertTrue(session.expiresAt()<=System.currentTimeMillis()+Duration.ofHours(8).toMillis());
        verify(official).exchangeForConsole(eq("code"),eq("http://localhost:5174/auth/callback"),anyString(),eq(query(url,"nonce")));
        assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(value->value.contains("consoleSession=") && value.contains("HttpOnly") && value.contains("SameSite=Strict") && value.contains("Path=/console") && !value.contains("Domain=")));
        assertThrows(Oauth2Exception.class,()->service.exchangeLogin("code",state,browser,new MockHttpServletResponse()));
    }

    @Test
    void eachRequestUsesCurrentRoleAndOriginalAuthorization() {
        MockHttpServletRequest request=login();
        assertEquals(admin.getUuid(),service.authenticate(request).userUuid());
        admin.setSystemRole("ADMIN");
        Oauth2Exception revoked=assertThrows(Oauth2Exception.class,()->service.authenticate(request));
        assertEquals(ApiResultCode.CONSOLE_ACCESS_DENIED,revoked.getResult().errorCode());
        admin.setSystemRole("SUPER_ADMIN");
        when(official.isConsoleAuthorizationActive(admin.getUuid(),7L)).thenReturn(false);
        assertThrows(Oauth2Exception.class,()->service.authenticate(request));
        when(official.isConsoleAuthorizationActive(admin.getUuid(),7L)).thenReturn(true);
        assertThrows(Oauth2Exception.class,()->service.authenticate(request),"Reconsenting must not restore the deleted Console session");
    }

    @Test
    void stepUpRequiresNativeProofAndIsBoundToSessionUserStateAndOneUse() {
        ConsoleSessionService.ConsolePrincipal first=service.authenticate(login());
        ConsoleSessionService.ConsolePrincipal second=service.authenticate(login());
        String requestId=query(service.startVerification(first).get("authorizationUrl"),"request_id");
        assertThrows(Oauth2Exception.class,()->service.validateVerificationRequest(requestId,"different-user"));
        storage.put("sensitive:generic-marker:"+admin.getUuid(),"verified");
        assertThrows(Oauth2Exception.class,()->service.completeVerification(requestId,admin.getUuid()));
        service.recordVerificationProof(requestId,admin.getUuid());
        String redirect=service.completeVerification(requestId,admin.getUuid()).get("redirectUrl");
        assertThrows(Oauth2Exception.class,()->service.completeVerification(requestId,admin.getUuid()));
        String ticket=query(redirect,"ticket"),state=query(redirect,"state");
        assertThrows(Oauth2Exception.class,()->service.exchangeVerification(second,ticket,state));
        assertThrows(Oauth2Exception.class,()->service.exchangeVerification(first,ticket,"wrong-state"));
        ConsoleSessionService.SessionView verified=service.exchangeVerification(first,ticket,state);
        assertTrue(verified.freshUntil()>System.currentTimeMillis());
        assertTrue(verified.freshUntil()<=System.currentTimeMillis()+Duration.ofMinutes(15).toMillis());
        service.requireFresh(first);
        assertThrows(Oauth2Exception.class,()->service.requireFresh(second));
        assertThrows(Oauth2Exception.class,()->service.exchangeVerification(first,ticket,state));
    }

    @Test
    void logoutOnlyDeletesTheConsoleSessionAndItsFreshness() {
        MockHttpServletRequest request=login();
        ConsoleSessionService.ConsolePrincipal principal=service.authenticate(request);
        storage.put("passport:session","preserved");
        service.logout(principal,new MockHttpServletResponse());
        assertThrows(Oauth2Exception.class,()->service.authenticate(request));
        assertEquals("preserved",storage.get("passport:session"));
    }

    @Test
    void malformedExpiredFlowsAndExpiredSessionFailClosed() throws Exception {
        MockHttpServletResponse response=new MockHttpServletResponse();
        String url=service.startLogin(response).get("authorizationUrl");
        String loginKey=storage.keySet().stream().filter(key->key.startsWith("console:login:")).findFirst().orElseThrow();
        ObjectMapper json=new ObjectMapper();
        ConsoleSessionService.LoginFlow original=json.readValue(storage.get(loginKey),ConsoleSessionService.LoginFlow.class);
        storage.put(loginKey,json.writeValueAsString(new ConsoleSessionService.LoginFlow(original.state(),original.nonce(),original.verifier(),original.redirectUri(),1)));
        assertThrows(Oauth2Exception.class,()->service.exchangeLogin("code",query(url,"state"),request(cookieValue(response,ConsoleSessionService.LOGIN_COOKIE),ConsoleSessionService.LOGIN_COOKIE),new MockHttpServletResponse()));
        MockHttpServletRequest browser=login();
        String sessionKey=storage.keySet().stream().filter(key->key.startsWith("console:session:")).findFirst().orElseThrow();
        storage.put(sessionKey,json.writeValueAsString(new ConsoleSessionService.SessionData(admin.getUuid(),7L,1)));
        assertThrows(Oauth2Exception.class,()->service.authenticate(browser));
    }

    @Test
    void qrLoginRequiresOriginalBrowserPollTokenApprovalAndOneTimeExchange() {
        var challenge=qrChallenge("first");
        MockHttpServletRequest browser=startQr(challenge);
        assertEquals("pending",service.qrLoginStatus("first","poll-first",browser).status());
        assertThrows(Oauth2Exception.class,()->service.qrLoginStatus("first","poll-first",new MockHttpServletRequest()));
        assertThrows(Oauth2Exception.class,()->service.qrLoginStatus("first","wrong-poll",browser));
        assertThrows(Oauth2Exception.class,()->service.qrLoginStatus("other","poll-first",browser));
        assertThrows(Oauth2Exception.class,()->service.exchangeQrLogin("first","poll-first",browser,new MockHttpServletResponse()));
        when(official.approveConsoleLogin(admin,service.callbackUri()))
            .thenReturn(new OfficialAuthorizationService.ConsoleExchange(admin,7L,"ksuser_console"));
        service.approveQrLogin(challenge,admin);
        assertThrows(Oauth2Exception.class,()->service.approveQrLogin(challenge,admin));
        challenge.setStatus("approved");
        assertEquals("approved",service.qrLoginStatus("first","poll-first",browser).status());
        MockHttpServletResponse finish=new MockHttpServletResponse();
        var session=service.exchangeQrLogin("first","poll-first",browser,finish);
        assertEquals(admin.getUuid(),session.user().uuid());
        assertEquals(0,session.freshUntil(),"Login approval must not grant permission for sensitive writes");
        assertEquals(admin.getUuid(),service.authenticate(request(cookieValue(finish,ConsoleSessionService.SESSION_COOKIE),ConsoleSessionService.SESSION_COOKIE)).userUuid());
        assertThrows(Oauth2Exception.class,()->service.exchangeQrLogin("first","poll-first",browser,new MockHttpServletResponse()));
        verify(official,never()).exchangeForConsole(anyString(),anyString(),anyString(),anyString());
    }

    @Test
    void refreshAndCancelInvalidateOldQrEvenWhenPhoneHasConfirmed() {
        var first=qrChallenge("first");
        MockHttpServletRequest browser=startQr(first);
        when(official.approveConsoleLogin(admin,service.callbackUri()))
            .thenReturn(new OfficialAuthorizationService.ConsoleExchange(admin,7L,"ksuser_console"));
        service.approveQrLogin(first,admin);
        first.setStatus("approved");
        var second=qrChallenge("second");
        when(qr.createChallenge(eq(QrChallengeService.ChallengeType.CONSOLE_LOGIN),isNull(),isNull(),eq("console"),anyString(),anyString()))
            .thenReturn(second);
        MockHttpServletResponse refreshed=new MockHttpServletResponse();
        service.startQrLogin(browser,refreshed,"127.0.0.1","browser");
        assertThrows(Oauth2Exception.class,()->service.exchangeQrLogin("first","poll-first",browser,new MockHttpServletResponse()));
        assertFalse(storage.containsKey("console:qr-grant:first"));
        MockHttpServletRequest next=request(cookieValue(refreshed,ConsoleSessionService.QR_LOGIN_COOKIE),ConsoleSessionService.QR_LOGIN_COOKIE);
        MockHttpServletResponse lateCancel=new MockHttpServletResponse();
        service.cancelQrLogin("first",next,lateCancel);
        assertTrue(lateCancel.getHeaders("Set-Cookie").isEmpty());
        assertEquals("pending",service.qrLoginStatus("second","poll-second",next).status());
        assertThrows(Oauth2Exception.class,()->service.exchangeQrLogin("first","poll-first",next,new MockHttpServletResponse()));
        MockHttpServletResponse cancelled=new MockHttpServletResponse();
        service.cancelQrLogin("second",next,cancelled);
        assertTrue(cancelled.getHeaders("Set-Cookie").isEmpty());
        assertThrows(Oauth2Exception.class,()->service.approveQrLogin(second,admin));
        assertTrue(storage.keySet().stream().noneMatch(key->key.startsWith("console:session:")));
    }

    @Test
    void qrApprovalAndExchangeCheckCurrentRoleGrantAndExpiry() throws Exception {
        var challenge=qrChallenge("first");
        MockHttpServletRequest browser=startQr(challenge);
        admin.setSystemRole("ADMIN");
        assertThrows(Oauth2Exception.class,()->service.approveQrLogin(challenge,admin));
        verify(official,never()).approveConsoleLogin(any(),anyString());
        admin.setSystemRole("SUPER_ADMIN");
        when(official.approveConsoleLogin(admin,service.callbackUri()))
            .thenReturn(new OfficialAuthorizationService.ConsoleExchange(admin,7L,"ksuser_console"));
        service.approveQrLogin(challenge,admin);
        challenge.setStatus("approved");
        when(official.isConsoleAuthorizationActive(admin.getUuid(),7L)).thenReturn(false);
        assertThrows(Oauth2Exception.class,()->service.exchangeQrLogin("first","poll-first",browser,new MockHttpServletResponse()));
        assertTrue(storage.keySet().stream().noneMatch(key->key.startsWith("console:session:")));

        var expired=qrChallenge("expired");
        MockHttpServletRequest old=startQr(expired);
        String key=storage.keySet().stream().filter(value->value.startsWith("console:qr-login:")).findFirst().orElseThrow();
        storage.put(key,new ObjectMapper().writeValueAsString(new ConsoleSessionService.QrLoginFlow("expired","poll-expired",1)));
        assertEquals("expired",service.qrLoginStatus("expired","poll-expired",old).status());
        assertThrows(Oauth2Exception.class,()->service.approveQrLogin(expired,admin));
        assertThrows(Oauth2Exception.class,()->service.exchangeQrLogin("expired","poll-expired",old,new MockHttpServletResponse()));
    }

    private QrChallengeService.QrChallengePayload qrChallenge(String id) {
        var challenge=new QrChallengeService.QrChallengePayload();
        challenge.setChallengeId(id);challenge.setPollToken("poll-"+id);challenge.setApproveCode("approve-"+id);
        challenge.setType("console_login");challenge.setStatus("pending");
        when(qr.getByChallengeId(id)).thenReturn(challenge);
        return challenge;
    }
    private MockHttpServletRequest startQr(QrChallengeService.QrChallengePayload challenge) {
        when(qr.createChallenge(eq(QrChallengeService.ChallengeType.CONSOLE_LOGIN),isNull(),isNull(),eq("console"),anyString(),anyString()))
            .thenReturn(challenge);
        MockHttpServletResponse response=new MockHttpServletResponse();
        var init=service.startQrLogin(new MockHttpServletRequest(),response,"127.0.0.1","browser");
        assertEquals("KSUSER-AUTH-QR:"+challenge.getApproveCode(),init.getQrText());
        assertEquals(120,init.getExpiresInSeconds());
        return request(cookieValue(response,ConsoleSessionService.QR_LOGIN_COOKIE),ConsoleSessionService.QR_LOGIN_COOKIE);
    }

    private MockHttpServletRequest login() {
        MockHttpServletResponse start=new MockHttpServletResponse();
        String url=service.startLogin(start).get("authorizationUrl");
        MockHttpServletResponse finish=new MockHttpServletResponse();
        service.exchangeLogin("code",query(url,"state"),request(cookieValue(start,ConsoleSessionService.LOGIN_COOKIE),ConsoleSessionService.LOGIN_COOKIE),finish);
        return request(cookieValue(finish,ConsoleSessionService.SESSION_COOKIE),ConsoleSessionService.SESSION_COOKIE);
    }
    private static MockHttpServletRequest request(String value,String name) { MockHttpServletRequest request=new MockHttpServletRequest();request.setCookies(new Cookie(name,value));return request; }
    private static String cookieValue(MockHttpServletResponse response,String name) { return response.getHeaders("Set-Cookie").stream().filter(value->value.startsWith(name+"=")).map(value->value.substring(name.length()+1,value.indexOf(';'))).findFirst().orElseThrow(); }
    private static String query(String url,String key) { String value=UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(key);return value==null?null:java.net.URLDecoder.decode(value,java.nio.charset.StandardCharsets.UTF_8); }
}
