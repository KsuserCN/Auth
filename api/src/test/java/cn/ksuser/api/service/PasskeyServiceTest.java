package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.repository.UserPasskeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PasskeyServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void effectiveOriginsMergeOnlyExplicitHarmonyOriginsAndPreserveExistingClients() {
        AppProperties properties = new AppProperties();
        properties.getPasskey().setOrigin("https://auth.ksuser.cn");
        properties.getPasskey().setAndroidOrigins(java.util.List.of("android:apk-key-hash:existing-client"));
        // A test fixture, not a claim about the origin format returned by HarmonyOS.
        properties.getPasskey().setHarmonyOrigins(Arrays.asList(
                " https://signed-harmony.example ", "https://signed-harmony.example", "", null));
        PasskeyService service = new PasskeyService(mock(UserPasskeyRepository.class),
                mock(StringRedisTemplate.class), properties);

        Set<Object> origins = (Set<Object>) ReflectionTestUtils.invokeMethod(service, "getEffectiveOrigins");
        Set<String> values = origins.stream().map(Object::toString).collect(Collectors.toSet());

        assertTrue(values.contains("https://signed-harmony.example"));
        assertTrue(values.contains("https://auth.ksuser.cn"));
        assertTrue(values.contains("android:apk-key-hash:existing-client"));
        assertFalse(values.contains("https://other-harmony.example"));
        assertTrue(origins.size() == 4); // Web, Console, Android and the explicit HarmonyOS origin.
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyHarmonyConfigurationDoesNotInferAnOrigin() {
        AppProperties properties = new AppProperties();
        properties.getPasskey().setOrigin("https://auth.ksuser.cn");
        PasskeyService service = new PasskeyService(mock(UserPasskeyRepository.class),
                mock(StringRedisTemplate.class), properties);

        Set<Object> origins = (Set<Object>) ReflectionTestUtils.invokeMethod(service, "getEffectiveOrigins");
        assertTrue(origins.size() == 2);
    }

    @Test
    void wildcardHarmonyConfigurationIsRejected() {
        AppProperties properties = new AppProperties();
        properties.getPasskey().setHarmonyOrigins(java.util.List.of("https://*.example.com"));
        PasskeyService service = new PasskeyService(mock(UserPasskeyRepository.class),
                mock(StringRedisTemplate.class), properties);

        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "getEffectiveOrigins"));
    }

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
