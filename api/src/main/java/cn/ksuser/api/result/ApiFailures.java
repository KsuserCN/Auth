package cn.ksuser.api.result;

import cn.ksuser.api.exception.Oauth2Exception;

public final class ApiFailures {
    private ApiFailures() { }
    public static ApiResult result(Throwable error, ApiResultCode fallback) {
        if (error instanceof BusinessArgumentException business) return business.getResult();
        if (error instanceof BusinessStateException business) return business.getResult();
        if (error instanceof Oauth2Exception oauth) return oauth.getResult();
        return ApiResult.of(fallback);
    }
}
