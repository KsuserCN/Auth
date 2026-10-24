package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiResultCode;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.PageResponse;
import cn.ksuser.api.dto.SensitiveLogQueryRequest;
import cn.ksuser.api.dto.SensitiveLogResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.SensitiveLogService;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class SensitiveLogController {

    @Autowired
    private SensitiveLogService sensitiveLogService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    /** Notification targets are fetched independently of list pagination and filters. */
    @GetMapping("/sensitive-logs/{id}")
    public ResponseEntity<ApiResponse<SensitiveLogResponse>> getSensitiveLog(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, ApiResultCode.REQUEST_003));
        }
        String uuid = jwtUtil.getUuidFromToken(authHeader.substring(7));
        if (uuid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, ApiResultCode.REQUEST_004));
        }
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, ApiResultCode.AUTH_005));
        }
        // A missing record and a record belonging to another account have the same response.
        return sensitiveLogService.findLog(user.getId(), id)
                .map(log -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(new ApiResponse<>(200, ApiResultCode.REQUEST_007, log)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noStore())
                        .body(new ApiResponse<>(404, ApiResultCode.NOT_FOUND)));
    }

    /**
     * 查询用户的敏感操作日志
     */
    @GetMapping("/sensitive-logs")
    public ResponseEntity<ApiResponse<PageResponse<SensitiveLogResponse>>> getSensitiveLogs(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String result
    ) {
        // 提取并验证token
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, ApiResultCode.REQUEST_003));
        }

        // 从token获取UUID
        String uuid = jwtUtil.getUuidFromToken(accessToken);
        if (uuid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, ApiResultCode.REQUEST_004));
        }

        // 查找用户
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, ApiResultCode.AUTH_005));
        }

        // 构建查询请求
        SensitiveLogQueryRequest request = new SensitiveLogQueryRequest();
        request.setPage(page);
        request.setPageSize(pageSize);
        
        if (startDate != null && !startDate.isEmpty()) {
            try {
                request.setStartDate(java.time.LocalDate.parse(startDate));
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, ApiResultCode.REQUEST_005));
            }
        }
        
        if (endDate != null && !endDate.isEmpty()) {
            try {
                request.setEndDate(java.time.LocalDate.parse(endDate));
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, ApiResultCode.REQUEST_006));
            }
        }
        
        request.setOperationType(operationType);
        request.setResult(result);

        // 查询日志
        PageResponse<SensitiveLogResponse> response = sensitiveLogService.queryLogs(user.getId(), request);

        return ResponseEntity.ok(new ApiResponse<>(200, ApiResultCode.REQUEST_007, response));
    }
}
