package cn.ksuser.api.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** The two supported display languages, independent of account preferences. */
public enum ClientLanguage {
    SIMPLIFIED_CHINESE("zh-Hans", Locale.SIMPLIFIED_CHINESE),
    ENGLISH("en", Locale.ENGLISH);

    // HTTP weights permit only 0..1 with at most three fractional digits.
    // LanguageRange.parse also accepts Java floating-point syntax such as +0.5.
    private static final Pattern LANGUAGE_PREFERENCE = Pattern.compile(
        "(?:[a-z]{1,8}(?:-[a-z0-9]{1,8})*|\\*)"
            + "(?:[ \\t]*;[ \\t]*q=(?:0(?:\\.[0-9]{0,3})?|1(?:\\.0{0,3})?))?",
        Pattern.CASE_INSENSITIVE);

    private final String tag;
    private final Locale locale;

    ClientLanguage(String tag, Locale locale) {
        this.tag = tag;
        this.locale = locale;
    }

    public String tag() { return tag; }
    public Locale locale() { return locale; }

    /** Missing or malformed legacy headers retain the previous Chinese default. */
    public static ClientLanguage fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) return SIMPLIFIED_CHINESE;
        StringBuilder normalized = new StringBuilder();
        for (String preference : header.split(",", -1)) {
            String value = preference.trim();
            // HTTP list fields allow empty elements; they carry no preference.
            if (value.isEmpty()) continue;
            if (!LANGUAGE_PREFERENCE.matcher(value).matches()) return SIMPLIFIED_CHINESE;
            if (!normalized.isEmpty()) normalized.append(',');
            normalized.append(value.replace('\t', ' '));
        }
        if (normalized.isEmpty()) return SIMPLIFIED_CHINESE;
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(normalized.toString())) {
                if (range.getWeight() == 0 || range.getRange().contains("*")) continue;
                String language = range.getRange();
                return language.equals("zh") || language.startsWith("zh-")
                    ? SIMPLIFIED_CHINESE : ENGLISH;
            }
        } catch (IllegalArgumentException ignored) {
            // A display preference must never prevent an otherwise valid code request.
        }
        return SIMPLIFIED_CHINESE;
    }
}
