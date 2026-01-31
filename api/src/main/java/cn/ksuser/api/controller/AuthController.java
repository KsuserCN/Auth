package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.RegisterRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 注册接口
     * @param registerRequest 注册请求
     * @return ApiResponse
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(@RequestBody RegisterRequest registerRequest) {
        String username = registerRequest.getUsername();
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();

        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "用户名不能为空"));
        }
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "邮箱不能为空"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "密码不能为空"));
        }

        // 执行注册
        User user = userService.register(username, email, password);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "用户名或邮箱已存在"));
        }

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "注册成功", user));
    }
}
