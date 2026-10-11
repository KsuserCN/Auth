package cn.ksuser.api.result;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.AdaptiveAuthStatusResponse;
import cn.ksuser.api.exception.GlobalExceptionHandler;
import cn.ksuser.api.exception.Oauth2Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiResponseContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc() { return MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new GlobalExceptionHandler()).build(); }

    @Test void serializesCodesAndBusinessParametersWithoutServerCopy() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(new ApiResponse<>(400,
            ApiResultCode.AUTH_020.withParams("min", 8, "max", 64))));
        assertEquals(400, json.get("code").asInt());
        assertEquals("AUTH_020", json.get("errorCode").asText());
        assertEquals(8, json.path("params").path("min").asInt());
        assertFalse(json.has("msg")); assertFalse(json.has("data"));
        var success = mapper.readTree(mapper.writeValueAsString(new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, Map.of("verified", true))));
        assertFalse(success.has("params")); assertTrue(success.path("data").path("verified").asBoolean());
    }
    @Test void distinctBusinessErrorsRemainDistinctWithIdenticalHttpStatus() throws Exception {
        mvc().perform(get("/business/first")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_018"));
        mvc().perform(get("/business/second")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_020"));
    }
    @Test void unexpectedExceptionsAndMalformedBodiesUseUnifiedCodesWithoutDetails() throws Exception {
        mvc().perform(get("/business/unexpected")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("SERVER_ERROR")).andExpect(jsonPath("$.msg").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database-secret"))));
        mvc().perform(post("/business/body").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mvc().perform(get("/business/body")).andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mvc().perform(post("/business/body").contentType(MediaType.TEXT_PLAIN).content("test"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }
    @Test void missingUploadPartAndMalformedMultipartUseClientErrorCodes() throws Exception {
        mvc().perform(multipart("/business/upload"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mvc().perform(get("/business/multipart-failure"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }
    @Test void protocolErrorsKeepStandardErrorAndShareDetailedCode() throws Exception {
        mvc().perform(post("/oauth2/token")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.error_description").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.msg").doesNotExist());
    }
    @Test void servletProtocolErrorsUseOriginalUriAndContextPath() {
        var request = new MockHttpServletRequest(); request.setRequestURI("/auth/error"); request.setContextPath("/auth");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/auth/sso/token/");
        assertTrue(ApiErrorResponses.isProtocol(request));
    }
    @Test void adaptiveStatusSerializesOnlyDescriptorsAndLeavesInternalAuditCopyPrivate() throws Exception {
        var response = new AdaptiveAuthStatusResponse();
        response.setReasons(List.of("internal audit reason")); response.setRecommendedAction("internal action");
        response.setAlertMessage("internal alert"); response.setAlertTitle("internal title");
        response.setReasonCodes(List.of(ApiResult.of(ApiResultCode.ADAPTIVE_REASON_IP_CHANGED)));
        response.setRecommendedActionCode(ApiResultCode.ADAPTIVE_ACTION_SAFE);
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertFalse(json.has("reasons")); assertFalse(json.has("recommendedAction"));
        assertFalse(json.has("alertMessage")); assertFalse(json.has("alertTitle"));
        assertEquals("ADAPTIVE_REASON_IP_CHANGED", json.path("reasonCodes").get(0).path("errorCode").asText());
    }
    @Test void flowsPreserve201And202AndExceptionsPreserveCauseWithoutLeakingIt() throws Exception {
        for (int status : new int[]{201, 202}) {
            var json = mapper.readTree(mapper.writeValueAsString(new ApiResponse<>(status,
                status == 201 ? ApiResultCode.AUTH_MFA_REQUIRED : ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED)));
            assertEquals(status, json.path("code").asInt()); assertFalse(json.has("msg"));
        }
        var exception = new BusinessArgumentException(ApiResultCode.AUTH_018, new IllegalArgumentException("private detail"));
        assertSame(exception.getResult(), ApiFailures.result(exception, ApiResultCode.BAD_REQUEST));
        assertEquals(ApiResultCode.BAD_REQUEST, ApiFailures.result(new IllegalArgumentException("private detail"), ApiResultCode.BAD_REQUEST).errorCode());
        assertThrows(IllegalArgumentException.class, () -> ApiResultCode.AUTH_020.withParams("min"));
    }
    @RestController static class Fixture {
        @GetMapping("/business/first") Object first() { throw new BusinessArgumentException(ApiResultCode.AUTH_018); }
        @GetMapping("/business/second") Object second() { throw new BusinessArgumentException(ApiResultCode.AUTH_020.withParams("min", 8, "max", 64)); }
        @GetMapping("/business/unexpected") Object unexpected() { throw new IllegalStateException("database-secret"); }
        @PostMapping(value="/business/body", consumes=MediaType.APPLICATION_JSON_VALUE) Object body(@RequestBody Map<String,Object> body) { return body; }
        @PostMapping("/business/upload") Object upload(@RequestPart("file") MultipartFile file) { return Map.of(); }
        @GetMapping("/business/multipart-failure") Object multipartFailure() { throw new org.springframework.web.multipart.MultipartException("private malformed multipart detail"); }
        @PostMapping("/oauth2/token") Object token() { throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.UNAUTHENTICATED); }
    }
}
