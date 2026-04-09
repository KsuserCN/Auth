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
    void shouldKeepWebBrowserDescription() {
        String source = service.describeClientSource(
                "Microsoft Edge 146.0.0.0",
                "Mac"
        );

        assertEquals("网页端（Microsoft Edge 146.0.0.0 / macOS）", source);
    }
}
