package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiErrorResponses;
import cn.ksuser.api.result.ApiResult;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Includes container errors that never reach a controller or MVC advice. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<?> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer number ? number : 500;
        return ResponseEntity.status(status).body(ApiErrorResponses.body(request, status, ApiResult.of(ApiErrorResponses.forStatus(status))));
    }
}
