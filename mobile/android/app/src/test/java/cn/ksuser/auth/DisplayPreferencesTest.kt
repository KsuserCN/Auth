package cn.ksuser.auth

import cn.ksuser.auth.core.settings.AppPreferences
import cn.ksuser.auth.core.settings.AvatarCropGeometry
import cn.ksuser.auth.core.settings.DisplayTime
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.core.settings.translatedLiteral
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DisplayPreferencesTest {
    @Test fun explicitLanguageOverridesDeviceAndUnknownPreferencesFollowDevice() {
        assertEquals("en", AppPreferences.resolveLanguage("en", "zh-CN"))
        assertEquals("zh-Hans", AppPreferences.resolveLanguage("zh-Hans", "en-US"))
        assertEquals("zh-Hans", AppPreferences.resolveLanguage("system", "zh-TW"))
        assertEquals("en", AppPreferences.resolveLanguage("system", "de-DE"))
        assertEquals("en", AppPreferences.resolveLanguage("obsolete", "fr-FR"))
    }
    @Test fun timestampsWithOffsetsConvertButLegacyWallTimesStayUnchanged() {
        val zone = ZoneId.of("Asia/Shanghai")
        val expected = LocalDateTime.of(2026, 10, 17, 8, 30)
        assertEquals(expected, DisplayTime.local("2026-10-17T00:30:00Z", zone))
        assertEquals(expected, DisplayTime.local("2026-10-17T02:30:00+02:00", zone))
        assertEquals(LocalDateTime.of(2026, 10, 17, 0, 30), DisplayTime.local("2026-10-17T00:30:00", zone))
        assertNull(DisplayTime.local("invalid", zone))
    }
    @Test fun cropKeepsSquareAtRequestedPositionAndCannotEscapeSourceBounds() {
        val portrait = AvatarCropGeometry.square(600, 1000)
        assertEquals(0, portrait.x); assertEquals(200, portrait.y); assertEquals(600, portrait.size)
        val bottomRight = AvatarCropGeometry.square(600, 1000, 2f, 5f, 2f)
        assertEquals(300, bottomRight.x); assertEquals(700, bottomRight.y); assertEquals(300, bottomRight.size)
        val panorama = AvatarCropGeometry.square(3000, 100, 1f, -1f, -1f)
        assertEquals(0, panorama.x); assertEquals(0, panorama.y); assertEquals(100, panorama.size)
    }
    @Test fun substitutionDoesNotInterpretPlaceholderLikeUserInput() {
        val previous = L10n.language
        try {
            L10n.language = "zh-Hans"
            assertEquals("Name {1} / safe", L10n.format("{0} / {1}", "Name {1}", "safe"))
        } finally { L10n.language = previous }
    }
    @Test fun literalTranslationDoesNotGuessTemplateBoundariesInUserProvidedData() {
        val messages = mapOf("欢迎回来，{0}" to "Welcome back, {0}", "{0}：{1}" to "{0}: {1}")
        assertEquals("欢迎回来，Alice", translatedLiteral("欢迎回来，Alice", "en", messages))
        assertEquals("自定义：名字", translatedLiteral("自定义：名字", "en", messages))
        assertEquals("Welcome back, {0}", translatedLiteral("欢迎回来，{0}", "en", messages))
        assertEquals("欢迎回来，{0}", translatedLiteral("欢迎回来，{0}", "zh-Hans", messages))
    }

}
