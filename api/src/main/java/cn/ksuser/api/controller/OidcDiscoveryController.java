package cn.ksuser.api.controller;

import cn.ksuser.api.service.SsoPlatformService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OidcDiscoveryController {

    private final SsoPlatformService ssoPlatformService;

    public OidcDiscoveryController(SsoPlatformService ssoPlatformService) {
        this.ssoPlatformService = ssoPlatformService;
    }

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openidConfiguration() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(ssoPlatformService.buildOpenIdConfiguration());
    }
}
