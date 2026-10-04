package cn.ksuser.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserAgentParserServiceTest {

    private final UserAgentParserService service = new UserAgentParserService();

    @Test
    void shouldRecognizeDesktopClientAndMacOs() {
        UserAgentParserService.UserAgentInfo info = service.parse(
                "KsuserAuthDesktop/1.0.0 (macOS; Flutter Desktop)"
        );

        assertEquals("Ksuser Auth Desktop 1.0.0", info.getBrowser());
        assertEquals("Mac", info.getDeviceType());
        assertEquals("Ksuser 桌面版（macOS）", service.describeClientSource(info));
    }

    @Test
    void shouldRecognizeIosNativeAndIphoneSafariBeforeMacOs() {
        var nativeInfo=service.parse("KsuserAuthiOS/1.0.0 (iPhone; iOS 17)");
        assertEquals("Ksuser Auth Mobile 1.0.0",nativeInfo.getBrowser());
        assertEquals("Ksuser 移动版（iOS）",service.describeClientSource(nativeInfo));
        assertEquals("iOS",service.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1").getDeviceType());
    }

    @Test
    void appleCapabilityIsOnlyAdvertisedToNativeIosNotLegacyAndroidOrWeb() {
        org.junit.jupiter.api.Assertions.assertTrue(UserAgentParserService.isNativeIosClient("KsuserAuthMobile/1.0.0 (iOS 17.0)"));
        org.junit.jupiter.api.Assertions.assertFalse(UserAgentParserService.isNativeIosClient("KsuserAuthMobile/1.0.0 (Android 14; API 34)"));
        org.junit.jupiter.api.Assertions.assertFalse(UserAgentParserService.isNativeIosClient("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"));
    }

    @Test
    void shouldKeepWebBrowserDescription() {
        String source = service.describeClientSource(
                "Microsoft Edge 146.0.0.0",
                "Mac"
        );

        assertEquals("网页端（Microsoft Edge 146.0.0.0 / macOS）", source);
    }
}
