package cn.ksuser.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ClientLanguageTest {
    @ParameterizedTest
    @CsvSource({
        "zh-Hans,SIMPLIFIED_CHINESE", "zh-TW,SIMPLIFIED_CHINESE", "zh-Hant-HK,SIMPLIFIED_CHINESE",
        "en,ENGLISH", "en-GB,ENGLISH", "fr-FR,ENGLISH",
        "'en;q=0.3,zh-CN;q=0.9',SIMPLIFIED_CHINESE",
        "'zh-TW;q=0.1,en;q=1',ENGLISH", "'zh;q=0,en;q=0.8',ENGLISH",
        "'*;q=1,en;q=0.5',ENGLISH", "'zh;q=0,en;q=0',SIMPLIFIED_CHINESE",
        "'*',SIMPLIFIED_CHINESE", "'en;q=2',SIMPLIFIED_CHINESE", "'not_a_language',SIMPLIFIED_CHINESE",
        "'en;q=+0.5',SIMPLIFIED_CHINESE", "'en;q=.7',SIMPLIFIED_CHINESE",
        "'en;q=0.1234',SIMPLIFIED_CHINESE", "'en;q=NaN',SIMPLIFIED_CHINESE",
        "'en-US;q=1.000,zh;q=0.999',ENGLISH", "'en;Q=0.001',ENGLISH",
        "'en- US',SIMPLIFIED_CHINESE", "',en,,zh;q=0.5,',ENGLISH", "', ,',SIMPLIFIED_CHINESE"
    })
    void normalizesWeightedLanguageHeaders(String header, ClientLanguage expected) {
        assertEquals(expected, ClientLanguage.fromAcceptLanguage(header));
    }

    @Test void oldClientsWithoutALanguageKeepChinese() {
        assertEquals(ClientLanguage.SIMPLIFIED_CHINESE, ClientLanguage.fromAcceptLanguage(null));
        assertEquals(ClientLanguage.SIMPLIFIED_CHINESE, ClientLanguage.fromAcceptLanguage("  "));
    }

    @Test void acceptsHttpOptionalWhitespace() {
        assertEquals(ClientLanguage.ENGLISH, ClientLanguage.fromAcceptLanguage(" en-US\t; q=0.8, zh;q=0.7 "));
    }
}
