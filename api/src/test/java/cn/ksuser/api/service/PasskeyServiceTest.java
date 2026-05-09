package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.repository.UserPasskeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PasskeyServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void androidOriginsIncludeInstalledApkKeyHashFromFingerprint() {
        PasskeyService service = new PasskeyService(
                mock(UserPasskeyRepository.class),
                mock(StringRedisTemplate.class),
                new AppProperties()
        );

        Set<String> origins = (Set<String>) ReflectionTestUtils.invokeMethod(
                service,
                "buildAndroidOriginsFromFingerprint",
                "D8:FE:BE:29:A8:8C:67:97:FD:EA:B6:42:6F:C2:BC:A5:EF:B0:76:C9:41:66:2C:FC:92:83:F0:70:B4:FA:A7:10"
        );

        assertTrue(origins.contains(
                "android:apk-key-hash:2P6-KaiMZ5f96rZCb8K8pe-wdslBZiz8koPwcLT6pxA"
        ));
        assertTrue(origins.contains(
                "android:apk-key-hash:2P6+KaiMZ5f96rZCb8K8pe+wdslBZiz8koPwcLT6pxA"
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void effectiveOriginsIncludeAndroidFingerprintsFromConfiguration() {
        AppProperties appProperties = new AppProperties();
        appProperties.getPasskey().setOrigin("https://auth.ksuser.cn");
        appProperties.getPasskey().setAndroidCertSha256Fingerprints(java.util.List.of(
                "D8:FE:BE:29:A8:8C:67:97:FD:EA:B6:42:6F:C2:BC:A5:EF:B0:76:C2:01:66:2C:FC:92:83:F0:70:B4:FA:A7:10",
                "D8:FE:BE:29:A8:8C:67:97:FD:EA:B6:42:6F:C2:BC:A5:EF:B0:76:C9:41:66:2C:FC:92:83:F0:70:B4:FA:A7:10"
        ));
        PasskeyService service = new PasskeyService(
                mock(UserPasskeyRepository.class),
                mock(StringRedisTemplate.class),
                appProperties
        );

        Set<Object> origins = (Set<Object>) ReflectionTestUtils.invokeMethod(service, "getEffectiveOrigins");
        Set<String> originValues = origins.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertTrue(originValues.contains(
                "android:apk-key-hash:2P6+KaiMZ5f96rZCb8K8pe+wdsIBZiz8koPwcLT6pxA"
        ));
        assertTrue(originValues.contains(
                "android:apk-key-hash:2P6+KaiMZ5f96rZCb8K8pe+wdslBZiz8koPwcLT6pxA"
        ));
    }
}
