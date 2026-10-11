package cn.ksuser.api.result;

import cn.ksuser.api.config.AppProperties;

public final class PasswordRequirementResult {
    private PasswordRequirementResult() { }
    public static ApiResultCode from(AppProperties.Password config) {
        int mask = (config.isRequireUppercase() ? 1 : 0) | (config.isRequireLowercase() ? 2 : 0)
            | (config.isRequireDigits() ? 4 : 0) | (config.isRequireSpecialChars() ? 8 : 0);
        return ApiResultCode.valueOf("PASSWORD_REQUIREMENTS_" + mask);
    }
}
