package cn.ksuser.api.result;

import cn.ksuser.api.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared by MVC, security filters and the servlet error endpoint. */
public final class ApiErrorResponses {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ApiErrorResponses() { }
    public static ApiResultCode forStatus(int status) {
        return switch (status) {
            case 400 -> ApiResultCode.BAD_REQUEST;
            case 401 -> ApiResultCode.UNAUTHENTICATED;
            case 403 -> ApiResultCode.FORBIDDEN;
            case 404 -> ApiResultCode.NOT_FOUND;
            case 405 -> ApiResultCode.METHOD_NOT_ALLOWED;
            case 408 -> ApiResultCode.REQUEST_TIMEOUT;
            case 413 -> ApiResultCode.REQUEST_UPLOAD_TOO_LARGE;
            case 409 -> ApiResultCode.REQUEST_CONFLICT;
            case 415 -> ApiResultCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ApiResultCode.RATE_LIMITED;
            default -> status >= 500 ? ApiResultCode.SERVER_ERROR
                : status >= 400 ? ApiResultCode.BAD_REQUEST : ApiResultCode.REQUEST_FAILED;
        };
    }
    public static boolean isProtocol(HttpServletRequest request) {
        Object original = request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI);
        String path = (original instanceof String uri ? uri : request.getRequestURI());
        String context = request.getContextPath();
        if (!context.isEmpty() && path.startsWith(context + "/")) path = path.substring(context.length());
        path = path.replaceAll("/+$", "");
        return path.equals("/oauth2/token") || path.equals("/oauth2/userinfo")
            || path.equals("/sso/token") || path.equals("/sso/userinfo") || path.equals("/sso/jwks")
            || path.equals("/.well-known/openid-configuration");
    }
    public static Object body(HttpServletRequest request, int status, ApiResult result) {
        if (!isProtocol(request)) return new ApiResponse<>(status, result);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", status == 401 ? "invalid_token" : status == 403 ? "access_denied" : status >= 500 ? "server_error" : "invalid_request");
        body.put("error_description", result.errorCode().name());
        body.put("errorCode", result.errorCode());
        if (!result.params().isEmpty()) body.put("params", result.params());
        return body;
    }
    public static void write(HttpServletRequest request, HttpServletResponse response, int status, ApiResultCode code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.writeValueAsString(body(request, status, ApiResult.of(code))));
    }
}
