package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.PasswordRequirementResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/info")
public class InfoController {

    private final AppProperties appProperties;

    public InfoController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 获取密码强度要求
     * @return ApiResponse
     */
    @GetMapping("/password-requirement")
    public ResponseEntity<ApiResponse<PasswordRequirementResponse>> passwordRequirement() {
        AppProperties.Password pwdConfig = appProperties.getPassword();

        PasswordRequirementResponse data = new PasswordRequirementResponse(
            pwdConfig.getMinLength(),
            pwdConfig.getMaxLength(),
            pwdConfig.isRequireUppercase(),
            pwdConfig.isRequireLowercase(),
            pwdConfig.isRequireDigits(),
            pwdConfig.isRequireSpecialChars(),
            true,
            buildPasswordRequirementMessage(pwdConfig)
        );

        return ResponseEntity.status(HttpStatus.OK)
            .body(new ApiResponse<>(200, "获取成功", data));
    }

    /**
     * 根据配置动态生成密码要求消息
     * @return 密码要求描述
     */
    private String buildPasswordRequirementMessage(AppProperties.Password pwdConfig) {
        List<String> requirements = new ArrayList<>();

        if (pwdConfig.isRequireUppercase()) {
            requirements.add("大写字母");
        }
        if (pwdConfig.isRequireLowercase()) {
            requirements.add("小写字母");
        }
        if (pwdConfig.isRequireDigits()) {
            requirements.add("数字");
        }
        if (pwdConfig.isRequireSpecialChars()) {
            requirements.add("特殊字符");
        }

        if (requirements.isEmpty()) {
            return "密码强度不足";
        }

        return "密码强度不足：需包含" + String.join("、", requirements);
    }
}
