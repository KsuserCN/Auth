package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiResultCode;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.PasswordRequirementResponse;
import cn.ksuser.api.service.RegistrationValidationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/info")
public class InfoController {

    private final AppProperties appProperties;
    private final RegistrationValidationService registrationValidation;

    public InfoController(AppProperties appProperties, RegistrationValidationService registrationValidation) {
        this.appProperties = appProperties;
        this.registrationValidation = registrationValidation;
    }

    public record PasswordValidationRequest(String password) {}

    @PostMapping("/validate-password")
    public ResponseEntity<ApiResponse<Void>> validatePassword(@RequestBody PasswordValidationRequest request) {
        var rejection = registrationValidation.validatePassword(request.password());
        if (rejection != null) {
            return ResponseEntity.status(rejection.status()).cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(rejection.status().value(), rejection.result()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS));
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
            .body(new ApiResponse<>(200, ApiResultCode.APPLE_004, data));
    }

    /**
     * 根据配置动态生成密码要求消息
     * @return 密码要求描述
     */
    private ApiResultCode buildPasswordRequirementMessage(AppProperties.Password config) {
        return cn.ksuser.api.result.PasswordRequirementResult.from(config);
    }
}
