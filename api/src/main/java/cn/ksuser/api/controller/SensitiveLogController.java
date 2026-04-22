package cn.ksuser.api.controller;

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
                    .body(new ApiResponse<>(401, "Access token不存在"));
        }

        // 从token获取UUID
        String uuid = jwtUtil.getUuidFromToken(accessToken);
        if (uuid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "Access token无效或已过期"));
        }

        // 查找用户
        User user = userService.findByUuid(uuid).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, "用户不存在"));
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
                        .body(new ApiResponse<>(400, "Invalid startDate format, use YYYY-MM-DD"));
            }
        }
        
        if (endDate != null && !endDate.isEmpty()) {
            try {
                request.setEndDate(java.time.LocalDate.parse(endDate));
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, "Invalid endDate format, use YYYY-MM-DD"));
            }
        }
        
        request.setOperationType(operationType);
        request.setResult(result);

        // 查询日志
        PageResponse<SensitiveLogResponse> response = sensitiveLogService.queryLogs(user.getId(), request);

        return ResponseEntity.ok(new ApiResponse<>(200, "Sensitive logs retrieved successfully", response));
    }
}
