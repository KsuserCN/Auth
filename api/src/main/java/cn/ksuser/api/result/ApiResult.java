package cn.ksuser.api.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A language-independent result descriptor, also used by nested business status data. */
public record ApiResult(ApiResultCode errorCode,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params) {
    public ApiResult {
        Objects.requireNonNull(errorCode, "errorCode");
        params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static ApiResult of(ApiResultCode code, Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Parameters require key/value pairs");
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            if (params.containsKey(key)) throw new IllegalArgumentException("Duplicate result parameter");
            params.put(key, pairs[i + 1]);
        }
        return new ApiResult(code, params);
    }
}
