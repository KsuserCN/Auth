package cn.ksuser.api.service;

import cn.ksuser.api.dto.MobileAppPlatforms;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.List;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileAssociationVerifierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MobileAssociationVerifier verifier = spy(new MobileAssociationVerifier(mapper));
    private MobileAppPlatforms.Ios ios() { return new MobileAppPlatforms.Ios("com.example.app", "TEAMID1234", "https://login.example.com/oauth/callback", null, null, null, null, null); }
    private MobileAppPlatforms.Android android() { return new MobileAppPlatforms.Android("com.example.app", List.of("AB:".repeat(31) + "AB"), "https://login.example.com/oauth/callback", null, null, null, null); }
    @Test void iosRequiresBothPathAndWebCredentialsForCorrectApplication() throws Exception {
        doReturn(mapper.readTree("""
            {"applinks":{"details":[{"appIDs":["TEAMID1234.com.example.app"],"components":[{"/":"/oauth/*"}]}]},"webcredentials":{"apps":["TEAMID1234.com.example.app"]}}
            """)).when(verifier).fetch(any(), any());
        assertDoesNotThrow(() -> verifier.verifyIos(ios()));
        doReturn(mapper.readTree("{\"applinks\":{\"details\":[{\"appID\":\"TEAMID1234.com.example.app\",\"paths\":[\"*\"]}]}}")).when(verifier).fetch(any(), any());
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> verifier.verifyIos(ios()));
    }
    @Test void iosHonorsFirstMatchExclusionsAndRejectsQueryConstraints() throws Exception {
        var excluded = mapper.readTree("{\"paths\":[\"NOT /oauth/*\",\"*\"]}");
        assertFalse(MobileAssociationVerifier.matchesIosPath(excluded, "/oauth/callback"));
        assertTrue(MobileAssociationVerifier.matchesIosPath(excluded, "/another"));
        assertTrue(MobileAssociationVerifier.matchesIosPath(mapper.readTree("{\"paths\":[\"/another/*\"]}"), "/another/*value"));
        assertFalse(MobileAssociationVerifier.matchesIosPath(mapper.readTree("{\"components\":[{\"/\":\"*\",\"exclude\":true},{\"/\":\"/oauth/*\"}]}"), "/oauth/callback"));
        assertFalse(MobileAssociationVerifier.matchesIosPath(mapper.readTree("{\"components\":[{\"/\":\"*\",\"?\":{\"fixed\":\"1\"}}]}"), "/oauth/callback"));
    }
    @Test void androidRequiresPackageAllFingerprintsAndHandleUrlsRelation() throws Exception {
        String json = "[{\"relation\":[\"delegate_permission/common.handle_all_urls\"],\"target\":{\"namespace\":\"android_app\",\"package_name\":\"com.example.app\",\"sha256_cert_fingerprints\":[\"" + "AB:".repeat(31) + "AB\"]}}]";
        doReturn(mapper.readTree(json)).when(verifier).fetch(any(), any());
        assertDoesNotThrow(() -> verifier.verifyAndroid(android()));
        doReturn(mapper.readTree(json.replace("com.example.app", "other.package"))).when(verifier).fetch(any(), any());
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> verifier.verifyAndroid(android()));
        doReturn(mapper.readTree(json.replace("common.handle_all_urls", "common.get_login_creds"))).when(verifier).fetch(any(), any());
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> verifier.verifyAndroid(android()));
    }
    @Test void privateReservedAndTransitionAddressesAreNotFetchable() throws Exception {
        for (String ip : List.of("0.0.0.0", "10.0.0.1", "127.0.0.1", "100.64.0.1", "169.254.169.254", "172.16.0.1", "192.168.1.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "::1", "fe80::1", "fd00::1", "2001:db8::1", "2002:7f00:1::1", "::ffff:127.0.0.1")) assertFalse(MobileAssociationVerifier.isPublic(InetAddress.getByName(ip)), ip);
        assertTrue(MobileAssociationVerifier.isPublic(InetAddress.getByName("8.8.8.8")));
        assertTrue(MobileAssociationVerifier.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
    }
    @Test void privateDnsAnswerFailsBeforeAnyTlsConnection() throws Exception {
        doReturn(new InetAddress[]{InetAddress.getByName("127.0.0.1")}).when(verifier).resolve("login.example.com");
        var error = assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> verifier.verifyIos(ios()));
        assertEquals("关联域名必须解析为公网地址", error.getMessage());
    }
    @Test void mixedPublicPrivateAnswersAreRejectedAsAWhole() throws Exception {
        doReturn(new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("169.254.169.254")}).when(verifier).resolve("login.example.com");
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> verifier.verifyIos(ios()));
    }

    private byte[] response(String http) throws Exception {
        if (http.startsWith("HTTP/1.1 200") && !http.toLowerCase().contains("content-type:")) http = http.replace("HTTP/1.1 200 OK\r\n", "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n");
        if (http.startsWith("HTTP/1.0 200") && !http.toLowerCase().contains("content-type:")) http = http.replace("HTTP/1.0 200 OK\r\n", "HTTP/1.0 200 OK\r\nContent-Type: application/json\r\n");
        try (Socket socket = new Socket()) {
            return MobileAssociationVerifier.readResponse(new ByteArrayInputStream(http.getBytes(StandardCharsets.US_ASCII)), socket, System.nanoTime() + Duration.ofSeconds(2).toNanos());
        }
    }
    @Test void httpRedirectIsRejectedWithoutFollowingLocation() {
        var error = assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> response("HTTP/1.1 302 Found\r\nLocation: http://169.254.169.254/\r\nContent-Length: 0\r\n\r\n"));
        assertTrue(error.getMessage().contains("不支持重定向"));
    }
    @Test void httpAcceptsBoundedLengthChunkedAndConnectionCloseJson() throws Exception {
        assertEquals("{}", new String(response("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}"), StandardCharsets.UTF_8));
        assertEquals("{}", new String(response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n"), StandardCharsets.UTF_8));
        assertEquals("{}", new String(response("HTTP/1.0 200 OK\r\n\r\n{}"), StandardCharsets.UTF_8));
    }
    @Test void httpRejectsOversizeBodiesAndAmbiguousLength() {
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> response("HTTP/1.1 200 OK\r\nContent-Length: 131073\r\n\r\n"));
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n20001\r\n"));
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> response("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 2\r\n\r\n{}"));
        assertThrows(IOException.class, () -> response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 2\r\n\r\n"));
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> response("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n\r\n"));
    }
    @Test void httpRejectsExpiredBudgetBeforeReading() throws Exception {
        try (Socket socket = new Socket()) {
            assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> MobileAssociationVerifier.readResponse(new ByteArrayInputStream("HTTP/1.1 200 OK\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII)), socket, System.nanoTime() - 1));
        }
    }
    @Test void androidDynamicExclusionCannotBeMarkedVerified() throws Exception {
        String json = "[{\"relation\":[\"delegate_permission/common.handle_all_urls\"],\"target\":{\"namespace\":\"android_app\",\"package_name\":\"com.example.app\",\"sha256_cert_fingerprints\":[\"" + "AB:".repeat(31) + "AB\"]},\"relation_extensions\":{\"delegate_permission/common.handle_all_urls\":{\"dynamic_app_link_components\":[{\"/\":\"*\",\"exclude\":true}]}}}]";
        doReturn(mapper.readTree(json)).when(verifier).fetch(any(), any());
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> verifier.verifyAndroid(android()));
    }
    @Test void jsonRequiresApplicationJsonMimeType() throws Exception {
        assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> response("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\n{}"));
        assertEquals("{}", new String(response("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: 2\r\n\r\n{}"), StandardCharsets.UTF_8));
        try (Socket socket = new Socket()) {
            assertThrows(MobileAssociationVerifier.VerificationFailure.class, () -> MobileAssociationVerifier.readResponse(new ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII)), socket, System.nanoTime() + Duration.ofSeconds(2).toNanos()));
        }
    }
}
